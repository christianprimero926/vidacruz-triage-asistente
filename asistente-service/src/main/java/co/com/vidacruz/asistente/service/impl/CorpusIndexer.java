package co.com.vidacruz.asistente.service.impl;

import static co.com.vidacruz.asistente.utilities.Constants.FORMATO_SEGMENTO;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.METADATA_PROTOCOLO;
import static co.com.vidacruz.asistente.utilities.ConstantsFields.METADATA_SECCION;

import co.com.vidacruz.asistente.domain.Protocolo;
import co.com.vidacruz.asistente.service.CorpusLoader;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Construye el índice vectorial con un segmento por sección de cada protocolo. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorpusIndexer {

    private final CorpusLoader corpusLoader;
    private final EmbeddingModel embeddingModel;

    public EmbeddingStore<TextSegment> construirIndice() {
        long inicio = System.nanoTime();
        List<TextSegment> segmentos = corpusLoader.protocolos().stream()
                .flatMap(protocolo -> segmentosDe(protocolo).stream())
                .toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segmentos).content();

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.addAll(embeddings, segmentos);
        log.info("Índice construido: {} segmentos en {} ms", segmentos.size(),
                (System.nanoTime() - inicio) / 1_000_000);
        return store;
    }

    public static List<TextSegment> segmentosDe(Protocolo protocolo) {
        return protocolo.secciones().stream()
                .map(seccion -> TextSegment.from(
                        FORMATO_SEGMENTO.formatted(protocolo.titulo(), seccion.nombre(), seccion.contenido()),
                        Metadata.from(METADATA_PROTOCOLO, protocolo.id())
                                .put(METADATA_SECCION, seccion.nombre())))
                .toList();
    }
}
