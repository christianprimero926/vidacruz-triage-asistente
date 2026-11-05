package co.com.vidacruz.triage.validators;

import static co.com.vidacruz.triage.utilities.Constants.MSG_CAMPO_OBLIGATORIO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_FECHA_INVALIDA;
import static co.com.vidacruz.triage.utilities.Constants.MSG_RANGO_MAXIMO;
import static co.com.vidacruz.triage.utilities.Constants.MSG_RANGO_ORDEN;
import static co.com.vidacruz.triage.utilities.ConstantsFields.DESDE;
import static co.com.vidacruz.triage.utilities.ConstantsFields.HASTA;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.dto.request.RangoReporteDTO;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReporteValidator {

    private final TriageProperties properties;

    /** Acepta fecha y hora con zona (Z u offset) o solo la fecha, que se toma a las 00:00 UTC. */
    public RangoReporteDTO validar(String desdeTexto, String hastaTexto) {
        ErrorCollector errores = new ErrorCollector();
        Instant desde = interpretar(errores, DESDE, desdeTexto);
        Instant hasta = interpretar(errores, HASTA, hastaTexto);
        if (desde != null && hasta != null) {
            validarRango(errores, desde, hasta);
        }
        errores.lanzarSiHayErrores();
        return new RangoReporteDTO(desde, hasta);
    }

    private void validarRango(ErrorCollector errores, Instant desde, Instant hasta) {
        int maximoDias = properties.getReporte().getRangoMaximoDias();
        if (!desde.isBefore(hasta)) {
            errores.agregar(HASTA, MSG_RANGO_ORDEN);
        } else if (Duration.between(desde, hasta).compareTo(Duration.ofDays(maximoDias)) > 0) {
            errores.agregar(HASTA, String.format(MSG_RANGO_MAXIMO, maximoDias));
        }
    }

    private Instant interpretar(ErrorCollector errores, String campo, String texto) {
        if (texto == null || texto.isBlank()) {
            errores.agregar(campo, String.format(MSG_CAMPO_OBLIGATORIO, campo));
            return null;
        }
        try {
            return OffsetDateTime.parse(texto.trim()).toInstant();
        } catch (DateTimeParseException noEsFechaHora) {
            return interpretarSoloFecha(errores, campo, texto.trim());
        }
    }

    private Instant interpretarSoloFecha(ErrorCollector errores, String campo, String texto) {
        try {
            return LocalDate.parse(texto).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException noEsFecha) {
            errores.agregar(campo, String.format(MSG_FECHA_INVALIDA, campo));
            return null;
        }
    }
}
