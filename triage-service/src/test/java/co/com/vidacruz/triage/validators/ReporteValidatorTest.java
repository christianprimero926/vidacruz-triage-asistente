package co.com.vidacruz.triage.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.dto.request.RangoReporteDTO;
import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import co.com.vidacruz.triage.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ReporteValidator")
class ReporteValidatorTest {

    private final ReporteValidator validator = new ReporteValidator(new TriageProperties());

    private ValidationException rechazo(String desde, String hasta) {
        return assertThrows(ValidationException.class, () -> validator.validar(desde, hasta));
    }

    private List<String> campos(ValidationException error) {
        return error.getErrores().stream().map(ErrorDetalleDTO::getCampo).toList();
    }

    @Test
    @DisplayName("convierte fechas con Z y con offset al mismo instante")
    void shouldParseInstants_WhenDatesHaveZoneOrOffset() {
        RangoReporteDTO rango = validator.validar("2026-11-05T05:00:00-05:00", "2026-11-05T12:00:00Z");

        assertThat(rango.desde()).isEqualTo(Instant.parse("2026-11-05T10:00:00Z"));
        assertThat(rango.hasta()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
    }

    @Test
    @DisplayName("un offset positivo sin codificar en la URL (llega como espacio) se trata como +")
    void shouldTreatSpaceAsPlus_WhenOffsetArrivesUnencoded() {
        RangoReporteDTO rango = validator.validar("2026-11-05T11:00:00 01:00", "2026-11-05T12:00:00.5 00:00");

        assertThat(rango.desde()).isEqualTo(Instant.parse("2026-11-05T10:00:00Z"));
        assertThat(rango.hasta()).isEqualTo(Instant.parse("2026-11-05T12:00:00.5Z"));
    }

    @Test
    @DisplayName("una fecha sin hora se toma a medianoche UTC")
    void shouldUseMidnightUtc_WhenOnlyDateIsGiven() {
        RangoReporteDTO rango = validator.validar("2026-11-05", "2026-11-06");

        assertThat(rango.desde()).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
        assertThat(rango.hasta()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
    }

    @Test
    @DisplayName("admite exactamente 31 días y rechaza un segundo más")
    void shouldAcceptExactlyMaximumRange_WhenItIsThirtyOneDays() {
        assertThat(validator.validar("2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z")).isNotNull();

        ValidationException error = rechazo("2026-10-01T00:00:00Z", "2026-11-01T00:00:01Z");
        assertThat(campos(error)).containsExactly("hasta");
        assertThat(error.getErrores().get(0).getMensaje()).isEqualTo("El rango entre 'desde' y 'hasta' no puede superar 31 días");
    }

    @Test
    @DisplayName("el máximo sale de la propiedad configurada")
    void shouldUseConfiguredMaximum_WhenPropertyChanges() {
        var propiedades = new TriageProperties();
        propiedades.getReporte().setRangoMaximoDias(2);

        assertThrows(ValidationException.class,
                () -> new ReporteValidator(propiedades).validar("2026-11-01", "2026-11-04"));
    }

    @ParameterizedTest(name = "desde={0} hasta={1}")
    @CsvSource({"2026-11-05T10:00:00Z,2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z,2026-11-05T10:00:00Z"})
    void shouldReject_WhenDesdeIsNotBeforeHasta(String desde, String hasta) {
        ValidationException error = rechazo(desde, hasta);

        assertThat(campos(error)).containsExactly("hasta");
        assertThat(error.getErrores().get(0).getMensaje()).isEqualTo("El campo 'hasta' debe ser posterior a 'desde'");
    }

    @Test
    @DisplayName("reporta como obligatorios los dos campos ausentes")
    void shouldReportBothFields_WhenBothAreMissing() {
        assertThat(campos(rechazo(null, " "))).containsExactly("desde", "hasta");
    }

    @Test
    @DisplayName("nombra el campo con formato inválido")
    void shouldNameTheField_WhenFormatIsInvalid() {
        ValidationException error = rechazo("ayer", "2026-11-05T10:00:00Z");

        assertThat(campos(error)).containsExactly("desde");
        assertThat(error.getErrores().get(0).getMensaje())
                .isEqualTo("El campo 'desde' debe ser una fecha ISO 8601, por ejemplo 2026-11-05T10:00:00Z");
    }

    @Test
    @DisplayName("una fecha y hora sin zona no es válida")
    void shouldReject_WhenDateTimeHasNoZone() {
        assertThat(campos(rechazo("2026-11-05T10:00:00", "2026-11-05T12:00:00Z"))).containsExactly("desde");
    }
}
