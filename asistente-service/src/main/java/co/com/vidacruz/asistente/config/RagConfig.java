package co.com.vidacruz.asistente.config;

import co.com.vidacruz.asistente.service.impl.CorpusIndexer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class RagConfig {

    /** Modelo ONNX local: la búsqueda no depende del proveedor del LLM. */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    /** Scheduler acotado para los embeddings (CPU), separado del de LLM y de boundedElastic. */
    @Bean(destroyMethod = "dispose")
    public Scheduler ragScheduler(RagProperties properties) {
        return Schedulers.newBoundedElastic(properties.hilos(), properties.cola(), "rag");
    }

    /** Se indexa al crear el bean para que la aplicación no esté lista antes de tener el índice. */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(CorpusIndexer indexer) {
        return indexer.construirIndice();
    }
}
