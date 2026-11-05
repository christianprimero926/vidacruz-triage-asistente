package co.com.vidacruz.asistente.consumer;

import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverPartition;

/**
 * Reconstruye la vista desde el topic por partición. Una partición sin marca en Redis se relee desde el inicio y
 * la marca solo se crea cuando el consumo alcanza el último offset que existía al asignarla. Si el proceso cae a
 * mitad no hay marca y al volver se relee todo, lo cual es idempotente. Cada partición tiene un único dueño en el
 * grupo, así que varias réplicas no se pisan.
 */
@Slf4j
@Component
public class ReconstruccionEstado {

    private final UrgenciasRepository repository;
    private final ObjectProvider<KafkaReceiver<String, String>> receiver;
    private final Set<Integer> inicializadas = ConcurrentHashMap.newKeySet();
    private final Map<Integer, ProgresoParticion> pendientes = new ConcurrentHashMap<>();

    @Autowired
    public ReconstruccionEstado(UrgenciasRepository repository, ObjectProvider<KafkaReceiver<String, String>> receiver) {
        this.repository = repository;
        this.receiver = receiver;
    }

    /** Las marcas se leen de forma reactiva antes de suscribirse, para no bloquear el hilo del consumer. */
    public Mono<Void> prepararArranque() {
        return repository.particionesInicializadas()
                .doOnNext(marcadas -> {
                    inicializadas.clear();
                    pendientes.clear();
                    inicializadas.addAll(marcadas);
                })
                .then();
    }

    public void alAsignar(Collection<ReceiverPartition> particiones) {
        List<TopicPartition> porReconstruir = particiones.stream()
                .filter(particion -> !inicializadas.contains(particion.topicPartition().partition()))
                .peek(ReceiverPartition::seekToBeginning)
                .map(ReceiverPartition::topicPartition)
                .toList();
        if (porReconstruir.isEmpty()) {
            return;
        }
        porReconstruir.forEach(tp -> pendientes.put(tp.partition(), new ProgresoParticion()));
        log.info("Particiones sin marca, se releen desde el inicio: {}", porReconstruir.size());
        receiver.getObject()
                .doOnConsumer(consumer -> new RangoOffsets(consumer.beginningOffsets(porReconstruir),
                        consumer.endOffsets(porReconstruir)))
                .flatMap(rango -> fijarObjetivos(porReconstruir, rango))
                .subscribe(vacio -> { }, error -> log.warn("No se pudo fijar el offset objetivo: {}",
                        error.toString()));
    }

    /** Se invoca tras aplicar cada evento; crea la marca de la partición cuando alcanza su offset objetivo. */
    public Mono<Void> alProcesar(int particion, long offset) {
        ProgresoParticion progreso = pendientes.get(particion);
        if (progreso == null || !progreso.registrar(offset)) {
            return Mono.empty();
        }
        return marcar(particion);
    }

    private Mono<Void> fijarObjetivos(List<TopicPartition> particiones, RangoOffsets rango) {
        return Mono.when(particiones.stream().map(tp -> {
            ProgresoParticion progreso = pendientes.get(tp.partition());
            boolean completa = progreso != null
                    && progreso.fijarObjetivo(rango.inicio().get(tp), rango.fin().get(tp));
            return completa ? marcar(tp.partition()) : Mono.<Void>empty();
        }).toList());
    }

    private Mono<Void> marcar(int particion) {
        return repository.marcarParticion(particion).doOnSuccess(vacio -> {
            pendientes.remove(particion);
            inicializadas.add(particion);
            log.info("Partición {} reconstruida", particion);
        });
    }

    private record RangoOffsets(Map<TopicPartition, Long> inicio, Map<TopicPartition, Long> fin) {
    }
}
