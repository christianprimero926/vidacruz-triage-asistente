package co.com.vidacruz.triage.service.impl;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.dto.response.AuditoriaResponseDTO;
import co.com.vidacruz.triage.exception.PacienteNoEncontradoException;
import co.com.vidacruz.triage.mapper.AuditoriaMapper;
import co.com.vidacruz.triage.repository.AuditoriaRepository;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.AuditoriaService;
import co.com.vidacruz.triage.utilities.CorrelationContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaServiceImpl implements AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final PacienteRepository pacienteRepository;
    private final AuditoriaMapper mapper;

    @Override
    public Mono<Void> registrar(AuditoriaClinica entrada) {
        return Mono.deferContextual(contexto -> {
            if (entrada.getCorrelationId() == null) {
                CorrelationContext.correlationId(contexto).ifPresent(entrada::setCorrelationId);
            }
            return auditoriaRepository.save(entrada).then();
        });
    }

    @Override
    public Flux<AuditoriaResponseDTO> listarPorPaciente(UUID pacienteId) {
        return pacienteRepository.existsById(pacienteId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new PacienteNoEncontradoException(pacienteId)))
                .flatMapMany(existe -> auditoriaRepository.findByPacienteIdOrderByFechaEventoAscIdAsc(pacienteId))
                .map(mapper::toResponse);
    }
}
