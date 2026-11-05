package co.com.vidacruz.asistente.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.config.RagProperties;
import co.com.vidacruz.asistente.exception.BusinessException;
import co.com.vidacruz.asistente.exception.ErrorType;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagRetrieverImpl: scheduler acotado, timeout y 503")
class RagRetrieverImplResilienciaTest {

    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    private Scheduler scheduler;

    @AfterEach
    void liberar() {
        scheduler.dispose();
    }

    private RagRetrieverImpl retriever(int hilos, int cola, int timeoutSegundos) {
        scheduler = Schedulers.newBoundedElastic(hilos, cola, "rag-resiliencia");
        return new RagRetrieverImpl(embeddingModel, embeddingStore,
                new RagProperties(3, 0.65, 0.80, hilos, cola, timeoutSegundos), scheduler);
    }

    private void embeddingLento(long milisegundos) {
        when(embeddingModel.embed(anyString())).thenAnswer(invocacion -> {
            Thread.sleep(milisegundos);
            return Response.from(Embedding.from(new float[] {1f}));
        });
        lenient().when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));
    }

    private static void esServicioNoDisponible(Throwable error) {
        assertThat(error).isInstanceOf(BusinessException.class);
        BusinessException negocio = (BusinessException) error;
        assertThat(negocio.getType()).isEqualTo(ErrorType.SERVICIO_NO_DISPONIBLE);
        assertThat(negocio.getCodigo()).isEqualTo("BUSQUEDA_NO_DISPONIBLE");
    }

    @Test
    void shouldFailWith503_WhenEmbeddingModelThrows() {
        when(embeddingModel.embed(anyString())).thenThrow(new IllegalStateException("onnx"));

        StepVerifier.create(retriever(1, 5, 5).recuperar("pregunta"))
                .expectErrorSatisfies(RagRetrieverImplResilienciaTest::esServicioNoDisponible)
                .verify();
    }

    @Test
    void shouldFailWith503_WhenEmbeddingExceedsTimeout() {
        embeddingLento(3000);

        StepVerifier.create(retriever(1, 5, 1).recuperar("pregunta"))
                .expectErrorSatisfies(RagRetrieverImplResilienciaTest::esServicioNoDisponible)
                .verify();
    }

    @Test
    void shouldFailWith503_WhenQueueIsFull() {
        embeddingLento(1500);
        RagRetrieverImpl retriever = retriever(1, 1, 5);
        for (int i = 0; i < 5; i++) {
            retriever.recuperar("ocupada-" + i).subscribe(resultado -> { }, error -> { });
        }

        StepVerifier.create(retriever.recuperar("tercera"))
                .expectErrorSatisfies(RagRetrieverImplResilienciaTest::esServicioNoDisponible)
                .verify();
    }

    @Test
    void shouldReturnResults_WhenEmbeddingFinishesInTime() {
        embeddingLento(10);

        StepVerifier.create(retriever(1, 5, 5).recuperar("pregunta")).expectNext(List.of()).verifyComplete();
    }
}
