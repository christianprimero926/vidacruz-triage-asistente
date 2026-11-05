package co.com.vidacruz.asistente.config;

import co.com.vidacruz.asistente.consumer.ReconstruccionEstado;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaConfig {

    /** Los mensajes se leen como texto y se interpretan en el consumidor, para que un mensaje ilegible no rompa el deserializador. */
    @Bean
    @ConditionalOnProperty(name = "asistente.kafka.consumidor-habilitado", havingValue = "true", matchIfMissing = true)
    public KafkaReceiver<String, String> kafkaReceiver(EventosKafkaProperties properties,
                                                       ReconstruccionEstado reconstruccion) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.grupo());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.maxPollRecords());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(config)
                .subscription(List.of(properties.topic()))
                .addAssignListener(reconstruccion::alAsignar)
                .commitInterval(Duration.ofSeconds(1));
        return KafkaReceiver.create(options);
    }

    @Bean(destroyMethod = "close")
    public KafkaSender<String, String> kafkaSender(EventosKafkaProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return KafkaSender.create(SenderOptions.create(config));
    }
}
