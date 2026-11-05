package co.com.vidacruz.asistente.service;

import static co.com.vidacruz.asistente.utilities.Constants.FORMATO_BLOQUE_CONTEXTO;
import static co.com.vidacruz.asistente.utilities.Constants.FORMATO_PROMPT_USUARIO;
import static co.com.vidacruz.asistente.utilities.Constants.PROMPT_SISTEMA;

import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public ChatRequest construir(String pregunta, List<SegmentoRecuperado> contexto) {
        String bloques = contexto.stream()
                .map(s -> FORMATO_BLOQUE_CONTEXTO.formatted(s.protocolo(), s.seccion(), s.texto()))
                .collect(Collectors.joining("\n\n"));
        return ChatRequest.builder()
                .messages(SystemMessage.from(PROMPT_SISTEMA),
                        UserMessage.from(FORMATO_PROMPT_USUARIO.formatted(bloques, neutralizar(pregunta))))
                .build();
    }

    /** Quita los ángulos para que la pregunta no pueda cerrar el delimitador y colarse como instrucción. */
    private static String neutralizar(String pregunta) {
        return pregunta.replace('<', ' ').replace('>', ' ').strip();
    }
}
