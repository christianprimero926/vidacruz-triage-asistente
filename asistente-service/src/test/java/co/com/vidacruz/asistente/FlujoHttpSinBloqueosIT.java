package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * BlockHound sin allowlist: cualquier llamada bloqueante en un hilo del event loop rompe la solicitud.
 * El embedding ONNX y la llamada al LLM corren en schedulers aparte, por eso no necesitan excepciones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@AutoConfigureObservability
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Flujo HTTP completo sin llamadas bloqueantes (BlockHound)")
class FlujoHttpSinBloqueosIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final String RESPUESTA = "{\"respuesta\":\"Reconfirmar la presión tras 5 minutos de reposo.\","
            + "\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}],\"sinProtocolo\":false}";

    static {
        REDIS.start();
        BlockHound.install();
    }

    @Autowired
    private WebTestClient conApiKey;
    @LocalServerPort
    private int puerto;
    @MockitoBean
    private ChatModel chatModel;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Nested
    @DisplayName("Control negativo")
    class ControlNegativo {

        @Test
        void shouldDetectBlockingCall_WhenSleepRunsOnNonBlockingThread() {
            Mono<Long> bloqueante = Mono.delay(Duration.ofMillis(10)).doOnNext(valor -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            StepVerifier.create(bloqueante)
                    .expectErrorMatches(error -> error instanceof BlockingOperationError
                            || error.getCause() instanceof BlockingOperationError)
                    .verify(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("Flujo real")
    class Flujo {

        @Test
        void shouldServeEveryEndpointWithoutBlocking_WhenFullHttpFlowRuns() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(RESPUESTA)).build());

            conApiKey.post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pregunta", "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?"))
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001");
            conApiKey.post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pregunta", "receta de arroz con pollo"))
                    .exchange().expectStatus().isOk();
            conApiKey.post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pregunta", " ")).exchange().expectStatus().isBadRequest();
            conApiKey.get().uri("/asistente/estado-urgencias").exchange()
                    .expectStatus().isOk().expectBody().jsonPath("$.totalActivos").isEqualTo(0);
            conApiKey.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
            conApiKey.get().uri("/actuator/health").exchange().expectStatus().isOk();

            WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build()
                    .get().uri("/asistente/estado-urgencias").exchange().expectStatus().isUnauthorized();
            assertThat(chatModel).isNotNull();
        }
    }
}
