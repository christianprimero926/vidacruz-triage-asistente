package co.com.vidacruz.triage.validators;

import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_DECIMALES;
import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_LONGITUD;
import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_LONGITUD_MAXIMA;
import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_OBLIGATORIO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_RANGO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_VACIO;

import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import co.com.vidacruz.triage.exception.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Acumula los errores de todos los campos para responder con la lista completa y no solo el primero. */
class ErrorCollector {

    private final List<ErrorDetalleDTO> errores = new ArrayList<>();

    boolean tieneError(String campo) {
        return errores.stream().anyMatch(error -> error.getCampo().equals(campo));
    }

    void agregar(String campo, String mensaje) {
        errores.add(ErrorDetalleDTO.builder().campo(campo).mensaje(mensaje).build());
    }

    void rango(String campo, Integer valor, int minimo, int maximo) {
        if (valor == null) {
            obligatorio(campo);
        } else if (valor < minimo || valor > maximo) {
            agregar(campo, String.format(MSG_CAMPO_RANGO, campo, minimo, maximo));
        }
    }

    void rango(String campo, BigDecimal valor, RangoDecimal limites) {
        if (valor == null) {
            obligatorio(campo);
        } else if (valor.compareTo(limites.minimo()) < 0 || valor.compareTo(limites.maximo()) > 0) {
            agregar(campo, String.format(MSG_CAMPO_RANGO, campo, limites.minimo().toPlainString(), limites.maximo().toPlainString()));
        } else if (valor.stripTrailingZeros().scale() > limites.decimalesMaximos()) {
            agregar(campo, String.format(MSG_CAMPO_DECIMALES, campo, limites.decimalesMaximos()));
        }
    }

    void longitud(String campo, String valor, int minimo, int maximo) {
        if (valor == null) {
            obligatorio(campo);
            return;
        }
        int largo = valor.trim().length();
        if (largo < minimo || largo > maximo) {
            agregar(campo, String.format(MSG_CAMPO_LONGITUD, campo, minimo, maximo));
        }
    }

    void textoObligatorio(String campo, String valor, int maximo) {
        if (valor == null) {
            obligatorio(campo);
        } else if (valor.isBlank()) {
            agregar(campo, String.format(MSG_CAMPO_VACIO, campo));
        } else if (valor.trim().length() > maximo) {
            agregar(campo, String.format(MSG_CAMPO_LONGITUD_MAXIMA, campo, maximo));
        }
    }

    void lanzarSiHayErrores() {
        if (!errores.isEmpty()) {
            throw new ValidationException(errores);
        }
    }

    private void obligatorio(String campo) {
        agregar(campo, String.format(MSG_CAMPO_OBLIGATORIO, campo));
    }
}
