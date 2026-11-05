package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.domain.AlertaMedica;
import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.EstadoAlerta;
import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.AuditoriaService;
import co.com.vidacruz.triage.service.EscalamientoService;
import co.com.vidacruz.triage.service.OutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscalamientoServiceImpl implements EscalamientoService {

    private static final String ESTADO_ESCALADO = "ESCALADO";

    private final R2dbcEntityTemplate template;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaService auditoriaService;
    private final OutboxService outboxService;
    private final Clock clock;

    /** Marca, auditoría, evento y alerta se confirman juntos; con 0 filas no se hace nada más. */
    @Override
    @Transactional
    public Mono<NivelTriage> escalar(UUID pacienteId) {
        Instant ahora = clock.instant();
        return marcarEscalado(pacienteId, ahora)
                .filter(filas -> filas > 0)
                .flatMap(filas -> pacienteRepository.findById(pacienteId))
                .flatMap(paciente -> registrarEfectos(paciente, ahora))
                .map(Paciente::getNivel)
                .doOnNext(nivel -> log.info("Paciente id={} escalado por superar su SLA", pacienteId));
    }

    /** El UPDATE condicional es la barrera de RN-03: solo una llamada, aunque sean concurrentes, afecta la fila. */
    private Mono<Long> marcarEscalado(UUID pacienteId, Instant ahora) {
        Criteria vigente = Criteria.where("id").is(pacienteId)
                .and("estado").is(EstadoPaciente.REGISTRADO)
                .and("escalado").is(false);
        return template.update(Paciente.class)
                .matching(Query.query(vigente))
                .apply(Update.update("escalado", true).set("fechaEscalamiento", ahora));
    }

    private Mono<Paciente> registrarEfectos(Paciente paciente, Instant ahora) {
        return auditoriaService.registrar(auditoriaDe(paciente, ahora))
                .then(Mono.defer(() -> outboxService.registrar(paciente, TipoEvento.PACIENTE_ESCALADO, ahora)))
                .then(Mono.defer(() -> template.insert(alertaDe(paciente, ahora))))
                .thenReturn(paciente);
    }

    private AuditoriaClinica auditoriaDe(Paciente paciente, Instant ahora) {
        return AuditoriaClinica.builder()
                .pacienteId(paciente.getId())
                .tipoEvento(TipoEvento.PACIENTE_ESCALADO.getNombre())
                .estadoAnterior(EstadoPaciente.REGISTRADO.name())
                .estadoNuevo(ESTADO_ESCALADO)
                .fechaEvento(ahora)
                .build();
    }

    private AlertaMedica alertaDe(Paciente paciente, Instant ahora) {
        return AlertaMedica.builder()
                .id(UUID.randomUUID())
                .pacienteId(paciente.getId())
                .nivel(paciente.getNivel())
                .minutosEspera((int) Duration.between(paciente.getFechaRegistro(), ahora).toMinutes())
                .estado(EstadoAlerta.PENDIENTE)
                .intentos(0)
                .proximoIntento(ahora)
                .fechaCreacion(ahora)
                .build();
    }
}
