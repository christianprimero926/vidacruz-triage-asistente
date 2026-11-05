package co.com.vidacruz.asistente.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mensaje de triaje.eventos tal como llega; lector tolerante: los campos desconocidos se ignoran. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoTriaje(String eventoId, String tipo, Integer version, String pacienteId, String nivel,
                           String estado, Boolean escalado, Integer slaMinutos, String ocurridoEn,
                           String correlationId) {
}
