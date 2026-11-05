package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.EstadoPaciente;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import co.com.vidacruz.triage.exception.PacienteNoEncontradoException;
import co.com.vidacruz.triage.mapper.PacienteMapper;
import co.com.vidacruz.triage.repository.AuditoriaRepository;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.PacienteService;
import co.com.vidacruz.triage.service.TriageClassifier;
import co.com.vidacruz.triage.validators.PacienteValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacienteServiceImpl implements PacienteService {

    private final PacienteValidator validator;
    private final TriageClassifier classifier;
    private final TriageProperties properties;
    private final PacienteMapper mapper;
    private final R2dbcEntityTemplate template;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final Clock clock;

    /** Paciente y auditoría se confirman juntos o no se guarda nada. */
    @Override
    @Transactional
    public Mono<PacienteRegistradoResponseDTO> registrar(PacienteRequestDTO request) {
        return Mono.fromSupplier(() -> construirPaciente(request))
                .flatMap(paciente -> template.insert(paciente))
                .flatMap(this::registrarAuditoria)
                .doOnNext(paciente -> log.info("Paciente registrado id={} nivel={}", paciente.getId(), paciente.getNivel()))
                .map(mapper::toRegistradoResponse);
    }

    @Override
    public Mono<PacienteResponseDTO> obtener(UUID id) {
        return pacienteRepository.findById(id)
                .switchIfEmpty(Mono.error(() -> new PacienteNoEncontradoException(id)))
                .map(paciente -> mapper.toResponse(paciente, clock.instant()));
    }

    private Paciente construirPaciente(PacienteRequestDTO request) {
        validator.validar(request);
        NivelTriage nivel = classifier.clasificar(request);
        int slaMinutos = properties.getSla().minutosPara(nivel);
        Instant ahora = clock.instant();
        return mapper.toEntity(request).toBuilder()
                .id(UUID.randomUUID())
                .nivel(nivel)
                .slaMinutos(slaMinutos)
                .estado(EstadoPaciente.REGISTRADO)
                .escalado(false)
                .fechaRegistro(ahora)
                .fechaEstado(ahora)
                .slaVenceEn(ahora.plus(Duration.ofMinutes(slaMinutos)))
                .build();
    }

    private Mono<Paciente> registrarAuditoria(Paciente paciente) {
        AuditoriaClinica auditoria = AuditoriaClinica.builder()
                .pacienteId(paciente.getId())
                .tipoEvento(TipoEvento.PACIENTE_REGISTRADO.getNombre())
                .estadoNuevo(EstadoPaciente.REGISTRADO.name())
                .fechaEvento(paciente.getFechaRegistro())
                .build();
        return auditoriaRepository.save(auditoria).thenReturn(paciente);
    }
}
