package co.com.vidacruz.triage.exception;

import lombok.Getter;

/** Falla de un canal de alerta; el mensaje se guarda como último error y no debe incluir secretos. */
@Getter
public class CanalAlertaException extends RuntimeException {

    private final boolean reintentable;

    public CanalAlertaException(String mensaje, boolean reintentable, Throwable causa) {
        super(mensaje, causa);
        this.reintentable = reintentable;
    }

    public static CanalAlertaException noConfigurado(String mensaje) {
        return new CanalAlertaException(mensaje, false, null);
    }
}
