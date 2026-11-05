package co.com.vidacruz.triage.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("GeneralExceptionHandler")
class GeneralExceptionHandlerTest {

    private final GeneralExceptionHandler handler = new GeneralExceptionHandler(
            Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC));
    private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

    @Test
    @DisplayName("un ResponseStatusException 5xx responde con el código de error interno y sin detalles")
    void shouldRespondInternalError_WhenResponseStatusIsServerError() {
        var respuesta = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "detalle del proxy"), exchange);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        assertThat(respuesta.getBody().getCodigo()).isEqualTo("ERROR_INTERNO");
        assertThat(respuesta.getBody().getMensaje()).doesNotContain("proxy");
    }

    @Test
    @DisplayName("un ResponseStatusException 4xx conserva su estado")
    void shouldKeepStatus_WhenResponseStatusIsClientError() {
        var respuesta = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND), exchange);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody().getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO");
    }
}
