package co.com.vidacruz.asistente.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.config.RagProperties;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.impl.CorpusIndexer;
import co.com.vidacruz.asistente.service.impl.RagRetrieverImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CompuertaRelevancia con el modelo de embeddings real")
class CompuertaRelevanciaTest {

    private static final RagProperties PROPIEDADES = new RagProperties(3, 0.65, 0.80);

    private static CorpusLoader corpusLoader;
    private static RagRetrieverImpl retriever;
    private static CompuertaRelevancia compuerta;

    @BeforeAll
    static void construir() {
        corpusLoader = new CorpusLoader(new ObjectMapper());
        EmbeddingModel modelo = new AllMiniLmL6V2QuantizedEmbeddingModel();
        EmbeddingStore<TextSegment> indice = new CorpusIndexer(corpusLoader, modelo).construirIndice();
        retriever = new RagRetrieverImpl(modelo, indice, PROPIEDADES);
        compuerta = new CompuertaRelevancia(corpusLoader, PROPIEDADES);
    }

    private static boolean pasa(String pregunta) {
        List<SegmentoRecuperado> segmentos = retriever.recuperar(pregunta).block();
        return compuerta.pasa(pregunta, segmentos);
    }

    @Nested
    @DisplayName("Preguntas dentro del corpus")
    class DentroDelCorpus {

        @ParameterizedTest
        @ValueSource(strings = {
                "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?",
                "¿Cuándo se administra el primer antibiótico en sepsis?",
                "¿qué hago con un niño deshidratado severo?",
                "criterios qSOFA",
                "¿se puede dar nifedipino sublingual?"})
        void shouldPass_WhenQuestionIsAboutTheCorpus(String pregunta) {
            assertThat(pasa(pregunta)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "¿cómo manejo la hipertensión en el embarazo?",
                "tratamiento de la hipertensión arterial crónica",
                "¿qué antibiótico se usa para una infección urinaria?"})
        void shouldPass_WhenQuestionSharesATermButIsNotCovered(String pregunta) {
            // La segunda barrera (sinProtocolo del LLM y RespuestaValidator) es la que las frena.
            assertThat(pasa(pregunta)).isTrue();
        }
    }

    @Nested
    @DisplayName("Preguntas fuera del corpus")
    class FueraDelCorpus {

        @ParameterizedTest
        @ValueSource(strings = {
                "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?",
                "¿dosis de insulina?",
                "¿cómo tratar una fractura de fémur?",
                "¿cuál es la capital de Francia?",
                "receta de arroz con pollo",
                "¿cuál es el tratamiento de la neumonía?"})
        void shouldNotPass_WhenQuestionIsNotInTheCorpus(String pregunta) {
            assertThat(pasa(pregunta)).isFalse();
        }
    }

    @Nested
    @DisplayName("Términos discriminantes")
    class Terminos {

        @Test
        void shouldDeriveTermsOfASingleProtocol_WhenCorpusIsLoaded() {
            assertThat(compuerta.terminosDiscriminantes())
                    .contains("hipert", "sepsis", "qsofa", "deshid", "nifedi");
        }

        @Test
        void shouldExcludeTermsSharedByTwoProtocols_WhenDerivingFromCorpus() {
            assertThat(compuerta.terminosDiscriminantes()).doesNotContain("primer", "inicia");
        }
    }

    @Nested
    @DisplayName("Score alto")
    class ScoreAlto {

        @Test
        void shouldPass_WhenBestScoreReachesHighThresholdWithoutSharedTerms() {
            SegmentoRecuperado fuerte = new SegmentoRecuperado("PROTO-001", "Definición", "texto", 0.80);

            assertThat(compuerta.pasa("pregunta sin vocabulario del corpus", List.of(fuerte))).isTrue();
        }

        @Test
        void shouldNotPass_WhenNothingWasRetrieved() {
            assertThat(compuerta.pasa("criterios qSOFA", List.of())).isFalse();
        }
    }
}
