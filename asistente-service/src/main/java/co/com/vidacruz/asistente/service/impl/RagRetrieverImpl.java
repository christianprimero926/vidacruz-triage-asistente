package co.com.vidacruz.asistente.service.impl;

import static co.com.vidacruz.asistente.utilities.Constants.COD_BUSQUEDA_NO_DISPONIBLE;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_BUSQUEDA_NO_DISPONIBLE;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.METADATA_PROTOCOLO;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.METADATA_SECCION;

import co.com.vidacruz.asistente.config.RagProperties;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.exception.BusinessException;
import co.com.vidacruz.asistente.exception.ErrorType;
import co.com.vidacruz.asistente.service.RagRetriever;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Service
public class RagRetrieverImpl implements RagRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagProperties properties;
    private final Scheduler ragScheduler;

    public RagRetrieverImpl(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
                            RagProperties properties, @Qualifier("ragScheduler") Scheduler ragScheduler) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
        this.ragScheduler = ragScheduler;
    }

    /**
     * El embedding es CPU intensivo: va a un scheduler acotado y con timeout. Si la cola se llena o el cálculo falla,
     * se responde 503 en lugar de acumular trabajo.
     */
    @Override
    public Mono<List<SegmentoRecuperado>> recuperar(String pregunta) {
        return Mono.fromCallable(() -> buscar(pregunta))
                .subscribeOn(ragScheduler)
                .timeout(Duration.ofSeconds(properties.timeoutSegundos()))
                .onErrorMap(error -> {
                    log.warn("Falla en la búsqueda semántica: {}", error.getClass().getSimpleName());
                    return new BusinessException(ErrorType.SERVICIO_NO_DISPONIBLE, COD_BUSQUEDA_NO_DISPONIBLE,
                            MSG_BUSQUEDA_NO_DISPONIBLE, error);
                });
    }

    private List<SegmentoRecuperado> buscar(String pregunta) {
        EmbeddingSearchRequest solicitud = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(pregunta).content())
                .maxResults(properties.topK())
                .minScore(properties.scoreMinimo())
                .build();
        return embeddingStore.search(solicitud).matches().stream()
                .map(RagRetrieverImpl::aSegmento)
                .toList();
    }

    private static SegmentoRecuperado aSegmento(EmbeddingMatch<TextSegment> coincidencia) {
        TextSegment segmento = coincidencia.embedded();
        return new SegmentoRecuperado(
                segmento.metadata().getString(METADATA_PROTOCOLO),
                segmento.metadata().getString(METADATA_SECCION),
                segmento.text(),
                coincidencia.score());
    }
}
