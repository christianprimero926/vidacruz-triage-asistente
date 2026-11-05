package co.com.vidacruz.triage.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("GeneralExceptionHandler")
class GeneralExceptionHandlerTest {

    private final GeneralExceptionHandler handler = new GeneralExceptionHandler(
            new ErrorResponseFactory(Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC)));
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

    @Test
    @DisplayName("una conexión rechazada al consultar responde 503 con Retry-After y sin detalles internos")
    void shouldRespondServiceUnavailable_WhenDatabaseRefusesConnection() {
        var error = catchThrowable(() -> DatabaseClient.create(conexionRechazada())
                .sql("select 1").fetch().one().block());

        verificarNoDisponible(handler.handleUnexpected((Exception) error, exchange));
    }

    @Test
    @DisplayName("una conexión rechazada al abrir la transacción responde 503 con el mismo código")
    void shouldRespondServiceUnavailable_WhenTransactionCannotOpen() {
        ConnectionFactory fabrica = conexionRechazada();
        var error = catchThrowable(() -> TransactionalOperator.create(new R2dbcTransactionManager(fabrica))
                .transactional(DatabaseClient.create(fabrica).sql("select 1").fetch().one()).block());

        verificarNoDisponible(handler.handleUnexpected((Exception) error, exchange));
    }

    @Test
    @DisplayName("un ConnectException sin envoltorio de Spring también responde 503")
    void shouldRespondServiceUnavailable_WhenCauseIsConnectionRefused() {
        var error = new IllegalStateException("fallo", new ConnectException("Connection refused"));

        verificarNoDisponible(handler.handleUnexpected(error, exchange));
    }

    private static ConnectionFactory conexionRechazada() {
        return ConnectionFactories.get("r2dbc:pool:postgresql://u:p@127.0.0.1:1/db?connectTimeout=PT2S");
    }

    private static void verificarNoDisponible(ResponseEntity<ErrorResponseDTO> respuesta) {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(respuesta.getBody().getCodigo()).isEqualTo("BASE_DATOS_NO_DISPONIBLE");
        assertThat(respuesta.getBody().getMensaje()).doesNotContain("127.0.0.1", "postgres", "Connection");
    }
}
