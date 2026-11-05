package co.com.vidacruz.asistente.consumer;

import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverPartition;

/**
 * Si Redis llegó vacío (sin la clave de inicialización) el consumidor relee el topic desde el inicio:
 * el estado se reconstruye solo, porque los eventos son la fuente de verdad.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconstruccionEstado {

    private final UrgenciasRepository repository;
    private final AtomicBoolean pendiente = new AtomicBoolean(false);

    /** La consulta a Redis es reactiva y se hace antes de suscribirse, para no bloquear el hilo del consumer. */
    public Mono<Void> prepararArranque() {
        return repository.inicializado()
                .flatMap(existe -> existe
                        ? Mono.just(false)
                        : repository.marcarInicializado().thenReturn(true))
                .doOnNext(pendiente::set)
                .then();
    }

    public void alAsignar(Collection<ReceiverPartition> particiones) {
        if (pendiente.compareAndSet(true, false)) {
            log.info("Redis sin estado: se relee el topic desde el inicio en {} particiones", particiones.size());
            particiones.forEach(ReceiverPartition::seekToBeginning);
        }
    }
}
