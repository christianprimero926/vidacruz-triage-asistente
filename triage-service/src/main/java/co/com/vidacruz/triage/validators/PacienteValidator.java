package co.com.vidacruz.triage.validators;

import static co.com.vidacruz.triage.utilities.Constants.MSG_PRESION_DIASTOLICA;
import static co.com.vidacruz.triage.utilities.ConstantsFields.DOLOR;
import static co.com.vidacruz.triage.utilities.ConstantsFields.EDAD;
import static co.com.vidacruz.triage.utilities.ConstantsFields.FRECUENCIA_CARDIACA;
import static co.com.vidacruz.triage.utilities.ConstantsFields.GCS;
import static co.com.vidacruz.triage.utilities.ConstantsFields.NOMBRE_COMPLETO;
import static co.com.vidacruz.triage.utilities.ConstantsFields.PRESION_DIASTOLICA;
import static co.com.vidacruz.triage.utilities.ConstantsFields.PRESION_SISTOLICA;
import static co.com.vidacruz.triage.utilities.ConstantsFields.QUEJA_PRINCIPAL;
import static co.com.vidacruz.triage.utilities.ConstantsFields.SPO2;
import static co.com.vidacruz.triage.utilities.ConstantsFields.TEMPERATURA;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class PacienteValidator {

    private static final RangoDecimal RANGO_TEMPERATURA =
            new RangoDecimal(new BigDecimal("34.0"), new BigDecimal("42.0"), 2);
    private static final int QUEJA_LONGITUD_MAXIMA = 500;

    /** Lanza ValidationException con todos los campos inválidos. */
    public void validar(PacienteRequestDTO request) {
        ErrorCollector errores = new ErrorCollector();
        errores.longitud(NOMBRE_COMPLETO, request.getNombreCompleto(), 3, 100);
        errores.rango(EDAD, request.getEdad(), 0, 120);
        errores.rango(FRECUENCIA_CARDIACA, request.getFrecuenciaCardiaca(), 20, 250);
        errores.rango(PRESION_SISTOLICA, request.getPresionSistolica(), 60, 260);
        errores.rango(PRESION_DIASTOLICA, request.getPresionDiastolica(), 30, 160);
        validarDiastolicaMenorQueSistolica(request, errores);
        errores.rango(SPO2, request.getSpo2(), 70, 100);
        errores.rango(TEMPERATURA, request.getTemperatura(), RANGO_TEMPERATURA);
        errores.rango(DOLOR, request.getDolor(), 0, 10);
        errores.rango(GCS, request.getGcs(), 3, 15);
        errores.textoObligatorio(QUEJA_PRINCIPAL, request.getQuejaPrincipal(), QUEJA_LONGITUD_MAXIMA);
        errores.lanzarSiHayErrores();
    }

    /** Solo se compara si ninguna de las dos presiones falló ya, para no sumar ruido a un error de rango. */
    private void validarDiastolicaMenorQueSistolica(PacienteRequestDTO request, ErrorCollector errores) {
        Integer sistolica = request.getPresionSistolica();
        Integer diastolica = request.getPresionDiastolica();
        if (sistolica != null && diastolica != null && !errores.tieneError(PRESION_DIASTOLICA)
                && !errores.tieneError(PRESION_SISTOLICA) && diastolica >= sistolica) {
            errores.agregar(PRESION_DIASTOLICA, String.format(MSG_PRESION_DIASTOLICA, PRESION_DIASTOLICA));
        }
    }
}
