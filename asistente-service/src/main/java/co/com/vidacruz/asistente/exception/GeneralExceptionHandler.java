package co.com.vidacruz.asistente.exception;

import static co.com.vidacruz.asistente.utilities.Constants.COD_CUERPO_INVALIDO;
import static co.com.vidacruz.asistente.utilities.Constants.COD_ERROR_INTERNO;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_CUERPO_INVALIDO;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_ERROR_INTERNO;
import static co.com.vidacruz.asistente.utilities.Constants.REINTENTO_SEGUNDOS;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.CAMPO_CUERPO;

import co.com.vidacruz.asistente.dto.response.ErrorCampoDTO;
import co.com.vidacruz.asistente.dto.response.ErrorResponseDTO;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import co.com.vidacruz.asistente.config.ErrorResponseFactory;
import co.com.vidacruz.asistente.utilities.Saneador;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GeneralExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(ValidationException ex, ServerWebExchange exchange) {
        return responder(exchange, ex.getType().getStatus(), ex.getCodigo(), ex.getMessage(), ex.getErrores());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusiness(BusinessException ex, ServerWebExchange exchange) {
        HttpStatus status = ex.getType().getStatus();
        log.warn("Error de negocio {}: {}", ex.getCodigo(), ex.getMessage());
        ResponseEntity<ErrorResponseDTO> respuesta =
                responder(exchange, status, ex.getCodigo(), ex.getMessage(), null);
        if (status != HttpStatus.SERVICE_UNAVAILABLE) {
            return respuesta;
        }
        return ResponseEntity.status(status).header(HttpHeaders.RETRY_AFTER, REINTENTO_SEGUNDOS)
                .body(respuesta.getBody());
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponseDTO> handleInput(ServerWebInputException ex, ServerWebExchange exchange) {
        List<ErrorCampoDTO> errores = List.of(new ErrorCampoDTO(campoInvalido(ex), MSG_CUERPO_INVALIDO));
        return responder(exchange, HttpStatus.BAD_REQUEST, COD_CUERPO_INVALIDO,
                MSG_CUERPO_INVALIDO + ": " + errores.get(0).getCampo(), errores);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatusCode status = ex.getStatusCode();
        String codigo = status instanceof HttpStatus http ? http.name() : "HTTP_" + status.value();
        ResponseEntity<ErrorResponseDTO> respuesta = responder(exchange, status, codigo, mensajePara(status), null);
        return ResponseEntity.status(status).headers(ex.getHeaders()).body(respuesta.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, ServerWebExchange exchange) {
        log.error("Error no previsto en {}", Saneador.limpiar(exchange.getRequest().getPath().value()), ex);
        return responder(exchange, HttpStatus.INTERNAL_SERVER_ERROR, COD_ERROR_INTERNO, MSG_ERROR_INTERNO, null);
    }

    private ResponseEntity<ErrorResponseDTO> responder(ServerWebExchange exchange, HttpStatusCode status,
                                                       String codigo, String mensaje, List<ErrorCampoDTO> errores) {
        return ResponseEntity.status(status)
                .body(errorResponseFactory.crear(exchange, status, codigo, mensaje, errores));
    }

    private static String mensajePara(HttpStatusCode status) {
        return status instanceof HttpStatus http ? http.getReasonPhrase() : "Error en la solicitud";
    }

    /** Si Jackson señaló qué campo falló (tipo incorrecto, valor ilegible) se informa; si no, se habla del cuerpo. */
    private static String campoInvalido(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof JsonMappingException mapeo && !mapeo.getPath().isEmpty()) {
                String campo = mapeo.getPath().get(mapeo.getPath().size() - 1).getFieldName();
                return campo != null ? campo : CAMPO_CUERPO;
            }
        }
        return CAMPO_CUERPO;
    }
}
