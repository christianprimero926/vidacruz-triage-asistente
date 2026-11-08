package co.com.vidacruz.asistente.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CitaCanonica")
class CitaCanonicaTest {

    private static final List<SegmentoRecuperado> RECUPERADOS = List.of(
            new SegmentoRecuperado("PROTO-001", "Medidas iniciales", "texto", 0.84),
            new SegmentoRecuperado("PROTO-002", "Acciones iniciales", "texto", 0.80));

    private static RespuestaLlm citando(FuenteCitada... fuentes) {
        return new RespuestaLlm("Reconfirmar la presión.", List.of(fuentes), false);
    }

    @Test
    void shouldKeepSection_WhenCitedExactly() {
        RespuestaLlm resultado = CitaCanonica.aplicar(
                citando(new FuenteCitada("PROTO-001", "Medidas iniciales")), RECUPERADOS);

        assertThat(resultado.fuentes()).containsExactly(new FuenteCitada("PROTO-001", "Medidas iniciales"));
    }

    @Test
    void shouldReduceToRetrievedSection_WhenCitedWithProtocolTitle() {
        RespuestaLlm resultado = CitaCanonica.aplicar(citando(
                new FuenteCitada("PROTO-001", "Crisis Hipertensiva, manejo inicial — Medidas iniciales ")), RECUPERADOS);

        assertThat(resultado.fuentes()).containsExactly(new FuenteCitada("PROTO-001", "Medidas iniciales"));
        assertThat(resultado.respuesta()).isEqualTo("Reconfirmar la presión.");
        assertThat(resultado.sinProtocolo()).isFalse();
    }

    @Test
    void shouldKeepCitation_WhenSectionBelongsToAnotherProtocol() {
        FuenteCitada cruzada = new FuenteCitada("PROTO-001", "Sepsis — Acciones iniciales");

        assertThat(CitaCanonica.aplicar(citando(cruzada), RECUPERADOS).fuentes()).containsExactly(cruzada);
    }

    @Test
    void shouldKeepCitation_WhenSectionIsOnlyPartiallyEqual() {
        FuenteCitada parecida = new FuenteCitada("PROTO-001", "Otras medidas iniciales");

        assertThat(CitaCanonica.aplicar(citando(parecida), RECUPERADOS).fuentes()).containsExactly(parecida);
    }

    @Test
    void shouldReturnSameAnswer_WhenThereAreNoSources() {
        RespuestaLlm sinFuentes = new RespuestaLlm("x", null, true);

        assertThat(CitaCanonica.aplicar(sinFuentes, RECUPERADOS)).isSameAs(sinFuentes);
        assertThat(CitaCanonica.aplicar(null, RECUPERADOS)).isNull();
    }

    @Test
    void shouldKeepIncompleteCitation_WhenFieldsAreMissing() {
        FuenteCitada incompleta = new FuenteCitada("PROTO-001", null);

        assertThat(CitaCanonica.aplicar(citando(incompleta), RECUPERADOS).fuentes()).containsExactly(incompleta);
    }
}
