package co.com.vidacruz.triage.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    VALIDACION(HttpStatus.BAD_REQUEST),
    NO_ENCONTRADO(HttpStatus.NOT_FOUND),
    CONFLICTO(HttpStatus.CONFLICT),
    NO_AUTORIZADO(HttpStatus.UNAUTHORIZED),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
}
