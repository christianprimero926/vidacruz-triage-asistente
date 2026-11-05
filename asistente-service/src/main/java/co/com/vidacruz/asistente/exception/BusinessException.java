package co.com.vidacruz.asistente.exception;

import lombok.Getter;

/** Error de negocio previsto; se propaga como señal de error en el flujo reactivo. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorType type;
    private final String codigo;

    public BusinessException(ErrorType type, String codigo, String mensaje) {
        super(mensaje);
        this.type = type;
        this.codigo = codigo;
    }

    public BusinessException(ErrorType type, String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.type = type;
        this.codigo = codigo;
    }
}
