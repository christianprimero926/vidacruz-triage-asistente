package co.com.vidacruz.triage.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.job.SlaMonitorJob;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@DisplayName("Escalamiento por SLA (RF-03, RN-03)")
class EscalamientoIT extends AbstractIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Autowired
    private SlaMonitorJob job;
    @Autowired
    private EscalamientoService escalamientoService;
    @Autowired
    private ObjectMapper objectMapper;

    private long escalamientos(String tabla, UUID id) {
        return contar(tabla + " WHERE paciente_id = '" + id + "' AND "
                + (tabla.equals("alerta_medica") ? "TRUE" : "tipo_evento = 'paciente_escalado'"));
    }

    private void verificarUnSoloEscalamiento(UUID id) {
        assertThat(escalamientos("auditoria_clinica", id)).isEqualTo(1);
        assertThat(escalamientos("outbox_evento", id)).isEqualTo(1);
        assertThat(escalamientos("alerta_medica", id)).isEqualTo(1);
    }

    @Nested
    @DisplayName("Monitor de SLA")
    class Monitor {

        @Test
        @DisplayName("un SLA no vencido no escala")
        void shouldNotEscalate_WhenSlaHasNotExpired() {
            UUID id = crearPaciente(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(4).plusSeconds(59));

            job.ejecutar().block(ESPERA);

            webTestClient.get().uri("/triage/pacientes/{id}", id).exchange()
                    .expectBody().jsonPath("$.escalado").isEqualTo(false);
            assertThat(contar("alerta_medica")).isZero();
        }

        @Test
        @DisplayName("un SLA vencido escala: flag, fecha, auditoría REGISTRADO a ESCALADO, evento y alerta pendiente")
        void shouldEscalate_WhenSlaHasExpired() throws Exception {
            UUID id = crearPaciente(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(5));

            job.ejecutar().block(ESPERA);

            webTestClient.get().uri("/triage/pacientes/{id}", id).exchange()
                    .expectBody()
                    .jsonPath("$.escalado").isEqualTo(true)
                    .jsonPath("$.estado").isEqualTo("REGISTRADO")
                    .jsonPath("$.fechaEscalamiento").isEqualTo(TestClockConfig.INICIO.plusSeconds(300).toString());
            webTestClient.get().uri("/triage/pacientes/{id}/auditoria", id).exchange()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[1].tipoEvento").isEqualTo("paciente_escalado")
                    .jsonPath("$[1].estadoAnterior").isEqualTo("REGISTRADO")
                    .jsonPath("$[1].estadoNuevo").isEqualTo("ESCALADO")
                    .jsonPath("$[1].fechaEvento").isEqualTo(TestClockConfig.INICIO.plusSeconds(300).toString());
            verificarUnSoloEscalamiento(id);

            String payload = databaseClient.sql("SELECT payload FROM outbox_evento WHERE tipo_evento = 'paciente_escalado'")
                    .map(fila -> fila.get(0, String.class)).one().block();
            JsonNode evento = objectMapper.readTree(payload);
            assertThat(evento.get("tipo").asText()).isEqualTo("paciente_escalado");
            assertThat(evento.get("escalado").asBoolean()).isTrue();
            assertThat(evento.get("nivel").asText()).isEqualTo("CRITICO");
            assertThat(evento.get("pacienteId").asText()).isEqualTo(id.toString());

            var alerta = databaseClient.sql("SELECT nivel, minutos_espera, estado, intentos FROM alerta_medica")
                    .map(fila -> List.of(fila.get("nivel", String.class), fila.get("minutos_espera", Integer.class),
                            fila.get("estado", String.class), fila.get("intentos", Integer.class)))
                    .one().block();
            assertThat(alerta).containsExactly("CRITICO", 5, "PENDIENTE", 0);
        }

        @Test
        @DisplayName("cada nivel vence con su propio SLA")
        void shouldEscalateEachLevelAtItsOwnSla_WhenTimePasses() {
            UUID critico = crearPaciente(PacienteFixtures.e1());
            UUID urgente = crearPaciente(PacienteFixtures.e2());
            UUID noUrgente = crearPaciente(PacienteFixtures.e4());

            clock.avanzar(Duration.ofMinutes(5));
            job.ejecutar().block(ESPERA);
            assertThat(contar("paciente WHERE escalado")).isEqualTo(1);

            clock.avanzar(Duration.ofMinutes(5));
            job.ejecutar().block(ESPERA);
            assertThat(contar("paciente WHERE escalado")).isEqualTo(2);

            clock.avanzar(Duration.ofMinutes(50));
            job.ejecutar().block(ESPERA);
            assertThat(contar("paciente WHERE escalado")).isEqualTo(3);
            assertThat(escalamientos("alerta_medica", critico) + escalamientos("alerta_medica", urgente)
                    + escalamientos("alerta_medica", noUrgente)).isEqualTo(3);
        }

        @Test
        @DisplayName("correr el monitor varias veces no duplica nada")
        void shouldNotDuplicate_WhenMonitorRunsRepeatedly() {
            UUID id = crearPaciente(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(6));

            job.ejecutar().block(ESPERA);
            job.ejecutar().block(ESPERA);
            job.ejecutar().block(ESPERA);

            verificarUnSoloEscalamiento(id);
        }
    }

    @Nested
    @DisplayName("Idempotencia (RN-03)")
    class Idempotencia {

        @Test
        @DisplayName("dos llamadas seguidas producen un evento, una auditoría y una alerta")
        void shouldEscalateOnce_WhenCalledTwice() {
            UUID id = crearPaciente(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(6));

            assertThat(escalamientoService.escalar(id).block(ESPERA)).isTrue();
            assertThat(escalamientoService.escalar(id).block(ESPERA)).isFalse();

            verificarUnSoloEscalamiento(id);
        }

        @Test
        @DisplayName("ocho llamadas concurrentes escalan una sola vez")
        void shouldEscalateOnce_WhenCalledConcurrently() {
            UUID id = crearPaciente(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(6));

            List<Boolean> resultados = Flux.range(0, 8)
                    .flatMap(i -> escalamientoService.escalar(id).subscribeOn(Schedulers.parallel()))
                    .collectList().block(ESPERA);

            assertThat(resultados).filteredOn(Boolean::booleanValue).hasSize(1);
            verificarUnSoloEscalamiento(id);
        }

        @Test
        @DisplayName("un paciente en EN_CONSULTA o ALTA nunca se escala, ni por el monitor ni directamente")
        void shouldNeverEscalate_WhenPacienteIsInConsultaOrDischarged() {
            UUID enConsulta = crearPaciente(PacienteFixtures.e1());
            UUID dadoDeAlta = crearPaciente(PacienteFixtures.e2());
            moverPaciente(enConsulta, "EN_CONSULTA").expectStatus().isOk();
            moverPaciente(dadoDeAlta, "ALTA").expectStatus().isOk();
            clock.avanzar(Duration.ofHours(3));

            job.ejecutar().block(ESPERA);

            assertThat(escalamientoService.escalar(enConsulta).block(ESPERA)).isFalse();
            assertThat(escalamientoService.escalar(dadoDeAlta).block(ESPERA)).isFalse();
            assertThat(contar("paciente WHERE escalado")).isZero();
            assertThat(contar("alerta_medica")).isZero();
            assertThat(contar("outbox_evento WHERE tipo_evento = 'paciente_escalado'")).isZero();
        }

        @Test
        @DisplayName("un paciente escalado puede pasar a consulta y conserva el flag")
        void shouldAllowConsulta_WhenPacienteWasEscalated() {
            UUID id = crearPaciente(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(6));
            job.ejecutar().block(ESPERA);

            moverPaciente(id, "EN_CONSULTA").expectStatus().isOk()
                    .expectBody().jsonPath("$.estado").isEqualTo("EN_CONSULTA").jsonPath("$.escalado").isEqualTo(true);

            clock.avanzar(Duration.ofHours(1));
            job.ejecutar().block(ESPERA);
            verificarUnSoloEscalamiento(id);
            assertThat(contar("auditoria_clinica WHERE paciente_id = '" + id + "'")).isEqualTo(3);
        }
    }
}
