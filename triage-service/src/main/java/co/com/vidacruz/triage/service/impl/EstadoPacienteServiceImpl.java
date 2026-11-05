package co.com.vidacruz.triage.service.impl;

import static co.com.vidacruz.triage.utilities.Constants.CODIGO_CONFLICTO_CONCURRENTE;
import static co.com.vidacruz.triage.utilities.Constants.MSG_CONFLICTO_CONCURRENTE;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.dto.request.EstadoRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import co.com.vidacruz.triage.exception.BusinessException;
import co.com.vidacruz.triage.exception.ErrorType;
import co.com.vidacruz.triage.exception.PacienteNoEncontradoException;
import co.com.vidacruz.triage.exception.TransicionInvalidaException;
import co.com.vidacruz.triage.mapper.PacienteMapper;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.AuditoriaService;
import co.com.vidacruz.triage.service.OutboxService;
import co.com.vidacruz.triage.service.EstadoPacienteService;
import co.com.vidacruz.triage.validators.EstadoValidator;
import java.time.Clock;
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
public class EstadoPacienteServiceImpl implements EstadoPacienteService {

    private final EstadoValidator validator;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaService auditoriaService;
    private final OutboxService outboxService;
    private final PacienteMapper mapper;
    private final R2dbcEntityTemplate template;
    private final Clock clock;

    /** Cambio de estado y auditoría se confirman juntos. */
    @Override
    @Transactional
    public Mono<PacienteResponseDTO> cambiarEstado(UUID id, EstadoRequestDTO request) {
        return Mono.fromSupplier(() -> validator.validar(request))
                .flatMap(destino -> pacienteRepository.findById(id)
                        .switchIfEmpty(Mono.error(() -> new PacienteNoEncontradoException(id)))
                        .flatMap(paciente -> aplicarTransicion(paciente, destino)))
                .map(paciente -> mapper.toResponse(paciente, clock.instant()));
    }

    private Mono<Paciente> aplicarTransicion(Paciente paciente, EstadoPaciente destino) {
        EstadoPaciente origen = paciente.getEstado();
        if (!origen.puedePasarA(destino)) {
            return Mono.error(new TransicionInvalidaException(origen, destino));
        }
        Instant ahora = clock.instant();
        UUID id = paciente.getId();
        return actualizarSiSigueEn(id, origen, destino, ahora)
                .flatMap(filas -> filas == 0 ? Mono.error(conflictoConcurrente()) : Mono.just(filas))
                .then(Mono.defer(() -> pacienteRepository.findById(id)))
                .flatMap(actualizado -> registrarEfectos(actualizado, origen, destino, ahora))
                .doOnSuccess(resultado -> log.info("Paciente id={} pasó de {} a {}", id, origen, destino));
    }

    /**
     * El paciente se relee tras el UPDATE: si escalar confirmó entre la primera lectura y el UPDATE, el evento
     * y la respuesta deben llevar escalado=true y no el dato de la lectura inicial.
     */
    private Mono<Paciente> registrarEfectos(Paciente actualizado, EstadoPaciente origen, EstadoPaciente destino,
                                            Instant ahora) {
        TipoEvento tipo = TipoEvento.paraEstado(destino);
        AuditoriaClinica auditoria = AuditoriaClinica.de(actualizado.getId(), tipo, destino.name(), ahora)
                .conEstadoAnterior(origen.name());
        return auditoriaService.registrar(auditoria)
                .then(Mono.defer(() -> outboxService.registrar(actualizado, tipo, ahora)))
                .thenReturn(actualizado);
    }

    /** UPDATE condicional: si otro cambió el estado antes, no afecta filas y se informa el conflicto. */
    private Mono<Long> actualizarSiSigueEn(UUID id, EstadoPaciente origen, EstadoPaciente destino, Instant ahora) {
        return template.update(Paciente.class)
                .matching(Query.query(Criteria.where("id").is(id).and("estado").is(origen)))
                .apply(Update.update("estado", destino).set("fechaEstado", ahora));
    }

    private BusinessException conflictoConcurrente() {
        return new BusinessException(ErrorType.CONFLICTO, CODIGO_CONFLICTO_CONCURRENTE, MSG_CONFLICTO_CONCURRENTE);
    }
}
