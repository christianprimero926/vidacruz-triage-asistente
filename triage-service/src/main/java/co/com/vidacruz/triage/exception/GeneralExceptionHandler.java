package co.com.vidacruz.triage.exception;

import static co.com.vidacruz.triage.utilities.Constants.CODIGO_BASE_DATOS_NO_DISPONIBLE;
import static co.com.vidacruz.triage.utilities.Constants.CODIGO_ERROR_INTERNO;
import static co.com.vidacruz.triage.utilities.Constants.CODIGO_MEDIA_TYPE_NO_SOPORTADO;
import static co.com.vidacruz.triage.utilities.Constants.CODIGO_METODO_NO_PERMITIDO;
import static co.com.vidacruz.triage.utilities.Constants.CODIGO_RECURSO_NO_ENCONTRADO;
import static co.com.vidacruz.triage.utilities.Constants.CODIGO_SOLICITUD_INVALIDA;
import static co.com.vidacruz.triage.utilities.Constants.CODIGO_VALIDACION;
import static co.com.vidacruz.triage.utilities.Constants.MSG_BASE_DATOS_NO_DISPONIBLE;
import static co.com.vidacruz.triage.utilities.Constants.MSG_ERROR_INTERNO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_MEDIA_TYPE_NO_SOPORTADO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_METODO_NO_PERMITIDO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_RECURSO_NO_ENCONTRADO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_SOLICITUD_INVALIDA;
import static co.com.vidacruz.triage.utilities.Constants.MSG_VALIDACION;
import static co.com.vidacruz.triage.utilities.Constants.SEGUNDOS_REINTENTO_BASE_DATOS;

import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import co.com.vidacruz.triage.dto.response.ErrorResponseDTO;
import co.com.vidacruz.triage.utilities.InputErrorUtils;
import co.com.vidacruz.triage.utilities.LogSanitizer;
import io.r2dbc.spi.R2dbcTimeoutException;
import io.r2dbc.spi.R2dbcException;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GeneralExceptionHandler {

    private final ErrorResponseFactory errorFactory;

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(ValidationException ex, ServerWebExchange exchange) {
        return responder(ex.getType().getStatus(), ex.getCodigo(), ex.getMessage(), ex.getErrores(), exchange);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusiness(BusinessException ex, ServerWebExchange exchange) {
        return responder(ex.getType().getStatus(), ex.getCodigo(), ex.getMessage(), null, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponseDTO> handleInput(ServerWebInputException ex, ServerWebExchange exchange) {
        return InputErrorUtils.campoInvalido(ex)
                .map(detalle -> responder(HttpStatus.BAD_REQUEST, CODIGO_VALIDACION,
                        String.format(MSG_VALIDACION, detalle.getCampo()), List.of(detalle), exchange))
                .orElseGet(() -> responder(HttpStatus.BAD_REQUEST, CODIGO_SOLICITUD_INVALIDA,
                        MSG_SOLICITUD_INVALIDA, null, exchange));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is5xxServerError()) {
            return handleUnexpected(ex, exchange);
        }
        return responder(status, codigoPara(status), mensajePara(status), null, exchange);
    }

    ResponseEntity<ErrorResponseDTO> handleBaseDatosNoDisponible(Exception ex, ServerWebExchange exchange) {
        log.error("Base de datos no disponible en {} {}: {}", exchange.getRequest().getMethod(),
                LogSanitizer.sanear(exchange.getRequest().getPath().value()), ex.toString());
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, SEGUNDOS_REINTENTO_BASE_DATOS)
                .body(errorFactory.crear(status, CODIGO_BASE_DATOS_NO_DISPONIBLE, MSG_BASE_DATOS_NO_DISPONIBLE,
                        null, exchange));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, ServerWebExchange exchange) {
        if (esFalloDeConexion(ex)) {
            return handleBaseDatosNoDisponible(ex, exchange);
        }
        log.error("Error no previsto en {} {}", exchange.getRequest().getMethod(),
                LogSanitizer.sanear(exchange.getRequest().getPath().value()), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, CODIGO_ERROR_INTERNO, MSG_ERROR_INTERNO, null, exchange);
    }

    /**
     * Solo los fallos de conexión (IO, timeout del pool, SQLSTATE 08/57P0 o imposibilidad de abrir la transacción):
     * los errores SQL de la propia consulta, aunque Spring los traduzca igual, siguen siendo errores internos.
     */
    private static boolean esFalloDeConexion(Throwable ex) {
        for (Throwable causa = ex; causa != null && causa.getCause() != causa; causa = causa.getCause()) {
            if (causa instanceof CannotCreateTransactionException || causa instanceof IOException
                    || causa instanceof R2dbcTimeoutException || estadoSqlDeConexion(causa)) {
                return true;
            }
        }
        return false;
    }

    private static boolean estadoSqlDeConexion(Throwable causa) {
        String estado = causa instanceof R2dbcException r2dbc ? r2dbc.getSqlState() : null;
        return estado != null && (estado.startsWith("08") || estado.startsWith("57P0"));
    }

    private ResponseEntity<ErrorResponseDTO> responder(HttpStatusCode status, String codigo, String mensaje,
                                                       List<ErrorDetalleDTO> errores, ServerWebExchange exchange) {
        return ResponseEntity.status(status)
                .body(errorFactory.crear(status, codigo, mensaje, errores, exchange));
    }

    private static String codigoPara(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> CODIGO_RECURSO_NO_ENCONTRADO;
            case 405 -> CODIGO_METODO_NO_PERMITIDO;
            case 415 -> CODIGO_MEDIA_TYPE_NO_SOPORTADO;
            default -> CODIGO_SOLICITUD_INVALIDA;
        };
    }

    private static String mensajePara(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> MSG_RECURSO_NO_ENCONTRADO;
            case 405 -> MSG_METODO_NO_PERMITIDO;
            case 415 -> MSG_MEDIA_TYPE_NO_SOPORTADO;
            default -> MSG_SOLICITUD_INVALIDA;
        };
    }
}
