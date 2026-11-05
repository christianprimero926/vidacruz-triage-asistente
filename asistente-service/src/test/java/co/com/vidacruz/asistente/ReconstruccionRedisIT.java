package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.com.vidacruz.asistente.consumer.UrgenciasEventConsumer;
import java.time.Duration;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Reconstrucción por partición del estado cuando Redis se vacía")
class ReconstruccionRedisIT extends KafkaRedisITBase {

    private static final String TOPIC = "eventos-reconstruccion";

    @Autowired
    private ReactiveStringRedisTemplate redis;
    @Autowired
    private UrgenciasEventConsumer consumer;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registrarPropiedades(registry, "reconstruccion", 2);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private boolean esActivo(String nivel, String paciente) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember("urgencias:activos:" + nivel, paciente).block());
    }

    private boolean hayMarca(int particion) {
        return Boolean.TRUE.equals(redis.hasKey("urgencias:inicializado:" + particion).block());
    }

    private void esperar(java.util.function.BooleanSupplier condicion) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(300)).until(condicion::getAsBoolean);
    }

    private void vaciarRedisYReiniciar() {
        consumer.stop();
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
        consumer.start();
    }

    @Test
    void shouldRebuildStateAndCreateMarksFromTopic_WhenRedisIsEmptiedAndConsumerRestarts() {
        String uno = id();
        String dos = id();
        String tres = id();
        publicar(TOPIC, uno, evento("paciente_registrado", uno, "CRITICO"));
        publicar(TOPIC, dos, evento("paciente_registrado", dos, "URGENTE"));
        publicar(TOPIC, tres, evento("paciente_registrado", tres, "URGENTE"));
        publicar(TOPIC, tres, evento("paciente_alta", tres, "URGENTE"));
        esperar(() -> esActivo("CRITICO", uno) && esActivo("URGENTE", dos));

        vaciarRedisYReiniciar();

        esperar(() -> esActivo("CRITICO", uno) && esActivo("URGENTE", dos) && hayMarca(0) && hayMarca(1));
        assertThat(esActivo("URGENTE", tres)).isFalse();
    }

    @Test
    void shouldReplayOnlyThePartitionWithoutMark_WhenRestartHappensMidReconstruction() {
        vaciarRedisYReiniciar();
        String enParticionCero = id();
        String enParticionUno = id();
        String otroEnParticionUno = id();
        publicarEnParticion(TOPIC, 0, enParticionCero, evento("paciente_registrado", enParticionCero, "CRITICO"));
        publicarEnParticion(TOPIC, 1, enParticionUno, evento("paciente_registrado", enParticionUno, "URGENTE"));
        publicarEnParticion(TOPIC, 1, otroEnParticionUno,
                evento("paciente_registrado", otroEnParticionUno, "URGENTE"));
        esperar(() -> esActivo("CRITICO", enParticionCero) && esActivo("URGENTE", otroEnParticionUno)
                && hayMarca(0) && hayMarca(1));

        // Se simula una caída a mitad de la reconstrucción de la partición 1: sin marca y con la vista a medias.
        consumer.stop();
        redis.delete("urgencias:inicializado:1").block();
        redis.opsForSet().remove("urgencias:activos:URGENTE", enParticionUno, otroEnParticionUno).block();
        redis.opsForSet().remove("urgencias:activos:CRITICO", enParticionCero).block();
        consumer.start();

        esperar(() -> esActivo("URGENTE", enParticionUno) && esActivo("URGENTE", otroEnParticionUno) && hayMarca(1));
        assertThat(esActivo("CRITICO", enParticionCero))
                .as("la partición 0 conserva su marca y no se relee").isFalse();
        assertThat(hayMarca(0)).isTrue();
    }

    @Test
    void shouldNotReplayTopic_WhenEveryPartitionKeepsItsMark() {
        vaciarRedisYReiniciar();
        String paciente = id();
        publicar(TOPIC, paciente, evento("paciente_registrado", paciente, "NO_URGENTE"));
        esperar(() -> esActivo("NO_URGENTE", paciente) && hayMarca(0) && hayMarca(1));
        redis.opsForSet().remove("urgencias:activos:NO_URGENTE", paciente).block();

        consumer.stop();
        consumer.start();
        String centinela = id();
        publicar(TOPIC, centinela, evento("paciente_registrado", centinela, "NO_URGENTE_2"));
        esperar(() -> esActivo("NO_URGENTE_2", centinela));

        assertThat(esActivo("NO_URGENTE", paciente)).isFalse();
    }
}
