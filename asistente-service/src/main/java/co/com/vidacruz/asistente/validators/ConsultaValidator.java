package co.com.vidacruz.asistente.validators;

import static co.com.vidacruz.asistente.utilities.Constants.MAX_LONGITUD_PREGUNTA;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_PREGUNTA_LARGA;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_PREGUNTA_REQUERIDA;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.CAMPO_PREGUNTA;

import co.com.vidacruz.asistente.dto.request.ConsultaRequestDTO;
import co.com.vidacruz.asistente.dto.response.ErrorCampoDTO;
import co.com.vidacruz.asistente.exception.ValidationException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConsultaValidator {

    public void validar(ConsultaRequestDTO request) {
        String pregunta = request == null ? null : request.getPregunta();
        if (pregunta == null || pregunta.isBlank()) {
            throw errorEnPregunta(MSG_PREGUNTA_REQUERIDA);
        }
        if (pregunta.strip().length() > MAX_LONGITUD_PREGUNTA) {
            throw errorEnPregunta(MSG_PREGUNTA_LARGA);
        }
    }

    private static ValidationException errorEnPregunta(String mensaje) {
        return new ValidationException(List.of(new ErrorCampoDTO(CAMPO_PREGUNTA, mensaje)));
    }
}
