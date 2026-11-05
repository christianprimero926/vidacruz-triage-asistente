package co.com.vidacruz.triage.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.ErrorDetalleDTO;
import co.com.vidacruz.triage.exception.ErrorType;
import co.com.vidacruz.triage.exception.ValidationException;
import co.com.vidacruz.triage.support.PacienteFixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PacienteValidator (rangos del enunciado)")
class PacienteValidatorTest {

    private final PacienteValidator validator = new PacienteValidator();

    private List<String> camposInvalidos(PacienteRequestDTO request) {
        ValidationException error = assertThrows(ValidationException.class, () -> validator.validar(request));
        return error.getErrores().stream().map(ErrorDetalleDTO::getCampo).toList();
    }

    @Nested
    @DisplayName("Solicitud válida")
    class Valida {

        @Test
        @DisplayName("acepta los seis pacientes de ejemplo")
        void shouldAccept_WhenRequestIsAnExampleFromTheStatement() {
            assertThatCode(() -> {
                validator.validar(PacienteFixtures.e1());
                validator.validar(PacienteFixtures.e2());
                validator.validar(PacienteFixtures.e3());
                validator.validar(PacienteFixtures.e4());
                validator.validar(PacienteFixtures.e5());
                validator.validar(PacienteFixtures.e6());
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("acepta todos los valores mínimos y máximos")
        void shouldAccept_WhenValuesAreOnTheInnerBoundaries() {
            var minimos = PacienteFixtures.base().edad(0).frecuenciaCardiaca(20).presionSistolica(60)
                    .presionDiastolica(30).spo2(70).temperatura(new BigDecimal("34.0")).dolor(0).gcs(3).build();
            var maximos = PacienteFixtures.base().edad(120).frecuenciaCardiaca(250).presionSistolica(260)
                    .presionDiastolica(160).spo2(100).temperatura(new BigDecimal("42.0")).dolor(10).gcs(15).build();
            assertThatCode(() -> {
                validator.validar(minimos);
                validator.validar(maximos);
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Rangos numéricos")
    class Rangos {

        @ParameterizedTest(name = "frecuenciaCardiaca {0}")
        @ValueSource(ints = {19, 251})
        void shouldRejectFrecuenciaCardiaca_WhenOutOfRange(int valor) {
            assertThat(camposInvalidos(PacienteFixtures.base().frecuenciaCardiaca(valor).build()))
                    .containsExactly("frecuenciaCardiaca");
        }

        @ParameterizedTest(name = "presionSistolica {0}")
        @ValueSource(ints = {59, 261})
        void shouldRejectPresionSistolica_WhenOutOfRange(int valor) {
            assertThat(camposInvalidos(PacienteFixtures.base().presionSistolica(valor).build()))
                    .containsExactly("presionSistolica");
        }

        @ParameterizedTest(name = "presionDiastolica {0}")
        @ValueSource(ints = {29, 161})
        void shouldRejectPresionDiastolica_WhenOutOfRange(int valor) {
            var request = PacienteFixtures.base().presionSistolica(260).presionDiastolica(valor).build();
            assertThat(camposInvalidos(request)).containsExactly("presionDiastolica");
        }

        @ParameterizedTest(name = "spo2 {0}")
        @ValueSource(ints = {69, 101})
        void shouldRejectSpo2_WhenOutOfRange(int valor) {
            assertThat(camposInvalidos(PacienteFixtures.base().spo2(valor).build())).containsExactly("spo2");
        }

        @ParameterizedTest(name = "dolor {0}")
        @ValueSource(ints = {-1, 11})
        void shouldRejectDolor_WhenOutOfRange(int valor) {
            assertThat(camposInvalidos(PacienteFixtures.base().dolor(valor).build())).containsExactly("dolor");
        }

        @ParameterizedTest(name = "gcs {0}")
        @ValueSource(ints = {2, 16})
        void shouldRejectGcs_WhenOutOfRange(int valor) {
            assertThat(camposInvalidos(PacienteFixtures.base().gcs(valor).build())).containsExactly("gcs");
        }

        @ParameterizedTest(name = "edad {0}")
        @ValueSource(ints = {-1, 121})
        void shouldRejectEdad_WhenOutOfRange(int valor) {
            assertThat(camposInvalidos(PacienteFixtures.base().edad(valor).build())).containsExactly("edad");
        }

        @ParameterizedTest(name = "temperatura {0}")
        @ValueSource(strings = {"33.9", "42.1", "-36.5"})
        void shouldRejectTemperatura_WhenOutOfRange(String valor) {
            var request = PacienteFixtures.base().temperatura(new BigDecimal(valor)).build();
            assertThat(camposInvalidos(request)).containsExactly("temperatura");
        }

        @Test
        @DisplayName("la temperatura con más de dos decimales se rechaza y con ceros sobrantes se acepta")
        void shouldRejectTemperatura_WhenItHasMoreThanTwoDecimals() {
            var tresDecimales = PacienteFixtures.base().temperatura(new BigDecimal("36.123")).build();
            var cerosSobrantes = PacienteFixtures.base().temperatura(new BigDecimal("36.500")).build();

            assertThat(camposInvalidos(tresDecimales)).containsExactly("temperatura");
            assertThatCode(() -> validator.validar(cerosSobrantes)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Presión diastólica contra sistólica")
    class Presiones {

        @ParameterizedTest(name = "sistólica {0} y diastólica {1}")
        @CsvSource({"100,100", "100,101", "90,150"})
        void shouldRejectPresionDiastolica_WhenNotLowerThanSistolica(int sistolica, int diastolica) {
            var request = PacienteFixtures.base().presionSistolica(sistolica).presionDiastolica(diastolica).build();
            assertThat(camposInvalidos(request)).containsExactly("presionDiastolica");
        }

        @Test
        @DisplayName("acepta la diastólica una unidad por debajo de la sistólica")
        void shouldAccept_WhenDiastolicaIsOneBelowSistolica() {
            var request = PacienteFixtures.base().presionSistolica(100).presionDiastolica(99).build();
            assertThatCode(() -> validator.validar(request)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Textos")
    class Textos {

        @ParameterizedTest(name = "nombre de {0} caracteres")
        @ValueSource(ints = {2, 101})
        void shouldRejectNombre_WhenLengthIsOutOfRange(int longitud) {
            var request = PacienteFixtures.base().nombreCompleto("a".repeat(longitud)).build();
            assertThat(camposInvalidos(request)).containsExactly("nombreCompleto");
        }

        @Test
        @DisplayName("el largo del nombre se mide después de recortar espacios")
        void shouldMeasureNombreLength_WhenItHasSurroundingSpaces() {
            var corto = PacienteFixtures.base().nombreCompleto("  ab  ").build();
            var minimo = PacienteFixtures.base().nombreCompleto("  abc  ").build();
            var maximo = PacienteFixtures.base().nombreCompleto("a".repeat(100)).build();

            assertThat(camposInvalidos(corto)).containsExactly("nombreCompleto");
            assertThatCode(() -> {
                validator.validar(minimo);
                validator.validar(maximo);
            }).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "queja \"{0}\"")
        @ValueSource(strings = {"", "   "})
        void shouldRejectQueja_WhenItIsBlank(String queja) {
            var request = PacienteFixtures.base().quejaPrincipal(queja).build();
            assertThat(camposInvalidos(request)).containsExactly("quejaPrincipal");
        }

        @Test
        @DisplayName("la queja admite 500 caracteres y rechaza 501")
        void shouldRejectQueja_WhenItExceeds500Characters() {
            var limite = PacienteFixtures.base().quejaPrincipal("q".repeat(500)).build();
            var excedida = PacienteFixtures.base().quejaPrincipal("q".repeat(501)).build();

            assertThatCode(() -> validator.validar(limite)).doesNotThrowAnyException();
            assertThat(camposInvalidos(excedida)).containsExactly("quejaPrincipal");
        }
    }

    @Nested
    @DisplayName("Campos ausentes y varios errores")
    class Errores {

        @Test
        @DisplayName("un request vacío reporta los diez campos como obligatorios")
        void shouldReportEveryField_WhenRequestIsEmpty() {
            assertThat(camposInvalidos(new PacienteRequestDTO())).containsExactlyInAnyOrder(
                    "nombreCompleto", "edad", "frecuenciaCardiaca", "presionSistolica", "presionDiastolica",
                    "spo2", "temperatura", "dolor", "gcs", "quejaPrincipal");
        }

        @Test
        @DisplayName("junta todos los errores y el mensaje nombra cada campo")
        void shouldCollectAllErrors_WhenSeveralFieldsAreInvalid() {
            var request = PacienteFixtures.base().frecuenciaCardiaca(10).spo2(101).nombreCompleto("ab").build();

            assertThatThrownBy(() -> validator.validar(request))
                    .isInstanceOfSatisfying(ValidationException.class, error -> {
                        assertThat(error.getType()).isEqualTo(ErrorType.VALIDACION);
                        assertThat(error.getErrores()).extracting(ErrorDetalleDTO::getCampo)
                                .containsExactly("nombreCompleto", "frecuenciaCardiaca", "spo2");
                        assertThat(error.getMessage())
                                .contains("nombreCompleto", "frecuenciaCardiaca", "spo2");
                        assertThat(error.getErrores().get(1).getMensaje())
                                .isEqualTo("El campo 'frecuenciaCardiaca' debe estar entre 20 y 250");
                    });
        }

        @Test
        @DisplayName("un campo ausente indica que es obligatorio")
        void shouldReportRequiredMessage_WhenFieldIsMissing() {
            var request = PacienteFixtures.base().spo2(null).build();

            assertThatThrownBy(() -> validator.validar(request))
                    .isInstanceOfSatisfying(ValidationException.class, error ->
                            assertThat(error.getErrores()).singleElement().satisfies(detalle -> {
                                assertThat(detalle.getCampo()).isEqualTo("spo2");
                                assertThat(detalle.getMensaje()).isEqualTo("El campo 'spo2' es obligatorio");
                            }));
        }
    }
}
