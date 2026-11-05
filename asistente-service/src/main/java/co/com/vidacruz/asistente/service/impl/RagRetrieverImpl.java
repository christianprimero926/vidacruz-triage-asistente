package co.com.vidacruz.asistente.service.impl;

import static co.com.vidacruz.asistente.utilities.ConstantsFields.METADATA_PROTOCOLO;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.METADATA_SECCION;

import co.com.vidacruz.asistente.config.RagProperties;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.RagRetriever;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class RagRetrieverImpl implements RagRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagProperties properties;

    /** El embedding es CPU intensivo: se saca del event loop. */
    @Override
    public Mono<List<SegmentoRecuperado>> recuperar(String pregunta) {
        return Mono.fromCallable(() -> buscar(pregunta))
                .subscribeOn(Schedulers.boundedElastic());
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
