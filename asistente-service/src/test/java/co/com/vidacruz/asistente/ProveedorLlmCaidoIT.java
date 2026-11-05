package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Cliente real de LangChain4j contra un servidor HTTP falso que imita el API de OpenAI. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "asistente.llm.timeout-segundos=1")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("Proveedor LLM falso: modo degradado y circuit breaker")
class ProveedorLlmCaidoIT {

    private static final String PREGUNTA = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";
    private static final String RESPUESTA_LLM = "{\"respuesta\":\"Reconfirmar la presión tras 5 minutos de reposo.\","
            + "\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}],\"sinProtocolo\":false}";

    private static final AtomicInteger peticiones = new AtomicInteger();
    private static volatile int estado = 200;
    private static volatile long demoraMs;
    private static final HttpServer SERVIDOR = iniciarServidor();

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void proveedor(DynamicPropertyRegistry registry) {
        registry.add("asistente.llm.base-url", () -> "http://localhost:" + SERVIDOR.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void detener() {
        SERVIDOR.stop(0);
    }

    @BeforeEach
    void reiniciar() {
        peticiones.set(0);
        estado = 200;
        demoraMs = 0;
        circuitBreakerRegistry.circuitBreaker("llm").reset();
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/v1/chat/completions", intercambio -> {
                peticiones.incrementAndGet();
                intercambio.getRequestBody().readAllBytes();
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] cuerpo = (estado == 200 ? chatCompletion() : "{\"error\":\"fallo\"}")
                        .getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                try {
                    intercambio.sendResponseHeaders(estado, cuerpo.length);
                    intercambio.getResponseBody().write(cuerpo);
                } catch (IOException ignorada) {
                    // el cliente cerró la conexión por timeout
                } finally {
                    intercambio.close();
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String chatCompletion() {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "id", "chatcmpl-1", "object", "chat.completion", "created", 1, "model", "falso",
                    "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                            "message", Map.of("role", "assistant", "content", RESPUESTA_LLM)))));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private WebTestClient.ResponseSpec consultar() {
        return webTestClient.post().uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", PREGUNTA))
                .exchange();
    }

    @Test
    void shouldAnswerWithSources_WhenProviderWorks() {
        consultar().expectStatus().isOk()
                .expectBody().jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001");

        assertThat(peticiones.get()).isEqualTo(1);
    }

    @Test
    void shouldRespond503_WhenProviderReturns500() {
        estado = 500;

        consultar().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody().jsonPath("$.codigo").isEqualTo("LLM_NO_DISPONIBLE");
    }

    @Test
    void shouldRespond503_WhenProviderExceedsTimeout() {
        demoraMs = 3000;

        consultar().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("LLM_NO_DISPONIBLE");
    }

    @Test
    void shouldOpenCircuitAndStopCallingProvider_WhenRepeated500() {
        estado = 500;
        for (int i = 0; i < 5; i++) {
            consultar().expectStatus().isEqualTo(503);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("llm").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int antes = peticiones.get();

        consultar().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30");

        assertThat(antes).isEqualTo(5);
        assertThat(peticiones.get()).isEqualTo(antes);
    }

    @Test
    void shouldKeepReadinessUpAndShowOpenCircuitInHealth_WhenProviderIsDown() {
        estado = 500;
        for (int i = 0; i < 5; i++) {
            consultar().expectStatus().isEqualTo(503);
        }

        webTestClient.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        webTestClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.components.circuitBreakers.details.llm.details.state")
                .isEqualTo("OPEN");
    }

    @Test
    void shouldRecoverAnswering_WhenProviderComesBackAndCircuitCloses() {
        CircuitBreaker circuito = circuitBreakerRegistry.circuitBreaker("llm");
        estado = 500;
        for (int i = 0; i < 5; i++) {
            consultar().expectStatus().isEqualTo(503);
        }
        circuito.transitionToHalfOpenState();
        estado = 200;

        for (int i = 0; i < 3; i++) {
            consultar().expectStatus().isOk();
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
