package co.com.vidacruz.asistente.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class LlmConfig {

    private static final String CLAVE_SIN_CONFIGURAR = "sin-configurar";

    /** Sin reintentos y temperatura 0: los reintentos los decide el circuit breaker, no el cliente. */
    @Bean
    public ChatModel chatModel(LlmProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.configurado() ? properties.apiKey() : CLAVE_SIN_CONFIGURAR)
                .baseUrl(properties.baseUrl())
                .modelName(properties.model())
                .temperature(0.0)
                .maxRetries(0)
                .timeout(Duration.ofSeconds(properties.timeoutSegundos()))
                .build();
    }

    /** Scheduler acotado y aislado: una llamada lenta al LLM no consume hilos de otras tareas (bulkhead). */
    @Bean(destroyMethod = "dispose")
    public Scheduler llmScheduler(LlmProperties properties) {
        return Schedulers.newBoundedElastic(properties.hilos(), properties.cola(), "llm");
    }
}
