package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.domain.OutboxEvento;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.event.EventoPacienteDTO;
import co.com.vidacruz.triage.repository.OutboxRepository;
import co.com.vidacruz.triage.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> registrar(Paciente paciente, TipoEvento tipo, Instant ocurridoEn) {
        return Mono.fromCallable(() -> construirEvento(paciente, tipo, ocurridoEn))
                .flatMap(outboxRepository::guardar)
                .then();
    }

    private OutboxEvento construirEvento(Paciente paciente, TipoEvento tipo, Instant ocurridoEn)
            throws JsonProcessingException {
        UUID eventoId = UUID.randomUUID();
        EventoPacienteDTO evento = EventoPacienteDTO.builder()
                .eventoId(eventoId)
                .tipo(tipo.getNombre())
                .version(EventoPacienteDTO.VERSION_ACTUAL)
                .pacienteId(paciente.getId())
                .nivel(paciente.getNivel().name())
                .estado(paciente.getEstado().name())
                .escalado(paciente.isEscalado())
                .slaMinutos(paciente.getSlaMinutos())
                .ocurridoEn(ocurridoEn)
                .build();
        return OutboxEvento.builder()
                .id(eventoId)
                .pacienteId(paciente.getId())
                .tipoEvento(tipo.getNombre())
                .payload(objectMapper.writeValueAsString(evento))
                .fechaCreacion(ocurridoEn)
                .build();
    }
}
