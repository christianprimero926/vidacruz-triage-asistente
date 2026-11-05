package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.dto.request.EstadoRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface EstadoPacienteService {

    Mono<PacienteResponseDTO> cambiarEstado(UUID id, EstadoRequestDTO request);
}
