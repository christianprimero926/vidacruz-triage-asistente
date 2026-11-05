package co.com.vidacruz.asistente.validators;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.CorpusLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RespuestaValidator (RN-05)")
class RespuestaValidatorTest {

    private static final FuenteCitada MEDIDAS = new FuenteCitada("PROTO-001", "Medidas iniciales");
    private static final List<SegmentoRecuperado> RECUPERADOS = List.of(
            new SegmentoRecuperado("PROTO-001", "Medidas iniciales", "texto", 0.84),
            new SegmentoRecuperado("PROTO-001", "Contraindicaciones", "texto", 0.80));

    private final RespuestaValidator validator = new RespuestaValidator(new CorpusLoader(new ObjectMapper()));

    private static RespuestaLlm respuesta(String texto, Boolean sinProtocolo, FuenteCitada... fuentes) {
        return new RespuestaLlm(texto, List.of(fuentes), sinProtocolo);
    }

    @Test
    void shouldBeValid_WhenFiguresAndSourceComeFromCitedSection() {
        RespuestaLlm ok = respuesta("Reconfirmar la presión tras 5 minutos. No reducir más de un 25% en la primera hora.",
                false, MEDIDAS);

        assertThat(validator.esValida(ok, RECUPERADOS)).isTrue();
    }

    @Test
    void shouldBeValid_WhenAnswerMentionsProtocolIdAsText() {
        RespuestaLlm ok = respuesta("Según PROTO-001, reconfirmar la presión tras 5 minutos.", false, MEDIDAS);

        assertThat(validator.esValida(ok, RECUPERADOS)).isTrue();
    }

    @Test
    void shouldBeInvalid_WhenModelFlagsSinProtocolo() {
        assertThat(validator.esValida(respuesta("Algo", true, MEDIDAS), RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenThereAreNoSources() {
        assertThat(validator.esValida(respuesta("Reconfirmar la presión.", false), RECUPERADOS)).isFalse();
        assertThat(validator.esValida(new RespuestaLlm("Reconfirmar.", null, false), RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenProtocolIsNotInCorpus() {
        FuenteCitada inventada = new FuenteCitada("PROTO-999", "Medidas iniciales");

        assertThat(validator.esValida(respuesta("Reconfirmar.", false, inventada), RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenSectionIsNotInCorpus() {
        FuenteCitada inventada = new FuenteCitada("PROTO-001", "Dosis");

        assertThat(validator.esValida(respuesta("Reconfirmar.", false, inventada), RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenSourceExistsButWasNotRetrieved() {
        FuenteCitada noRecuperada = new FuenteCitada("PROTO-002", "Criterios");

        assertThat(validator.esValida(respuesta("Criterios de sepsis.", false, noRecuperada), RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenAnyFigureIsNotInCitedSections() {
        RespuestaLlm inventada = respuesta("Reconfirmar la presión tras 10 minutos de reposo.", false, MEDIDAS);

        assertThat(validator.esValida(inventada, RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenFigureBelongsToASectionThatWasNotCited() {
        RespuestaLlm ajena = respuesta("Presión arterial ≥ 180/120 mmHg.", false, MEDIDAS);

        assertThat(validator.esValida(ajena, RECUPERADOS)).isFalse();
    }

    @Test
    void shouldBeInvalid_WhenAnswerIsBlank() {
        assertThat(validator.esValida(respuesta("  ", false, MEDIDAS), RECUPERADOS)).isFalse();
    }
}
