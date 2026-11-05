package co.com.vidacruz.triage.exception;

import static co.com.vidacruz.triage.utilities.Constants.CODIGO_VALIDACION;
import static co.com.vidacruz.triage.utilities.Constants.MSG_VALIDACION;

import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class ValidationException extends BusinessException {

    private final transient List<ErrorDetalleDTO> errores;

    public ValidationException(List<ErrorDetalleDTO> errores) {
        super(ErrorType.VALIDACION, CODIGO_VALIDACION, mensajeNombrandoCampos(errores));
        this.errores = List.copyOf(errores);
    }

    private static String mensajeNombrandoCampos(List<ErrorDetalleDTO> errores) {
        String campos = errores.stream().map(ErrorDetalleDTO::getCampo).collect(Collectors.joining(", "));
        return String.format(MSG_VALIDACION, campos);
    }
}
