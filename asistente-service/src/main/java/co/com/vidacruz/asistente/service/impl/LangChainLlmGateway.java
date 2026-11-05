package co.com.vidacruz.asistente.service.impl;

import co.com.vidacruz.asistente.config.LlmProperties;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import co.com.vidacruz.asistente.service.LlmGateway;
import co.com.vidacruz.asistente.service.LlmResilience;
import co.com.vidacruz.asistente.service.PromptBuilder;
import co.com.vidacruz.asistente.service.RespuestaLlmParser;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Service
public class LangChainLlmGateway implements LlmGateway {

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final RespuestaLlmParser parser;
    private final LlmProperties properties;
    private final Scheduler llmScheduler;
    private final LlmResilience resilience;

    public LangChainLlmGateway(ChatModel chatModel, PromptBuilder promptBuilder, RespuestaLlmParser parser,
                               LlmProperties properties, @Qualifier("llmScheduler") Scheduler llmScheduler,
                               LlmResilience resilience) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.properties = properties;
        this.llmScheduler = llmScheduler;
        this.resilience = resilience;
    }

    /** La llamada del cliente es bloqueante, por eso corre en un scheduler propio y acotado. */
    @Override
    public Mono<RespuestaLlm> consultar(String pregunta, List<SegmentoRecuperado> contexto) {
        if (!properties.configurado()) {
            return Mono.error(LlmNoDisponibleException.noConfigurado());
        }
        Mono<RespuestaLlm> llamada = Mono
                .fromCallable(() -> chatModel.chat(promptBuilder.construir(pregunta, contexto)).aiMessage().text())
                .subscribeOn(llmScheduler)
                .timeout(Duration.ofSeconds(properties.timeoutSegundos()))
                .map(parser::parsear);
        return resilience.proteger(llamada)
                .onErrorMap(error -> !(error instanceof LlmNoDisponibleException), this::aNoDisponible);
    }

    private LlmNoDisponibleException aNoDisponible(Throwable error) {
        log.warn("Fallo al consultar el proveedor de IA: {}", error.getClass().getSimpleName());
        return LlmNoDisponibleException.porFallo(error);
    }
}
