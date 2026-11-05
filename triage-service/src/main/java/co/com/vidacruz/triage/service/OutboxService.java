package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.domain.Paciente;
import co.com.vidacruz.triage.domain.TipoEvento;
import java.time.Instant;
import reactor.core.publisher.Mono;

public interface OutboxService {

    /** Guarda el evento en la transacción que la invoque; un job lo publica a Kafka después del commit. */
    Mono<Void> registrar(Paciente paciente, TipoEvento tipo, Instant ocurridoEn);
}
