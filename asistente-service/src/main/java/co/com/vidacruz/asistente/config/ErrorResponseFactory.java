package co.com.vidacruz.asistente.config;

import static co.com.vidacruz.asistente.utilities.Constants.HEADER_CORRELACION;

import co.com.vidacruz.asistente.dto.response.ErrorCampoDTO;
import co.com.vidacruz.asistente.dto.response.ErrorResponseDTO;
import co.com.vidacruz.asistente.utilities.Saneador;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Único lugar donde se arma el cuerpo de error, para el handler de excepciones y para el filtro de API key. */
@Component
@RequiredArgsConstructor
public class ErrorResponseFactory {

    private final Clock clock;

    public ErrorResponseDTO crear(ServerWebExchange exchange, HttpStatusCode status, String codigo, String mensaje,
                                  List<ErrorCampoDTO> errores) {
        return ErrorResponseDTO.builder()
                .timestamp(clock.instant())
                .status(status.value())
                .codigo(codigo)
                .mensaje(mensaje)
                .path(Saneador.limpiar(exchange.getRequest().getPath().value()))
                .correlationId(exchange.getResponse().getHeaders().getFirst(HEADER_CORRELACION))
                .errores(errores)
                .build();
    }
}
