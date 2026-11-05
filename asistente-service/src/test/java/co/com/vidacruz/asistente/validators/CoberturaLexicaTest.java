package co.com.vidacruz.asistente.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.service.CorpusLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CoberturaLexica")
class CoberturaLexicaTest {

    private static final List<FuenteCitada> MEDIDAS = List.of(new FuenteCitada("PROTO-001", "Medidas iniciales"));

    private final CoberturaLexica cobertura = new CoberturaLexica(new CorpusLoader(new ObjectMapper()));

    @Test
    void shouldBeFull_WhenEveryContentWordIsInTheCitedSection() {
        assertThat(cobertura.calcular("Reconfirmar la presión tras reposo y vigilar encefalopatía.", "x", MEDIDAS))
                .isEqualTo(1.0);
    }

    @Test
    void shouldCountUncoveredRoots_WhenAnswerAddsWordsOutsideTheSources() {
        // evaluadas: reconf, presio, reposo, amoxic (4); cubiertas: 3
        assertThat(cobertura.calcular("Reconfirmar presión reposo amoxicilina", "x", MEDIDAS))
                .isCloseTo(0.75, within(0.0001));
    }

    @Test
    void shouldIgnoreQuestionWords_WhenTheyAppearInTheAnswer() {
        assertThat(cobertura.calcular("Reconfirmar presión reposo amoxicilina", "¿amoxicilina?", MEDIDAS))
                .isEqualTo(1.0);
    }

    @Test
    void shouldUseTitleAndSectionNameAsReference_WhenAnswerMentionsThem() {
        assertThat(cobertura.calcular("manejo inicial de la crisis hipertensiva", "x", MEDIDAS)).isEqualTo(1.0);
    }

    @Test
    void shouldNotCountProtocolIdsNorFigures_WhenEvaluatingWords() {
        assertThat(cobertura.calcular("PROTO-001: 5 minutos de reposo", "x", MEDIDAS)).isEqualTo(1.0);
    }

    @Test
    void shouldBeFull_WhenAnswerHasNoContentWords() {
        assertThat(cobertura.calcular("5 25%", "x", MEDIDAS)).isEqualTo(1.0);
    }

    @Test
    void shouldCombineSourcesOfEveryCitation_WhenSeveralSectionsAreCited() {
        List<FuenteCitada> dos = List.of(new FuenteCitada("PROTO-001", "Medidas iniciales"),
                new FuenteCitada("PROTO-001", "Contraindicaciones"));

        assertThat(cobertura.calcular("reposo nifedipino", "x", dos)).isEqualTo(1.0);
        assertThat(cobertura.calcular("reposo nifedipino", "x", MEDIDAS)).isEqualTo(0.5);
    }

    @Test
    void shouldBeFull_WhenAnswerOnlyAddsMetaDiscourseWords() {
        List<FuenteCitada> contraindicaciones = List.of(new FuenteCitada("PROTO-001", "Contraindicaciones"));
        String respuesta = "No. Según el contexto, en Crisis Hipertensiva, manejo inicial, el Nifedipino sublingual "
                + "figura en Contraindicaciones.";

        assertThat(cobertura.calcular(respuesta, "¿Se puede usar nifedipino sublingual en una crisis hipertensiva?",
                contraindicaciones)).isEqualTo(1.0);
    }

    @Test
    void shouldStillPenalizeInventedDrug_WhenAnswerUsesMetaDiscourse() {
        double resultado = cobertura.calcular("Según el contexto, la amoxicilina figura en reposo", "x", MEDIDAS);

        assertThat(resultado).isLessThan(0.85);
    }
}
