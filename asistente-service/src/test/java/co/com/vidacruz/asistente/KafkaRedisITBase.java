package co.com.vidacruz.asistente;

import static org.awaitility.Awaitility.await;

import co.com.vidacruz.asistente.dto.response.EstadoUrgenciasResponseDTO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Kafka y Redis reales compartidos por los IT de esta carpeta; cada clase usa sus propios topics y grupo. */
abstract class KafkaRedisITBase {

    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        KAFKA.start();
        REDIS.start();
    }

    static void registrarPropiedades(DynamicPropertyRegistry registry, String sufijo) {
        crearTopics("eventos-" + sufijo, "eventos-" + sufijo + "-dlt");
        registry.add("asistente.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("asistente.kafka.topic", () -> "eventos-" + sufijo);
        registry.add("asistente.kafka.topic-dlt", () -> "eventos-" + sufijo + "-dlt");
        registry.add("asistente.kafka.grupo", () -> "grupo-" + sufijo);
        registry.add("asistente.kafka.consumidor-habilitado", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static void crearTopics(String... nombres) {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(java.util.Arrays.stream(nombres).map(n -> new NewTopic(n, 1, (short) 1)).toList())
                    .all().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    static String evento(String tipo, String pacienteId, String nivel) {
        return "{\"eventoId\":\"" + java.util.UUID.randomUUID() + "\",\"tipo\":\"" + tipo
                + "\",\"version\":1,\"pacienteId\":\"" + pacienteId + "\",\"nivel\":\"" + nivel
                + "\",\"estado\":\"REGISTRADO\",\"escalado\":false,\"slaMinutos\":5,"
                + "\"ocurridoEn\":\"2026-11-05T15:00:00Z\",\"correlationId\":\"corr-12345678\"}";
    }

    static void publicar(String topic, String clave, String valor) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> productor = new KafkaProducer<>(config)) {
            productor.send(new ProducerRecord<>(topic, clave, valor)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<ConsumerRecord<String, String>> leerTopic(String topic, int esperados) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "lector-" + java.util.UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> leidos = new ArrayList<>();
        try (KafkaConsumer<String, String> consumidor = new KafkaConsumer<>(config)) {
            consumidor.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumidor.poll(Duration.ofMillis(300)).forEach(leidos::add);
                return leidos.size() >= esperados;
            });
        }
        return leidos;
    }

    static EstadoUrgenciasResponseDTO estado(WebTestClient cliente) {
        return cliente.get().uri("/asistente/estado-urgencias").exchange()
                .expectStatus().isOk()
                .returnResult(EstadoUrgenciasResponseDTO.class).getResponseBody().blockFirst();
    }

    static void esperarEstado(WebTestClient cliente, Predicate<EstadoUrgenciasResponseDTO> condicion) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(300))
                .until(() -> condicion.test(estado(cliente)));
    }
}
