package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@DisplayName("Seguridad, actuator y OpenAPI")
class SeguridadIT extends AbstractIT {

    private WebTestClient sinClave() {
        return webTestClient.mutate().defaultHeaders(cabeceras -> cabeceras.remove("X-API-Key")).build();
    }

    @Nested
    @DisplayName("API key")
    class ApiKey {

        @Test
        @DisplayName("sin clave responde 401 con el formato común, la correlación y no guarda nada")
        void shouldRespondUnauthorized_WhenKeyIsMissing() {
            sinClave().post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", "pedido-12345678")
                    .bodyValue(PacienteFixtures.e1())
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals("X-Correlation-Id", "pedido-12345678")
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(401)
                    .jsonPath("$.codigo").isEqualTo("NO_AUTORIZADO")
                    .jsonPath("$.mensaje").isEqualTo("API key ausente o inválida")
                    .jsonPath("$.path").isEqualTo("/triage/pacientes")
                    .jsonPath("$.correlationId").isEqualTo("pedido-12345678")
                    .jsonPath("$.timestamp").isNotEmpty();

            assertThat(contar("paciente")).isZero();
        }

        @Test
        @DisplayName("una clave incorrecta responde 401")
        void shouldRespondUnauthorized_WhenKeyIsWrong() {
            webTestClient.mutate().defaultHeader("X-API-Key", "incorrecta").build()
                    .get().uri("/triage/alertas")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.codigo").isEqualTo("NO_AUTORIZADO");
        }

        @ParameterizedTest(name = "GET {0} sin clave")
        @ValueSource(strings = {"/triage/alertas", "/triage/reportes/operacion?desde=2026-11-05&hasta=2026-11-06",
                "/actuator/prometheus", "/actuator/metrics", "/triage/ruta-que-no-existe"})
        void shouldRespondUnauthorized_WhenEndpointIsProtected(String ruta) {
            sinClave().get().uri(ruta).exchange().expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("el resto de endpoints también lo exigen")
        void shouldRespondUnauthorized_WhenCallingEveryTriageEndpointWithoutKey() {
            UUID id = UUID.randomUUID();
            sinClave().get().uri("/triage/pacientes/{id}", id).exchange().expectStatus().isUnauthorized();
            sinClave().get().uri("/triage/pacientes/{id}/auditoria", id).exchange().expectStatus().isUnauthorized();
            sinClave().patch().uri("/triage/pacientes/{id}/estado", id).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"estado\":\"ALTA\"}").exchange().expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("con la clave correcta pasa")
        void shouldPass_WhenKeyIsCorrect() {
            webTestClient.get().uri("/triage/alertas").exchange().expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("Rutas públicas")
    class Publicas {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
        void shouldBeUp_WhenCallingHealthWithoutKey(String ruta) {
            sinClave().get().uri(ruta).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("la readiness incluye la conexión R2DBC")
        void shouldIncludeR2dbc_WhenReadinessIsQueried() {
            sinClave().get().uri("/actuator/health/readiness").exchange()
                    .expectBody()
                    .jsonPath("$.components.r2dbc.status").isEqualTo("UP")
                    .jsonPath("$.components.readinessState.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("OpenAPI se publica sin clave y declara el esquema ApiKeyAuth")
        void shouldPublishOpenApiWithApiKeyScheme_WhenCallingApiDocs() {
            sinClave().get().uri("/v3/api-docs").exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.components.securitySchemes.ApiKeyAuth.type").isEqualTo("apiKey")
                    .jsonPath("$.components.securitySchemes.ApiKeyAuth.in").isEqualTo("header")
                    .jsonPath("$.components.securitySchemes.ApiKeyAuth.name").isEqualTo("X-API-Key")
                    .jsonPath("$.security[0].ApiKeyAuth").isArray()
                    .jsonPath("$.paths['/triage/pacientes']").isNotEmpty()
                    .jsonPath("$.paths['/triage/reportes/operacion']").isNotEmpty()
                    .jsonPath("$.paths['/triage/alertas']").isNotEmpty();
        }

        @Test
        @DisplayName("Swagger UI se sirve sin clave")
        void shouldServeSwaggerUi_WhenCalledWithoutKey() {
            sinClave().get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
            sinClave().get().uri("/swagger-ui.html").exchange()
                    .expectStatus().value(estado -> assertThat(estado).isIn(200, 302));
        }
    }

    @Nested
    @DisplayName("Prometheus")
    class Prometheus {

        @Test
        @DisplayName("con clave expone las métricas de negocio y el gauge del outbox")
        void shouldExposeBusinessMetrics_WhenScrapingWithKey() {
            crearPaciente(PacienteFixtures.e1());

            String texto = webTestClient.get().uri("/actuator/prometheus").exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();

            assertThat(texto).contains("triage_clasificacion_total{", "nivel=\"CRITICO\"", "triage_outbox_pendientes");
        }
    }
}
