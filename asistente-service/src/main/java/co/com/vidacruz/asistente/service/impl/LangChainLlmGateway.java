package co.com.vidacruz.asistente.service.impl;

import co.com.vidacruz.asistente.config.LlmProperties;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import co.com.vidacruz.asistente.service.LlmConversacion;
import co.com.vidacruz.asistente.service.LlmGateway;
import co.com.vidacruz.asistente.service.LlmResilience;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangChainLlmGateway implements LlmGateway {

    private final ChatModel chatModel;
    private final LlmConversacion conversacion;
    private final LlmProperties properties;
    private final LlmResilience resilience;

    @Override
    public Mono<RespuestaLlm> consultar(String pregunta, List<SegmentoRecuperado> contexto) {
        if (!properties.configurado()) {
            return Mono.error(LlmNoDisponibleException.noConfigurado());
        }
        return resilience.ejecutar(() -> conversacion.interpretar(textoDe(pregunta, contexto)))
                .onErrorMap(error -> !(error instanceof LlmNoDisponibleException), this::aNoDisponible);
    }

    /** Un texto nulo o vacío es un fallo del proveedor: no puede convertirse en un Mono vacío ni en un 200 sin cuerpo. */
    private String textoDe(String pregunta, List<SegmentoRecuperado> contexto) {
        String texto = chatModel.chat(conversacion.solicitud(pregunta, contexto)).aiMessage().text();
        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException("El modelo devolvió una respuesta vacía");
        }
        return texto;
    }

    private LlmNoDisponibleException aNoDisponible(Throwable error) {
        log.warn("Fallo al consultar el proveedor de IA: {}", error.getClass().getSimpleName());
        return LlmNoDisponibleException.porFallo(error);
    }
}
