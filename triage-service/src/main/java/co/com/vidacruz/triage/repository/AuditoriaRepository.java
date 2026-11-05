package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AuditoriaRepository extends ReactiveCrudRepository<AuditoriaClinica, Long> {

    Flux<AuditoriaClinica> findByPacienteIdOrderByFechaEventoAscIdAsc(UUID pacienteId);
}
