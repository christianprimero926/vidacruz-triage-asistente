package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.dto.response.PacienteResponseDTO;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface PacienteService {

    Mono<PacienteRegistradoResponseDTO> registrar(PacienteRequestDTO request);

    Mono<PacienteResponseDTO> obtener(UUID id);
}
