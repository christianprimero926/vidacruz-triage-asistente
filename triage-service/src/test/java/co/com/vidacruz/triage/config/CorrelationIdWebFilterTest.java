package co.com.vidacruz.triage.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("CorrelationIdWebFilter")
class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filtro = new CorrelationIdWebFilter();

    private record Resultado(String cabecera, String contexto) {
    }

    private Resultado ejecutar(String recibido) {
        var builder = MockServerHttpRequest.get("/x");
        if (recibido != null) {
            builder.header("X-Correlation-Id", recibido);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder);
        AtomicReference<String> enContexto = new AtomicReference<>();
        filtro.filter(exchange, ex -> Mono.deferContextual(contexto -> {
            enContexto.set(contexto.getOrDefault("correlationId", null));
            return Mono.empty();
        })).block(Duration.ofSeconds(2));
        return new Resultado(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"), enContexto.get());
    }

    @Test
    @DisplayName("reutiliza la correlación recibida si tiene el formato esperado y la deja en el contexto")
    void shouldReuseCorrelation_WhenFormatIsValid() {
        Resultado resultado = ejecutar("pedido-12345678");

        assertThat(resultado.cabecera()).isEqualTo("pedido-12345678");
        assertThat(resultado.contexto()).isEqualTo("pedido-12345678");
    }

    @ParameterizedTest(name = "\"{0}\" se descarta")
    @ValueSource(strings = {"corto", "con espacios y mas", "inyeccion;<script>", "ñandu-ñandu-ñandu", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void shouldGenerateNewOne_WhenFormatIsInvalid(String recibido) {
        Resultado resultado = ejecutar(recibido);

        assertThat(resultado.cabecera()).isNotEqualTo(recibido).matches("[0-9a-f-]{36}");
        assertThat(resultado.contexto()).isEqualTo(resultado.cabecera());
    }

    @Test
    @DisplayName("genera una cuando no llega ninguna y acepta el límite de 8 y de 64 caracteres")
    void shouldGenerateOne_WhenHeaderIsMissing() {
        assertThat(ejecutar(null).cabecera()).matches("[0-9a-f-]{36}");
        assertThat(ejecutar("12345678").cabecera()).isEqualTo("12345678");
        assertThat(ejecutar("x".repeat(64)).cabecera()).isEqualTo("x".repeat(64));
        assertThat(ejecutar("x".repeat(65)).cabecera()).hasSize(36);
    }
}
