package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AuditoriaRepository extends ReactiveCrudRepository<AuditoriaClinica, Long> {
}
