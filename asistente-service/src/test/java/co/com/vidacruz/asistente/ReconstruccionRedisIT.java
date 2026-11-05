package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.consumer.UrgenciasEventConsumer;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Reconstrucción del estado cuando Redis se vacía")
class ReconstruccionRedisIT extends KafkaRedisITBase {

    private static final String TOPIC = "eventos-reconstruccion";

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private ReactiveStringRedisTemplate redis;
    @Autowired
    private UrgenciasEventConsumer consumer;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registrarPropiedades(registry, "reconstruccion");
    }

    @Test
    void shouldRebuildStateFromTopic_WhenRedisIsEmptiedAndConsumerRestarts() {
        String uno = UUID.randomUUID().toString();
        String dos = UUID.randomUUID().toString();
        String tres = UUID.randomUUID().toString();
        publicar(TOPIC, uno, evento("paciente_registrado", uno, "CRITICO"));
        publicar(TOPIC, dos, evento("paciente_registrado", dos, "URGENTE"));
        publicar(TOPIC, tres, evento("paciente_registrado", tres, "URGENTE"));
        publicar(TOPIC, tres, evento("paciente_alta", tres, "URGENTE"));
        esperarEstado(webTestClient, e -> e.getTotalActivos() == 2);

        consumer.stop();
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
        assertThat(estado(webTestClient).getTotalActivos()).isZero();
        consumer.start();

        esperarEstado(webTestClient, e -> e.getTotalActivos() == 2);
        var estado = estado(webTestClient);
        assertThat(estado.getNiveles()).containsEntry("CRITICO", 1L).containsEntry("URGENTE", 1L);
        assertThat(redis.hasKey("urgencias:inicializado").block()).isTrue();
    }

    @Test
    void shouldNotReplayTopic_WhenRedisKeepsItsState() {
        String paciente = UUID.randomUUID().toString();
        publicar(TOPIC, paciente, evento("paciente_registrado", paciente, "NO_URGENTE"));
        esperarEstado(webTestClient, e -> e.getNiveles().get("NO_URGENTE") >= 1L);
        var antes = estado(webTestClient).getNiveles();
        redis.opsForSet().remove("urgencias:activos:NO_URGENTE", paciente).block();

        consumer.stop();
        consumer.start();
        String centinela = UUID.randomUUID().toString();
        publicar(TOPIC, centinela, evento("paciente_registrado", centinela, "NO_URGENTE_2"));
        esperarEstado(webTestClient, e -> e.getNiveles().get("NO_URGENTE_2") == antes.get("NO_URGENTE_2") + 1);

        assertThat(estado(webTestClient).getNiveles().get("NO_URGENTE")).isEqualTo(antes.get("NO_URGENTE") - 1);
    }
}
