package co.com.vidacruz.triage.exception;

import static co.com.vidacruz.triage.utilities.Constants.CODIGO_PACIENTE_NO_ENCONTRADO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_PACIENTE_NO_ENCONTRADO;

import java.util.UUID;

public class PacienteNoEncontradoException extends BusinessException {

    public PacienteNoEncontradoException(UUID id) {
        super(ErrorType.NO_ENCONTRADO, CODIGO_PACIENTE_NO_ENCONTRADO, String.format(MSG_PACIENTE_NO_ENCONTRADO, id));
    }
}
