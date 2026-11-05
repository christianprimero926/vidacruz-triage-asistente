package co.com.vidacruz.asistente.exception;

import static co.com.vidacruz.asistente.utilities.Constants.COD_VALIDACION;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_VALIDACION;

import co.com.vidacruz.asistente.dto.response.ErrorCampoDTO;
import java.util.List;
import lombok.Getter;

@Getter
public class ValidationException extends BusinessException {

    private final transient List<ErrorCampoDTO> errores;

    public ValidationException(List<ErrorCampoDTO> errores) {
        super(ErrorType.VALIDACION, COD_VALIDACION, MSG_VALIDACION + nombresDeCampos(errores));
        this.errores = errores;
    }

    private static String nombresDeCampos(List<ErrorCampoDTO> errores) {
        return String.join(", ", errores.stream().map(ErrorCampoDTO::getCampo).toList());
    }
}
