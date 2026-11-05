package co.com.vidacruz.asistente.exception;

import static co.com.vidacruz.asistente.utilities.Constants.COD_LLM_NO_CONFIGURADO;
import static co.com.vidacruz.asistente.utilities.Constants.COD_LLM_NO_DISPONIBLE;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_LLM_NO_CONFIGURADO;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_LLM_NO_DISPONIBLE;

/** Cualquier causa por la que no se obtuvo una respuesta utilizable del LLM; se responde 503. */
public class LlmNoDisponibleException extends BusinessException {

    private LlmNoDisponibleException(String codigo, String mensaje, Throwable causa) {
        super(ErrorType.SERVICIO_NO_DISPONIBLE, codigo, mensaje, causa);
    }

    public static LlmNoDisponibleException porFallo(Throwable causa) {
        return new LlmNoDisponibleException(COD_LLM_NO_DISPONIBLE, MSG_LLM_NO_DISPONIBLE, causa);
    }

    public static LlmNoDisponibleException noConfigurado() {
        return new LlmNoDisponibleException(COD_LLM_NO_CONFIGURADO, MSG_LLM_NO_CONFIGURADO, null);
    }
}
