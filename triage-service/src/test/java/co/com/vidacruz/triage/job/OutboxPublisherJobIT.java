package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.KafkaTestConsumer;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

@DisplayName("Outbox y publicación a Kafka")
class OutboxPublisherJobIT extends AbstractIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Autowired
    private OutboxPublisherJob job;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry meterRegistry;

    private List<ConsumerRecord<String, String>> leer(List<UUID> pacientes, int esperados) {
        try (KafkaTestConsumer consumidor = new KafkaTestConsumer(bootstrapServers(), TOPIC)) {
            return consumidor.leer(pacientes, esperados, ESPERA, Duration.ofMillis(1500));
        }
    }

    private JsonNode json(ConsumerRecord<String, String> registro) throws Exception {
        return objectMapper.readTree(registro.value());
    }

    private List<String> campo(List<ConsumerRecord<String, String>> registros, String nombre) {
        return registros.stream().map(registro -> {
            try {
                return json(registro).get(nombre).asText();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }).toList();
    }

    private double gauge() {
        return meterRegistry.get("triage.outbox.pendientes").gauge().value();
    }

    @Nested
    @DisplayName("Contrato del evento")
    class Contrato {

        @Test
        @DisplayName("el registro guarda el evento en la misma transacción y se publica con clave y JSON esperados")
        void shouldPublishEventWithKeyAndContract_WhenPacienteIsRegistered() throws Exception {
            UUID id = crearPaciente(PacienteFixtures.e1());
            assertThat(contar("outbox_evento")).isEqualTo(1);

            job.ejecutar().block(ESPERA);

            var registros = leer(List.of(id), 1);
            assertThat(registros).hasSize(1);
            var registro = registros.get(0);
            JsonNode evento = json(registro);
            assertThat(registro.key()).isEqualTo(id.toString());
            assertThat(new String(registro.headers().lastHeader("tipo-evento").value(), StandardCharsets.UTF_8))
                    .isEqualTo("paciente_registrado");
            assertThat(new String(registro.headers().lastHeader("correlation-id").value(), StandardCharsets.UTF_8))
                    .matches("[0-9a-f-]{36}");
            assertThat(evento.get("eventoId").asText()).isNotBlank();
            assertThat(evento.get("tipo").asText()).isEqualTo("paciente_registrado");
            assertThat(evento.get("version").asInt()).isEqualTo(1);
            assertThat(evento.get("pacienteId").asText()).isEqualTo(id.toString());
            assertThat(evento.get("nivel").asText()).isEqualTo("CRITICO");
            assertThat(evento.get("estado").asText()).isEqualTo("REGISTRADO");
            assertThat(evento.get("escalado").asBoolean()).isFalse();
            assertThat(evento.get("slaMinutos").asInt()).isEqualTo(5);
            assertThat(evento.get("ocurridoEn").asText()).isEqualTo(TestClockConfig.INICIO.toString());
            assertThat(registro.value()).doesNotContain("Paciente de Prueba").doesNotContain("Dolor tor");
        }

        @Test
        @DisplayName("el evento publicado queda marcado y el gauge de pendientes vuelve a cero")
        void shouldMarkAsPublishedAndResetGauge_WhenEventIsConfirmed() {
            UUID id = crearPaciente(PacienteFixtures.e4());

            job.ejecutar().block(ESPERA);

            assertThat(leer(List.of(id), 1)).hasSize(1);
            assertThat(contar("outbox_evento WHERE fecha_publicacion IS NOT NULL")).isEqualTo(1);
            assertThat(gauge()).isZero();
        }

        @Test
        @DisplayName("un evento ya publicado no se vuelve a enviar")
        void shouldNotRepublish_WhenEventWasAlreadyPublished() {
            UUID id = crearPaciente(PacienteFixtures.e4());

            job.ejecutar().block(ESPERA);
            job.ejecutar().block(ESPERA);

            assertThat(leer(List.of(id), 1)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Estados")
    class Estados {

        @Test
        @DisplayName("cada transición válida genera su evento y una inválida no genera ninguno")
        void shouldCreateOneEventPerValidTransition_WhenStateChanges() throws Exception {
            UUID id = crearPaciente(PacienteFixtures.e2());
            moverPaciente(id, "EN_CONSULTA").expectStatus().isOk();
            moverPaciente(id, "REGISTRADO").expectStatus().isEqualTo(409);
            moverPaciente(id, "ALTA").expectStatus().isOk();
            moverPaciente(id, "EN_CONSULTA").expectStatus().isEqualTo(409);

            assertThat(contar("outbox_evento")).isEqualTo(3);
            job.ejecutar().block(ESPERA);

            var registros = leer(List.of(id), 3);
            assertThat(campo(registros, "tipo")).containsExactly("paciente_registrado", "paciente_en_consulta", "paciente_alta");
            assertThat(campo(registros, "estado")).containsExactly("REGISTRADO", "EN_CONSULTA", "ALTA");
        }

        @Test
        @DisplayName("mantiene el orden de los eventos de cada paciente aunque se intercalen")
        void shouldKeepOrderPerPaciente_WhenEventsAreInterleaved() {
            UUID a = crearPaciente(PacienteFixtures.e1());
            UUID b = crearPaciente(PacienteFixtures.e2());
            UUID c = crearPaciente(PacienteFixtures.e3());
            moverPaciente(a, "EN_CONSULTA").expectStatus().isOk();
            moverPaciente(c, "ALTA").expectStatus().isOk();
            moverPaciente(b, "EN_CONSULTA").expectStatus().isOk();
            moverPaciente(a, "ALTA").expectStatus().isOk();
            moverPaciente(b, "ALTA").expectStatus().isOk();

            job.ejecutar().block(ESPERA);

            var registros = leer(List.of(a, b, c), 8);
            Map<String, List<ConsumerRecord<String, String>>> porPaciente =
                    registros.stream().collect(Collectors.groupingBy(ConsumerRecord::key));
            assertThat(campo(porPaciente.get(a.toString()), "tipo")).containsExactly(
                    "paciente_registrado", "paciente_en_consulta", "paciente_alta");
            assertThat(campo(porPaciente.get(b.toString()), "tipo")).containsExactly(
                    "paciente_registrado", "paciente_en_consulta", "paciente_alta");
            assertThat(campo(porPaciente.get(c.toString()), "tipo")).containsExactly(
                    "paciente_registrado", "paciente_alta");
            porPaciente.values().forEach(lista ->
                    assertThat(lista.stream().map(ConsumerRecord::partition).distinct()).hasSize(1));
        }

    }

    @Nested
    @DisplayName("Concurrencia y atomicidad")
    class Robustez {

        @Test
        @DisplayName("dos publicadores simultáneos no duplican eventos gracias al advisory lock")
        void shouldNotDuplicateEvents_WhenTwoPublishersRunConcurrently() {
            List<UUID> ids = List.of(crearPaciente(PacienteFixtures.e1()), crearPaciente(PacienteFixtures.e2()),
                    crearPaciente(PacienteFixtures.e3()), crearPaciente(PacienteFixtures.e4()),
                    crearPaciente(PacienteFixtures.e5()));

            Mono.when(job.ejecutar(), job.ejecutar()).block(ESPERA);

            assertThat(leer(ids, 5)).hasSize(5);
        }

        @Test
        @DisplayName("si falla el guardado del outbox no queda el paciente ni su auditoría")
        void shouldRollbackEverything_WhenOutboxInsertFails() {
            databaseClient.sql("CREATE FUNCTION falla_outbox() RETURNS TRIGGER AS 'BEGIN RAISE EXCEPTION ''fallo''; END;' "
                    + "LANGUAGE plpgsql").then().block();
            databaseClient.sql("CREATE TRIGGER tr_falla_outbox BEFORE INSERT ON outbox_evento "
                    + "FOR EACH ROW EXECUTE FUNCTION falla_outbox()").then().block();
            try {
                webTestClient.post().uri("/triage/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(PacienteFixtures.e1())
                        .exchange()
                        .expectStatus().is5xxServerError();

                assertThat(contar("paciente")).isZero();
                assertThat(contar("auditoria_clinica")).isZero();
            } finally {
                databaseClient.sql("DROP TRIGGER tr_falla_outbox ON outbox_evento").then().block();
                databaseClient.sql("DROP FUNCTION falla_outbox()").then().block();
            }
        }
    }

    @Nested
    @DisplayName("Kafka caído")
    class KafkaCaido {

        @Test
        @DisplayName("el POST responde 201, el evento queda pendiente y sale cuando Kafka vuelve")
        void shouldRegisterAndPublishLater_WhenKafkaIsDown() {
            pausarKafka();
            UUID id;
            try {
                id = crearPaciente(PacienteFixtures.e1());

                job.ejecutar().block(Duration.ofSeconds(60));

                assertThat(contar("outbox_evento WHERE fecha_publicacion IS NULL")).isEqualTo(1);
                assertThat(contar("outbox_evento WHERE intentos > 0 OR fecha_descarte IS NOT NULL")).isZero();
                assertThat(gauge()).isEqualTo(1.0);
                assertThat(contar("paciente")).isEqualTo(1);
            } finally {
                reanudarKafka();
            }

            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                job.ejecutar().block(Duration.ofSeconds(30));
                assertThat(contar("outbox_evento WHERE fecha_publicacion IS NULL")).isZero();
            });
            // Al menos una vez: si el envío vence en el job pero llega al broker, el reintento lo repite
            assertThat(campo(leer(List.of(id), 1), "eventoId").stream().distinct().toList()).hasSize(1);
            assertThat(gauge()).isZero();
        }
    }
}
