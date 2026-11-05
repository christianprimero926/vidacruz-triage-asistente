package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.AlertaMedica;
import co.com.vidacruz.triage.domain.EstadoAlerta;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AlertaMedicaRepository extends ReactiveCrudRepository<AlertaMedica, UUID> {

    Flux<AlertaMedica> findTop200ByOrderByFechaCreacionDescIdAsc();

    Flux<AlertaMedica> findTop200ByEstadoOrderByFechaCreacionDescIdAsc(EstadoAlerta estado);
}
