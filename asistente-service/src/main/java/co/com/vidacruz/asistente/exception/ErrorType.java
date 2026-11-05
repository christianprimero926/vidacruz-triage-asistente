package co.com.vidacruz.asistente.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {

    VALIDACION(HttpStatus.BAD_REQUEST),
    SERVICIO_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;
}
