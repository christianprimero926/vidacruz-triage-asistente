package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.Paciente;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PacienteRepository extends ReactiveCrudRepository<Paciente, UUID> {
}
