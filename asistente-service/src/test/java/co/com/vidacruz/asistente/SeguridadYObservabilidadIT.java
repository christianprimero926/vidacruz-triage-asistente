package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@AutoConfigureObservability
@ActiveProfiles("test")
@DisplayName("API key, correlación, métricas y documentación")
class SeguridadYObservabilidadIT {

    private static final String PREGUNTA_SIN_PROTOCOLO = "receta de arroz con pollo";

    @Autowired
    private WebTestClient conApiKey;
    @LocalServerPort
    private int puerto;

    private WebTestClient sinApiKey() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build();
    }

    @Nested
    @DisplayName("API key")
    class ApiKey {

        @Test
        void shouldRespond401InCommonFormat_WhenKeyIsMissing() {
            sinApiKey().post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pregunta", "hola")).exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().exists("X-Correlation-Id")
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(401)
                    .jsonPath("$.codigo").isEqualTo("NO_AUTORIZADO")
                    .jsonPath("$.path").isEqualTo("/asistente/consulta")
                    .jsonPath("$.correlationId").isNotEmpty();
        }

        @Test
        void shouldRespond401_WhenKeyIsWrong() {
            sinApiKey().get().uri("/asistente/estado-urgencias").header("X-API-Key", "otra-clave")
                    .exchange().expectStatus().isUnauthorized();
        }

        @Test
        void shouldRespond401_WhenKeyIsOnlyAPrefixOfTheRealOne() {
            sinApiKey().get().uri("/asistente/estado-urgencias").header("X-API-Key", "clave-api")
                    .exchange().expectStatus().isUnauthorized();
        }

        @Test
        void shouldProtectPrometheus_WhenKeyIsMissing() {
            sinApiKey().get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
        }

        @Test
        void shouldAllowHealthProbesWithoutKey_WhenCalledByPlatform() {
            sinApiKey().get().uri("/actuator/health").exchange().expectStatus().isOk();
            sinApiKey().get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
            sinApiKey().get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
        }

        @Test
        void shouldServeApiDocsWithoutKeyAndDeclareSecurityScheme_WhenSwaggerIsRequested() {
            sinApiKey().get().uri("/v3/api-docs").exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.components.securitySchemes.ApiKeyAuth.type").isEqualTo("apiKey")
                    .jsonPath("$.components.securitySchemes.ApiKeyAuth.name").isEqualTo("X-API-Key")
                    .jsonPath("$.components.securitySchemes.ApiKeyAuth.in").isEqualTo("header")
                    .jsonPath("$.paths['/asistente/consulta']").exists()
                    .jsonPath("$.paths['/asistente/estado-urgencias']").exists();
            sinApiKey().get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
        }

        @Test
        void shouldNotAskForKey_WhenSwaggerIsRequestedUnderTheIngressPrefix() {
            sinApiKey().get().uri("/asistente/swagger-ui.html").exchange().expectStatus().isNotFound();
            sinApiKey().get().uri("/asistente/v3/api-docs").exchange().expectStatus().isNotFound();
            sinApiKey().get().uri("/asistente/swagger-ui/index.html").exchange().expectStatus().isNotFound();
        }

        @Test
        void shouldAskForKey_WhenPathOnlyLooksLikeSwaggerUnderTheIngressPrefix() {
            sinApiKey().get().uri("/asistente/swagger-ui-falso").exchange().expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Correlación")
    class Correlacion {

        @Test
        void shouldEchoCorrelationId_WhenClientSendsAValidOne() {
            conApiKey.get().uri("/asistente/estado-urgencias").header("X-Correlation-Id", "mi-correlacion-123")
                    .exchange().expectHeader().valueEquals("X-Correlation-Id", "mi-correlacion-123");
        }

        @Test
        void shouldGenerateUuid_WhenClientSendsUnsafeValue() {
            String respuesta = conApiKey.get().uri("/asistente/estado-urgencias")
                    .header("X-Correlation-Id", "corto").exchange()
                    .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-Id");

            assertThat(UUID.fromString(respuesta)).isNotNull();
        }

        @Test
        void shouldReportCorrelationIdInErrorBody_WhenRequestFails() {
            conApiKey.post().uri("/asistente/consulta").header("X-Correlation-Id", "mi-correlacion-123")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("pregunta", " ")).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.correlationId").isEqualTo("mi-correlacion-123");
        }
    }

    @Nested
    @DisplayName("Métricas")
    class Metricas {

        @Test
        void shouldExposeBusinessMetricsInPrometheus_WhenKeyIsSent() {
            conApiKey.post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pregunta", PREGUNTA_SIN_PROTOCOLO)).exchange().expectStatus().isOk();

            String texto = conApiKey.get().uri("/actuator/prometheus").exchange()
                    .expectStatus().isOk()
                    .returnResult(String.class).getResponseBody().collectList().block().stream()
                    .reduce("", String::concat);

            assertThat(texto).contains("asistente_respuestas_total{").contains("tipo=\"sin_protocolo\"")
                    .contains("resilience4j_circuitbreaker_state");
        }
    }
}
