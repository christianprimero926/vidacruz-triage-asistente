package co.com.vidacruz.asistente.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.config.RagProperties;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.CorpusLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@DisplayName("RagRetrieverImpl con el modelo de embeddings real")
class RagRetrieverImplTest {

    private static final int TOTAL_SEGMENTOS = 7;

    private static EmbeddingModel embeddingModel;
    private static EmbeddingStore<TextSegment> embeddingStore;
    private static final Scheduler scheduler = Schedulers.newBoundedElastic(2, 20, "rag-test");

    @AfterAll
    static void liberar() {
        scheduler.dispose();
    }

    @BeforeAll
    static void construirIndice() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore = new CorpusIndexer(new CorpusLoader(new ObjectMapper()), embeddingModel)
                .construirIndice();
    }

    private static RagRetrieverImpl retriever(int topK, double scoreMinimo) {
        return new RagRetrieverImpl(embeddingModel, embeddingStore,
                new RagProperties(topK, scoreMinimo, 0.80, 2, 20, 5), scheduler);
    }

    @Nested
    @DisplayName("Segmento más relevante")
    class SegmentoMasRelevante {

        @Test
        void shouldReturnMedidasIniciales_WhenAskingAboutHypertensiveCrisis() {
            assertPrimerSegmento("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?",
                    "PROTO-001", "Medidas iniciales");
        }

        @Test
        void shouldReturnAccionesIniciales_WhenAskingAboutFirstAntibioticInSepsis() {
            assertPrimerSegmento("¿Cuándo se administra el primer antibiótico en sepsis?",
                    "PROTO-002", "Acciones iniciales");
        }

        @Test
        void shouldReturnCriterios_WhenAskingForQsofa() {
            assertPrimerSegmento("criterios qSOFA", "PROTO-002", "Criterios");
        }

        @Test
        void shouldReturnContraindicaciones_WhenAskingAboutSublingualNifedipine() {
            assertPrimerSegmento("¿se puede dar nifedipino sublingual?", "PROTO-001", "Contraindicaciones");
        }

        @Test
        void shouldReturnProtocoloDeshidratacion_WhenAskingAboutSevereDehydratedChild() {
            List<SegmentoRecuperado> segmentos = recuperar(retriever(3, 0.0),
                    "¿qué hago con un niño deshidratado severo?");

            assertThat(segmentos.get(0).protocolo()).isEqualTo("PROTO-003");
        }

        private void assertPrimerSegmento(String pregunta, String protocolo, String seccion) {
            SegmentoRecuperado primero = recuperar(retriever(3, 0.0), pregunta).get(0);

            assertThat(primero.protocolo()).isEqualTo(protocolo);
            assertThat(primero.seccion()).isEqualTo(seccion);
            assertThat(primero.texto()).contains(seccion);
        }
    }

    @Nested
    @DisplayName("Límites de la recuperación")
    class Limites {

        @Test
        void shouldReturnAtMostTopKOrderedByScore_WhenTopKIsThree() {
            List<SegmentoRecuperado> segmentos = recuperar(retriever(3, 0.0),
                    "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?");

            assertThat(segmentos).hasSize(3);
            assertThat(segmentos).extracting(SegmentoRecuperado::score).isSortedAccordingTo(
                    (a, b) -> Double.compare(b, a));
        }

        @Test
        void shouldSearchEverySegment_WhenTopKIsLargerThanCorpus() {
            assertThat(recuperar(retriever(50, 0.0), "criterios qSOFA")).hasSize(TOTAL_SEGMENTOS);
        }

        @Test
        void shouldReturnEmpty_WhenNoSegmentReachesMinimumScore() {
            assertThat(recuperar(retriever(3, 0.99), "criterios qSOFA")).isEmpty();
        }

        @Test
        void shouldDiscardSegmentsBelowMinimumScore_WhenMinimumScoreIsSet() {
            List<SegmentoRecuperado> segmentos = recuperar(retriever(50, 0.8),
                    "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?");

            assertThat(segmentos).isNotEmpty().allMatch(s -> s.score() >= 0.8);
            assertThat(segmentos).hasSizeLessThan(TOTAL_SEGMENTOS);
        }
    }

    private static List<SegmentoRecuperado> recuperar(RagRetrieverImpl retriever, String pregunta) {
        return retriever.recuperar(pregunta).block();
    }

    @Nested
    @DisplayName("Ejecución reactiva")
    class Reactivo {

        @Test
        void shouldNotRunEmbeddingOnSubscriberThread_WhenRetrieving() {
            String hiloLlamador = Thread.currentThread().getName();

            StepVerifier.create(retriever(1, 0.0).recuperar("criterios qSOFA")
                            .map(segmentos -> Thread.currentThread().getName()))
                    .assertNext(hilo -> assertThat(hilo).isNotEqualTo(hiloLlamador).startsWith("rag-test"))
                    .verifyComplete();
        }
    }
}
