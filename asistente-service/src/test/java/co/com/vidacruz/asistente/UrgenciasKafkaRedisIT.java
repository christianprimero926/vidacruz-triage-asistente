package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
@DisplayName("Eventos de Kafka a estado-urgencias en Redis (contenedores reales)")
class UrgenciasKafkaRedisIT extends KafkaRedisITBase {

    private static final String TOPIC = "eventos-proyeccion";
    private static final String TOPIC_DLT = "eventos-proyeccion-dlt";

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private ReactiveStringRedisTemplate redis;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registrarPropiedades(registry, "proyeccion");
    }

    @BeforeEach
    void limpiarRedis() {
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    void shouldCountActivePatientsPerLevel_WhenEventsArePublished() {
        String critico = id();
        String urgente = id();
        String noUrgente = id();
        publicar(TOPIC, critico, evento("paciente_registrado", critico, "CRITICO"));
        publicar(TOPIC, urgente, evento("paciente_registrado", urgente, "URGENTE"));
        publicar(TOPIC, noUrgente, evento("paciente_registrado", noUrgente, "NO_URGENTE"));
        publicar(TOPIC, noUrgente, evento("paciente_en_consulta", noUrgente, "NO_URGENTE"));
        publicar(TOPIC, urgente, evento("paciente_escalado", urgente, "URGENTE"));

        esperarEstado(webTestClient, e -> e.getTotalActivos() == 3);

        assertThat(estado(webTestClient).getNiveles()).containsExactly(
                Map.entry("CRITICO", 1L), Map.entry("URGENTE", 1L), Map.entry("URGENTE_2", 0L),
                Map.entry("NO_URGENTE", 1L), Map.entry("NO_URGENTE_2", 0L));
    }

    @Test
    void shouldCountPatientOnce_WhenSameEventIsRedelivered() {
        String paciente = id();
        String registro = evento("paciente_registrado", paciente, "URGENTE_2");
        publicar(TOPIC, paciente, registro);
        publicar(TOPIC, paciente, registro);
        publicar(TOPIC, paciente, evento("paciente_en_consulta", paciente, "URGENTE_2"));
        String centinela = id();
        publicar(TOPIC, centinela, evento("paciente_registrado", centinela, "CRITICO"));

        esperarEstado(webTestClient, e -> e.getNiveles().get("CRITICO") == 1L);

        assertThat(estado(webTestClient).getNiveles()).containsEntry("URGENTE_2", 1L);
        assertThat(estado(webTestClient).getTotalActivos()).isEqualTo(2L);
    }

    @Test
    void shouldSubtractPatient_WhenAltaArrives() {
        String paciente = id();
        String otro = id();
        publicar(TOPIC, paciente, evento("paciente_registrado", paciente, "CRITICO"));
        publicar(TOPIC, otro, evento("paciente_registrado", otro, "CRITICO"));
        esperarEstado(webTestClient, e -> e.getNiveles().get("CRITICO") == 2L);

        publicar(TOPIC, paciente, evento("paciente_alta", paciente, "CRITICO"));

        esperarEstado(webTestClient, e -> e.getNiveles().get("CRITICO") == 1L);
        assertThat(estado(webTestClient).getTotalActivos()).isEqualTo(1L);
    }

    @Test
    void shouldSendInvalidEventsToDltAndKeepConsuming_WhenPoisonMessagesArrive() {
        String ilegible = "esto no es json";
        String sinNivel = "{\"tipo\":\"paciente_alta\",\"pacienteId\":\"" + id() + "\"}";
        String nivelDesconocido = evento("paciente_registrado", id(), "GRAVISIMO");
        publicar(TOPIC, "k1", ilegible);
        publicar(TOPIC, "k2", sinNivel);
        publicar(TOPIC, "k3", nivelDesconocido);
        String valido = id();
        publicar(TOPIC, valido, evento("paciente_registrado", valido, "NO_URGENTE_2"));

        esperarEstado(webTestClient, e -> e.getNiveles().get("NO_URGENTE_2") == 1L);
        var enDlt = leerTopic(TOPIC_DLT, 3);

        assertThat(enDlt).extracting(r -> r.value()).contains(ilegible, sinNivel, nivelDesconocido);
        assertThat(enDlt).allSatisfy(r -> assertThat(
                new String(r.headers().lastHeader("motivo-dlt").value(), StandardCharsets.UTF_8)).isNotBlank());
        assertThat(estado(webTestClient).getTotalActivos()).isEqualTo(1L);
    }

    @Test
    void shouldSendToDltAndKeepConsuming_WhenRedisRejectsTheCommandPermanently() {
        redis.opsForValue().set("urgencias:activos:URGENTE_2", "no soy un set").block();
        String bloqueado = id();
        String evento = evento("paciente_registrado", bloqueado, "URGENTE_2");
        publicar(TOPIC, bloqueado, evento);
        String siguiente = id();
        publicar(TOPIC, siguiente, evento("paciente_registrado", siguiente, "CRITICO"));

        var enDlt = leerTopic(TOPIC_DLT, 1);
        redis.delete("urgencias:activos:URGENTE_2").block();
        esperarEstado(webTestClient, e -> e.getNiveles().get("CRITICO") == 1L);

        assertThat(enDlt).extracting(r -> r.value()).contains(evento);
        assertThat(enDlt.stream().filter(r -> evento.equals(r.value())).findFirst().orElseThrow()
                .headers().lastHeader("motivo-dlt").value())
                .asString(StandardCharsets.UTF_8).contains("Redis rechazó el comando");
    }
}
