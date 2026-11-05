package co.com.vidacruz.asistente.validators;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.config.ValidacionProperties;
import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.CorpusLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("RespuestaValidator (RN-05)")
class RespuestaValidatorTest {

    private static final String PREGUNTA = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";
    private static final FuenteCitada MEDIDAS = new FuenteCitada("PROTO-001", "Medidas iniciales");
    private static final List<SegmentoRecuperado> RECUPERADOS = List.of(
            new SegmentoRecuperado("PROTO-001", "Medidas iniciales", "texto", 0.84),
            new SegmentoRecuperado("PROTO-001", "Contraindicaciones", "texto", 0.80));

    private final CorpusLoader corpus = new CorpusLoader(new ObjectMapper());
    private final RespuestaValidator validator = new RespuestaValidator(corpus, new CoberturaLexica(corpus),
            new ValidacionProperties(0.85));

    private static RespuestaLlm respuesta(String texto, Boolean sinProtocolo, FuenteCitada... fuentes) {
        return new RespuestaLlm(texto, List.of(fuentes), sinProtocolo);
    }

    private boolean valida(RespuestaLlm respuesta) {
        return validator.esValida(PREGUNTA, respuesta, RECUPERADOS);
    }

    private boolean valida(String pregunta, String protocolo, String seccion, String texto) {
        List<SegmentoRecuperado> recuperados = List.of(new SegmentoRecuperado(protocolo, seccion, "texto", 0.9));
        return validator.esValida(pregunta, respuesta(texto, false, new FuenteCitada(protocolo, seccion)),
                recuperados);
    }

    @Nested
    @DisplayName("Fuentes y cifras")
    class FuentesYCifras {

        @Test
        void shouldBeValid_WhenFiguresAndSourceComeFromCitedSection() {
            assertThat(valida(respuesta(
                    "Reconfirmar la presión tras 5 minutos. No reducir más de un 25% en la primera hora.",
                    false, MEDIDAS))).isTrue();
        }

        @Test
        void shouldBeInvalid_WhenModelFlagsSinProtocolo() {
            assertThat(valida(respuesta("Algo", true, MEDIDAS))).isFalse();
        }

        @Test
        void shouldBeInvalid_WhenThereAreNoSources() {
            assertThat(valida(respuesta("Reconfirmar la presión.", false))).isFalse();
            assertThat(valida(new RespuestaLlm("Reconfirmar.", null, false))).isFalse();
        }

        @Test
        void shouldBeInvalid_WhenProtocolIsNotInCorpus() {
            assertThat(valida(respuesta("Reconfirmar.", false, new FuenteCitada("PROTO-999", "Medidas iniciales"))))
                    .isFalse();
        }

        @Test
        void shouldBeInvalid_WhenSectionIsNotInCorpus() {
            assertThat(valida(respuesta("Reconfirmar.", false, new FuenteCitada("PROTO-001", "Dosis")))).isFalse();
        }

        @Test
        void shouldBeInvalid_WhenSourceExistsButWasNotRetrieved() {
            assertThat(valida(respuesta("Criterios de sepsis.", false, new FuenteCitada("PROTO-002", "Criterios"))))
                    .isFalse();
        }

        @Test
        void shouldBeInvalid_WhenAnyFigureIsNotInCitedSections() {
            assertThat(valida(respuesta("Reconfirmar la presión tras 10 minutos de reposo.", false, MEDIDAS)))
                    .isFalse();
        }

        @Test
        void shouldBeInvalid_WhenFigureBelongsToASectionThatWasNotCited() {
            assertThat(valida(respuesta("Presión arterial ≥ 180/120 mmHg.", false, MEDIDAS))).isFalse();
        }

        @Test
        void shouldBeInvalid_WhenAnswerIsBlank() {
            assertThat(valida(respuesta("  ", false, MEDIDAS))).isFalse();
        }
    }

    @Nested
    @DisplayName("Menciones de protocolos en el texto")
    class MencionesDeProtocolos {

        @Test
        void shouldBeValid_WhenMentionedProtocolIsTheCitedOne() {
            assertThat(valida(respuesta("Según PROTO-001, reconfirmar la presión tras 5 minutos.", false, MEDIDAS)))
                    .isTrue();
        }

        @Test
        void shouldBeInvalid_WhenTextMentionsAnotherExistingProtocolThatIsNotCited() {
            assertThat(valida(respuesta("Según PROTO-002, reconfirmar la presión tras 5 minutos.", false, MEDIDAS)))
                    .isFalse();
        }

        @Test
        void shouldBeInvalid_WhenTextMentionsProtocolThatDoesNotExist() {
            assertThat(valida(respuesta("Según PROTO-999, reconfirmar la presión tras 5 minutos.", false, MEDIDAS)))
                    .isFalse();
        }

        @Test
        void shouldBeInvalid_WhenOneOfSeveralMentionsIsNotCited() {
            assertThat(valida(respuesta("PROTO-001 indica reconfirmar la presión; ver también PROTO-003.", false,
                    MEDIDAS))).isFalse();
        }
    }

    @Nested
    @DisplayName("Cobertura léxica (0.85)")
    class CoberturaDeVocabulario {

        @ParameterizedTest(name = "[{index}] {1}")
        @CsvSource(delimiter = '|', textBlock = """
                PROTO-001|Definición|¿Cómo se define la crisis hipertensiva?|Se considera crisis hipertensiva cuando la presión arterial es de 180/120 mmHg o más. Si existe daño de órgano diana se trata de una emergencia; si no hay daño, es una urgencia.
                PROTO-001|Medidas iniciales|¿Cuáles son las medidas iniciales ante una crisis hipertensiva?|Primero se debe reconfirmar la presión después de 5 minutos de reposo. Además, no hay que reducir la presión más de un 25% durante la primera hora, y se deben vigilar los signos de encefalopatía.
                PROTO-001|Medidas iniciales|¿Cuáles son las medidas iniciales ante una crisis hipertensiva?|Las medidas iniciales son: reconfirmar la presión tras 5 minutos de reposo, evitar bajarla más de un 25% en la primera hora y vigilar signos de encefalopatía.
                PROTO-001|Contraindicaciones|¿Qué está contraindicado en la crisis hipertensiva?|Están contraindicados el nifedipino sublingual y la reducción intravenosa rápida de la presión sin monitorización en cuidados intensivos.
                PROTO-002|Criterios|¿Cuáles son los criterios de sepsis qSOFA?|El qSOFA es positivo con 2 o más criterios: frecuencia respiratoria ≥ 22, alteración del estado mental y presión sistólica ≤ 100 mmHg.
                PROTO-002|Acciones iniciales|¿Cuándo se administra el primer antibiótico en sepsis?|El antibiótico de amplio espectro se administra en la primera hora, después de tomar lactato y hemocultivos. Si hay hipotensión o lactato ≥ 4, se inicia fluidoterapia con 30 ml/kg de cristaloides.
                PROTO-002|Acciones iniciales|¿Cuándo se administra el primer antibiótico en sepsis?|Se debe tomar lactato y hemocultivos antes de administrar el antibiótico, que debe ser de amplio espectro y aplicarse en la primera hora.
                PROTO-003|Clasificación|¿Cómo se clasifica la deshidratación pediátrica?|La deshidratación se clasifica como leve con pérdida de 3-5% del peso, moderada con 5-8% y severa con más de 8% o alteración del estado mental.
                PROTO-003|Rehidratación|¿Cómo se rehidrata a un niño deshidratado?|En deshidratación leve o moderada sin vómito se usa solución oral, 50 ml/kg en 4 horas. En la severa se usa la vía intravenosa con un bolo de 20 ml/kg.
                """)
        void shouldAcceptFaithfulParaphrase_WhenVocabularyComesFromCitedSection(String protocolo, String seccion,
                                                                               String pregunta, String texto) {
            assertThat(valida(pregunta, protocolo, seccion, texto)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @CsvSource(delimiter = '|', textBlock = """
                PROTO-002|Acciones iniciales|¿Cuándo se administra el primer antibiótico en sepsis?|Administre amoxicilina por vía oral en la primera hora tras tomar lactato y hemocultivos.
                PROTO-002|Acciones iniciales|¿Cuándo se administra el primer antibiótico en sepsis?|Se administra el antibiótico de amplio espectro en la primera hora, preferiblemente ceftriaxona intramuscular, después de tomar lactato y hemocultivos.
                PROTO-001|Medidas iniciales|¿Cuáles son las medidas iniciales ante una crisis hipertensiva?|Reconfirmar la presión tras reposo y administrar captopril sublingual si persiste elevada.
                PROTO-001|Medidas iniciales|¿Cuáles son las medidas iniciales ante una crisis hipertensiva?|Reconfirmar la presión tras reposo, no reducir más de un veinticinco por ciento en la primera hora y administrar labetalol.
                PROTO-001|Medidas iniciales|¿Cuáles son las medidas iniciales ante una crisis hipertensiva?|Reconfirmar la presión tras 5 minutos de reposo, no reducir más de un 25% en la primera hora y vigilar signos de encefalopatía; administrar además hidralazina intravenosa.
                PROTO-003|Rehidratación|¿Cómo se rehidrata a un niño deshidratado?|En deshidratación leve se usa solución oral; en la severa se administra suero fisiológico con furosemida por vía intravenosa.
                PROTO-003|Rehidratación|¿Cómo se rehidrata a un niño deshidratado?|Leve o moderada sin vómito: solución oral cincuenta mililitros por kilo en cuatro horas.
                PROTO-002|Criterios|¿Cuáles son los criterios de sepsis qSOFA?|Se requieren dos o más criterios: frecuencia respiratoria alta, alteración mental o hipotensión, y se debe indicar oxígeno suplementario con mascarilla.
                PROTO-001|Contraindicaciones|¿Qué está contraindicado en la crisis hipertensiva?|Está contraindicado el nifedipino sublingual; en cambio se recomienda administrar enalapril oral inmediatamente.
                PROTO-002|Acciones iniciales|¿Cuándo se administra el primer antibiótico en sepsis?|Administre ibuprofeno oral para la fiebre y tome lactato antes del antibiótico.
                """)
        void shouldRejectForeignClinicalText_WhenDrugRouteOrSpelledFigureIsInvented(String protocolo, String seccion,
                                                                                    String pregunta, String texto) {
            assertThat(valida(pregunta, protocolo, seccion, texto)).isFalse();
        }

        @Test
        void shouldNotLetQuestionWordsHideInventedContent_WhenAnswerRepeatsThem() {
            String texto = "Reconfirmar la presión tras 5 minutos de reposo y administrar amoxicilina por vía oral.";

            assertThat(validator.esValida(PREGUNTA, respuesta(texto, false, MEDIDAS), RECUPERADOS)).isFalse();
        }
    }
}
