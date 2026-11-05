package co.com.vidacruz.triage.domain;

import static co.com.vidacruz.triage.domain.EstadoPaciente.ALTA;
import static co.com.vidacruz.triage.domain.EstadoPaciente.EN_CONSULTA;
import static co.com.vidacruz.triage.domain.EstadoPaciente.REGISTRADO;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("EstadoPaciente (RF-02)")
class EstadoPacienteTest {

    @Nested
    @DisplayName("Transiciones válidas")
    class Validas {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"REGISTRADO,EN_CONSULTA", "REGISTRADO,ALTA", "EN_CONSULTA,ALTA"})
        void shouldAllowTransition_WhenItIsInTheFlow(EstadoPaciente origen, EstadoPaciente destino) {
            assertThat(origen.puedePasarA(destino)).isTrue();
        }
    }

    @Nested
    @DisplayName("Transiciones inválidas")
    class Invalidas {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "REGISTRADO,REGISTRADO", "EN_CONSULTA,REGISTRADO", "EN_CONSULTA,EN_CONSULTA",
                "ALTA,REGISTRADO", "ALTA,EN_CONSULTA", "ALTA,ALTA"})
        void shouldRejectTransition_WhenItIsNotInTheFlow(EstadoPaciente origen, EstadoPaciente destino) {
            assertThat(origen.puedePasarA(destino)).isFalse();
        }
    }

    @Nested
    @DisplayName("Transiciones disponibles")
    class Disponibles {

        @Test
        void shouldListNextStates_WhenAskedForEachState() {
            assertThat(REGISTRADO.transicionesValidas()).containsExactlyInAnyOrder(EN_CONSULTA, ALTA);
            assertThat(EN_CONSULTA.transicionesValidas()).containsExactly(ALTA);
            assertThat(ALTA.transicionesValidas()).isEmpty();
        }
    }
}
