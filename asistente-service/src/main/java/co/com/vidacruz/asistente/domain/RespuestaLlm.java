package co.com.vidacruz.asistente.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Respuesta estructurada que se le exige al LLM; aún no está validada contra el corpus. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaLlm(String respuesta, List<FuenteCitada> fuentes, Boolean sinProtocolo) {
}
