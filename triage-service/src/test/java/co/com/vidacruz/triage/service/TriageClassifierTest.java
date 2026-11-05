package co.com.vidacruz.triage.service;

import static co.com.vidacruz.triage.domain.NivelTriage.CRITICO;
import static co.com.vidacruz.triage.domain.NivelTriage.NO_URGENTE;
import static co.com.vidacruz.triage.domain.NivelTriage.NO_URGENTE_2;
import static co.com.vidacruz.triage.domain.NivelTriage.URGENTE;
import static co.com.vidacruz.triage.domain.NivelTriage.URGENTE_2;
import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.support.PacienteFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TriageClassifier (RN-01)")
class TriageClassifierTest {

    private final TriageClassifier classifier = new TriageClassifier();

    private NivelTriage clasificar(PacienteRequestDTO paciente) {
        return classifier.clasificar(paciente);
    }

    @Nested
    @DisplayName("Pacientes de ejemplo del enunciado")
    class Ejemplos {

        @Test
        @DisplayName("E1 con frecuencia cardíaca 130 es CRITICO")
        void shouldClassifyCritico_WhenE1() {
            assertThat(clasificar(PacienteFixtures.e1())).isEqualTo(CRITICO);
        }

        @Test
        @DisplayName("E2 con SpO2 91 es URGENTE")
        void shouldClassifyUrgente_WhenE2() {
            assertThat(clasificar(PacienteFixtures.e2())).isEqualTo(URGENTE);
        }

        @Test
        @DisplayName("E3 con temperatura 38.2 es URGENTE_2")
        void shouldClassifyUrgente2_WhenE3() {
            assertThat(clasificar(PacienteFixtures.e3())).isEqualTo(URGENTE_2);
        }

        @Test
        @DisplayName("E4 sin alteraciones es NO_URGENTE")
        void shouldClassifyNoUrgente_WhenE4() {
            assertThat(clasificar(PacienteFixtures.e4())).isEqualTo(NO_URGENTE);
        }

        @Test
        @DisplayName("E5 con queja administrativa es NO_URGENTE_2")
        void shouldClassifyNoUrgente2_WhenE5() {
            assertThat(clasificar(PacienteFixtures.e5())).isEqualTo(NO_URGENTE_2);
        }

        @Test
        @DisplayName("E6 con GCS 6 es CRITICO")
        void shouldClassifyCritico_WhenE6() {
            assertThat(clasificar(PacienteFixtures.e6())).isEqualTo(CRITICO);
        }
    }

    @Nested
    @DisplayName("Fronteras de cada regla")
    class Fronteras {

        @ParameterizedTest(name = "FC {0} -> {1}")
        @CsvSource({"39,CRITICO", "40,NO_URGENTE", "89,NO_URGENTE", "90,URGENTE_2", "120,URGENTE_2", "121,CRITICO"})
        void shouldClassifyByFrecuenciaCardiaca_WhenValueIsOnBoundary(int frecuencia, NivelTriage esperado) {
            var paciente = PacienteFixtures.base().frecuenciaCardiaca(frecuencia).build();
            assertThat(clasificar(paciente)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "SpO2 {0} -> {1}")
        @CsvSource({"89,CRITICO", "90,URGENTE", "93,URGENTE", "94,NO_URGENTE"})
        void shouldClassifyBySpo2_WhenValueIsOnBoundary(int spo2, NivelTriage esperado) {
            var paciente = PacienteFixtures.base().spo2(spo2).build();
            assertThat(clasificar(paciente)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "PAS {0} -> {1}")
        @CsvSource({"89,CRITICO", "90,URGENTE", "100,URGENTE", "101,NO_URGENTE"})
        void shouldClassifyByPresionSistolica_WhenValueIsOnBoundary(int sistolica, NivelTriage esperado) {
            var paciente = PacienteFixtures.base().presionSistolica(sistolica).build();
            assertThat(clasificar(paciente)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "Temperatura {0} -> {1}")
        @CsvSource({"37.99,NO_URGENTE", "38.0,URGENTE_2", "38.49,URGENTE_2", "38.5,URGENTE", "41.9,URGENTE"})
        void shouldClassifyByTemperatura_WhenValueIsOnBoundary(String temperatura, NivelTriage esperado) {
            var paciente = PacienteFixtures.base().temperatura(new BigDecimal(temperatura)).build();
            assertThat(clasificar(paciente)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "GCS {0} -> {1}")
        @CsvSource({"8,CRITICO", "9,URGENTE", "13,URGENTE", "14,NO_URGENTE", "15,NO_URGENTE"})
        void shouldClassifyByGcs_WhenValueIsOnBoundary(int gcs, NivelTriage esperado) {
            var paciente = PacienteFixtures.base().gcs(gcs).build();
            assertThat(clasificar(paciente)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "Dolor {0} -> {1}")
        @CsvSource({"6,NO_URGENTE", "7,URGENTE_2", "10,URGENTE_2"})
        void shouldClassifyByDolor_WhenValueIsOnBoundary(int dolor, NivelTriage esperado) {
            var paciente = PacienteFixtures.base().dolor(dolor).build();
            assertThat(clasificar(paciente)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("Varias condiciones a la vez")
    class Prioridad {

        @Test
        @DisplayName("rige el nivel de mayor gravedad cuando se cumplen condiciones de distintos niveles")
        void shouldClassifyHighestSeverity_WhenSeveralLevelsApply() {
            var paciente = PacienteFixtures.base().frecuenciaCardiaca(95).spo2(91).dolor(9).build();
            assertThat(clasificar(paciente)).isEqualTo(URGENTE);
        }

        @Test
        @DisplayName("una condición crítica prevalece sobre las urgentes")
        void shouldClassifyCritico_WhenCriticalAndUrgentApply() {
            var paciente = PacienteFixtures.base().gcs(8).temperatura(new BigDecimal("39.0")).build();
            assertThat(clasificar(paciente)).isEqualTo(CRITICO);
        }

        @Test
        @DisplayName("una queja administrativa no baja el nivel de un paciente con signos alterados")
        void shouldNotLowerLevel_WhenAdministrativeComplaintHasAlteredSigns() {
            var paciente = PacienteFixtures.base().frecuenciaCardiaca(130).quejaPrincipal("Vacunación").build();
            assertThat(clasificar(paciente)).isEqualTo(CRITICO);
        }
    }

    @Nested
    @DisplayName("Queja administrativa")
    class QuejaAdministrativa {

        @ParameterizedTest(name = "\"{0}\" es administrativa")
        @ValueSource(strings = {
                "documentos", "Documentos", "Recolección de resultados", "recoleccion de resultados",
                "Renovación de recetario", "  RENOVACION   DE   RECETARIO  ", "vacunación", "Seguimiento"})
        void shouldClassifyNoUrgente2_WhenComplaintMatchesNormalizedList(String queja) {
            var paciente = PacienteFixtures.base().quejaPrincipal(queja).build();
            assertThat(clasificar(paciente)).isEqualTo(NO_URGENTE_2);
        }

        @ParameterizedTest(name = "\"{0}\" no es administrativa")
        @ValueSource(strings = {"seguimiento de infarto", "vacunacion infantil con fiebre", "dolor", "renovacion", "documentos y"})
        void shouldClassifyNoUrgente_WhenComplaintOnlyContainsAdministrativeWord(String queja) {
            var paciente = PacienteFixtures.base().quejaPrincipal(queja).build();
            assertThat(clasificar(paciente)).isEqualTo(NO_URGENTE);
        }
    }
}
