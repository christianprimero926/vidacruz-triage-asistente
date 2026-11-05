package co.com.vidacruz.triage.config;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.domain.NivelTriage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TriageProperties (RN-02)")
class TriagePropertiesTest {

    @Test
    @DisplayName("el SLA por defecto de cada nivel es el del enunciado")
    void shouldReturnStatementSla_WhenDefaultsAreUsed() {
        var sla = new TriageProperties().getSla();

        assertThat(sla.minutosPara(NivelTriage.CRITICO)).isEqualTo(5);
        assertThat(sla.minutosPara(NivelTriage.URGENTE)).isEqualTo(10);
        assertThat(sla.minutosPara(NivelTriage.URGENTE_2)).isEqualTo(30);
        assertThat(sla.minutosPara(NivelTriage.NO_URGENTE)).isEqualTo(60);
        assertThat(sla.minutosPara(NivelTriage.NO_URGENTE_2)).isEqualTo(120);
    }
}
