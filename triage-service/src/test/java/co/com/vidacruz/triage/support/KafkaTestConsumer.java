package co.com.vidacruz.triage.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Consumidor de prueba: lee el topic desde el inicio y se queda solo con los pacientes que interesan. */
public class KafkaTestConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaTestConsumer(String bootstrapServers, String topic) {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
    }

    /** Espera hasta tener los mensajes esperados y sigue leyendo durante el margen para detectar duplicados. */
    public List<ConsumerRecord<String, String>> leer(Collection<UUID> pacientes, int esperados,
                                                      Duration espera, Duration margen) {
        Set<String> claves = pacientes.stream().map(UUID::toString).collect(Collectors.toSet());
        List<ConsumerRecord<String, String>> leidos = new ArrayList<>();
        Instant limite = Instant.now().plus(espera);
        while (leidos.size() < esperados && Instant.now().isBefore(limite)) {
            acumular(claves, leidos, Duration.ofMillis(500));
        }
        Instant finMargen = Instant.now().plus(margen);
        while (Instant.now().isBefore(finMargen)) {
            acumular(claves, leidos, Duration.ofMillis(200));
        }
        return leidos;
    }

    private void acumular(Set<String> claves, List<ConsumerRecord<String, String>> leidos, Duration poll) {
        for (ConsumerRecord<String, String> registro : consumer.poll(poll)) {
            if (claves.contains(registro.key())) {
                leidos.add(registro);
            }
        }
    }

    @Override
    public void close() {
        consumer.close(Duration.ofSeconds(2));
    }
}
