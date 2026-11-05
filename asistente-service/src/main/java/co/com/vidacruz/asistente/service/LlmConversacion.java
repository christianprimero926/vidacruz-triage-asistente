package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Formato de intercambio con el LLM: cómo se le pregunta (prompt) y cómo se interpreta lo que responde. */
@Component
@RequiredArgsConstructor
public class LlmConversacion {

    private final PromptBuilder promptBuilder;
    private final RespuestaLlmParser parser;

    public ChatRequest solicitud(String pregunta, List<SegmentoRecuperado> contexto) {
        return promptBuilder.construir(pregunta, contexto);
    }

    public RespuestaLlm interpretar(String texto) {
        return parser.parsear(texto);
    }
}
