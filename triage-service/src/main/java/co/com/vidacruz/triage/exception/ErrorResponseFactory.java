package co.com.vidacruz.triage.exception;

import static co.com.vidacruz.triage.utilities.Constants.HEADER_CORRELATION_ID;

import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import co.com.vidacruz.triage.utilities.LogSanitizer;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Única fábrica del formato de error: la usan el handler de excepciones y los filtros. */
@Component
@RequiredArgsConstructor
public class ErrorResponseFactory {

    private final Clock clock;

    public ErrorResponseDTO crear(HttpStatusCode status, String codigo, String mensaje,
                                  List<ErrorDetalleDTO> errores, ServerWebExchange exchange) {
        return ErrorResponseDTO.builder()
                .timestamp(clock.instant())
                .status(status.value())
                .codigo(codigo)
                .mensaje(mensaje)
                .path(LogSanitizer.sanear(exchange.getRequest().getPath().value()))
                .correlationId(exchange.getResponse().getHeaders().getFirst(HEADER_CORRELATION_ID))
                .errores(errores)
                .build();
    }
}
