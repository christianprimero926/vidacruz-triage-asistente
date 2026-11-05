package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.dto.response.AuditoriaResponseDTO;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuditoriaService {

    /** Agrega la entrada dentro de la transacción que la invoque. */
    Mono<Void> registrar(AuditoriaClinica entrada);

    Flux<AuditoriaResponseDTO> listarPorPaciente(UUID pacienteId);
}
