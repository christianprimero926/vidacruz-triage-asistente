package co.com.vidacruz.triage.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaConfig {

    /**
     * KafkaProducer.send puede bloquear (metadatos, buffer lleno hasta max.block.ms). Por eso corre en un
     * scheduler acotado y aislado, que sí admite bloqueo, y nunca en el event loop ni en los hilos paralelos.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler kafkaScheduler() {
        return Schedulers.newBoundedElastic(2, 1000, "kafka-sender");
    }

    /** El productor se crea en el primer envío: la app arranca aunque Kafka esté caído. */
    @Bean(destroyMethod = "close")
    public KafkaSender<String, String> kafkaSender(TriageKafkaProperties properties, Scheduler kafkaScheduler) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, properties.getMaxBlockMs());
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, properties.getRequestTimeoutMs());
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, properties.getDeliveryTimeoutMs());
        return KafkaSender.create(SenderOptions.<String, String>create(config)
                .stopOnError(true)
                .scheduler(kafkaScheduler));
    }
}
