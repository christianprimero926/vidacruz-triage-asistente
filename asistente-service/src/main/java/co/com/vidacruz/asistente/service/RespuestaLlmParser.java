package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.exception.LlmNoDisponibleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Extrae el JSON de la respuesta del modelo aunque venga en un bloque de código o con texto alrededor. */
@Component
@RequiredArgsConstructor
public class RespuestaLlmParser {

    private final ObjectMapper objectMapper;

    public RespuestaLlm parsear(String texto) {
        int inicio = texto == null ? -1 : texto.indexOf('{');
        int fin = texto == null ? -1 : texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            throw LlmNoDisponibleException.porFallo(new IllegalArgumentException("La respuesta no contiene JSON"));
        }
        try {
            return objectMapper.readValue(texto.substring(inicio, fin + 1), RespuestaLlm.class);
        } catch (JsonProcessingException e) {
            throw LlmNoDisponibleException.porFallo(e);
        }
    }
}
