package co.com.vidacruz.triage.utilities;

import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_TIPO_INVALIDO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_PARAMETRO_INVALIDO;

import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.web.server.ServerWebInputException;

/** Extrae el campo responsable de un error de entrada, cuando Spring o Jackson lo informan. */
@UtilityClass
public class InputErrorUtils {

    public static Optional<ErrorDetalleDTO> campoInvalido(ServerWebInputException ex) {
        for (Throwable causa = ex.getCause(); causa != null; causa = siguiente(causa)) {
            if (causa instanceof JsonMappingException mapping) {
                return campoDeJson(mapping);
            }
            if (causa instanceof TypeMismatchException) {
                return parametroDeRuta(ex);
            }
        }
        return Optional.empty();
    }

    private static Throwable siguiente(Throwable causa) {
        return causa.getCause() == causa ? null : causa.getCause();
    }

    private static Optional<ErrorDetalleDTO> campoDeJson(JsonMappingException mapping) {
        return mapping.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .findFirst()
                .map(campo -> detalle(campo, String.format(MSG_CAMPO_TIPO_INVALIDO, campo)));
    }

    private static Optional<ErrorDetalleDTO> parametroDeRuta(ServerWebInputException ex) {
        MethodParameter parametro = ex.getMethodParameter();
        return Optional.ofNullable(parametro)
                .map(MethodParameter::getParameterName)
                .map(nombre -> detalle(nombre, String.format(MSG_PARAMETRO_INVALIDO, nombre)));
    }

    private static ErrorDetalleDTO detalle(String campo, String mensaje) {
        return ErrorDetalleDTO.builder().campo(campo).mensaje(mensaje).build();
    }
}
