package co.com.vidacruz.triage.config;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.exception.ErrorResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("ApiKeyWebFilter")
class ApiKeyWebFilterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ApiKeyWebFilter filtro = crearFiltro("clave-secreta");

    private ApiKeyWebFilter crearFiltro(String clave) {
        var propiedades = new ApiKeyProperties();
        propiedades.setApiKey(clave);
        return new ApiKeyWebFilter(propiedades,
                new ErrorResponseFactory(Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC)), objectMapper);
    }

    private boolean pasa(MockServerWebExchange exchange) {
        AtomicBoolean continuo = new AtomicBoolean();
        filtro.filter(exchange, ex -> {
            continuo.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(2));
        return continuo.get();
    }

    private MockServerWebExchange peticion(String ruta, String clave) {
        var builder = MockServerHttpRequest.get(ruta);
        if (clave != null) {
            builder.header("X-API-Key", clave);
        }
        return MockServerWebExchange.from(builder);
    }

    @Test
    @DisplayName("deja pasar la clave correcta")
    void shouldContinue_WhenApiKeyIsCorrect() {
        assertThat(pasa(peticion("/triage/pacientes/x", "clave-secreta"))).isTrue();
    }

    @ParameterizedTest(name = "clave \"{0}\"")
    @ValueSource(strings = {"otra", "clave-secreta ", "CLAVE-SECRETA", ""})
    void shouldRejectWithJson_WhenApiKeyIsWrong(String clave) {
        MockServerWebExchange exchange = peticion("/triage/pacientes/x", clave);

        assertThat(pasa(exchange)).isFalse();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString()).startsWith("application/json");
        String cuerpo = exchange.getResponse().getBodyAsString().block();
        assertThat(cuerpo).contains("\"status\":401", "\"codigo\":\"NO_AUTORIZADO\"", "\"path\":\"/triage/pacientes/x\"");
    }

    @Test
    @DisplayName("rechaza una petición sin la cabecera")
    void shouldReject_WhenHeaderIsMissing() {
        assertThat(pasa(peticion("/triage/reportes/operacion", null))).isFalse();
    }

    @ParameterizedTest(name = "{0} es pública")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
            "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config",
            "/webjars/swagger-ui/index.css", "/triage/swagger-ui.html", "/triage/v3/api-docs",
            "/triage/v3/api-docs/swagger-config", "/triage/swagger-ui/index.html"})
    void shouldContinueWithoutKey_WhenPathIsPublic(String ruta) {
        assertThat(pasa(peticion(ruta, null))).isTrue();
    }

    @ParameterizedTest(name = "{0} exige clave")
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/metrics", "/actuator/info", "/triage/alertas",
            "/actuator/healthz", "/swagger-ui-falso", "/triage/swagger-ui-falso", "/triage/pacientes"})
    void shouldRequireKey_WhenPathIsNotPublic(String ruta) {
        assertThat(pasa(peticion(ruta, null))).isFalse();
    }
}
