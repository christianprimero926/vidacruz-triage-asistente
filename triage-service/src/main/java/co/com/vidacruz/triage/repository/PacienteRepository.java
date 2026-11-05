package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.Paciente;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PacienteRepository extends ReactiveCrudRepository<Paciente, UUID> {

    /** Respaldado por el índice parcial de pacientes que aún pueden escalarse; los más atrasados primero. */
    @Query("""
            SELECT id FROM paciente
            WHERE estado = 'REGISTRADO' AND escalado = FALSE AND sla_vence_en <= :ahora
            ORDER BY sla_vence_en
            LIMIT :lote
            """)
    Flux<UUID> findIdsConSlaVencido(Instant ahora, int lote);
}
