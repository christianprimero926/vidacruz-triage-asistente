package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.domain.EventoUrgencia;
import co.com.vidacruz.asistente.domain.NivelTriage;
import co.com.vidacruz.asistente.domain.TipoEvento;
import co.com.vidacruz.asistente.event.EventoTriaje;
import co.com.vidacruz.asistente.exception.EventoInvalidoException;
import co.com.vidacruz.asistente.utilities.Saneador;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventoParser {

    private final ObjectMapper objectMapper;

    public EventoUrgencia parsear(String json) {
        EventoTriaje evento = leer(json);
        TipoEvento tipo = TipoEvento.deNombre(evento.tipo())
                .orElseThrow(() -> new EventoInvalidoException("Tipo de evento desconocido: " + Saneador.limpiar(evento.tipo())));
        if (evento.pacienteId() == null || evento.pacienteId().isBlank()) {
            throw new EventoInvalidoException("El evento no trae pacienteId");
        }
        NivelTriage nivel = NivelTriage.deNombre(evento.nivel())
                .orElseThrow(() -> new EventoInvalidoException("Nivel ausente o desconocido: " + Saneador.limpiar(evento.nivel())));
        return new EventoUrgencia(tipo, evento.pacienteId(), nivel);
    }

    private EventoTriaje leer(String json) {
        try {
            EventoTriaje evento = json == null ? null : objectMapper.readValue(json, EventoTriaje.class);
            if (evento == null) {
                throw new EventoInvalidoException("El mensaje está vacío");
            }
            return evento;
        } catch (JsonProcessingException e) {
            throw new EventoInvalidoException("El mensaje no es un JSON válido", e);
        }
    }
}
