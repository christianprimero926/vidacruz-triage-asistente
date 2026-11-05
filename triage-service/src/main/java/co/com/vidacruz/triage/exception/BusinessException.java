package co.com.vidacruz.triage.exception;

import lombok.Getter;

/** Error de negocio no verificado: viaja como señal de error del flujo reactivo. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorType type;
    private final String codigo;

    public BusinessException(ErrorType type, String codigo, String mensaje) {
        super(mensaje);
        this.type = type;
        this.codigo = codigo;
    }
}
