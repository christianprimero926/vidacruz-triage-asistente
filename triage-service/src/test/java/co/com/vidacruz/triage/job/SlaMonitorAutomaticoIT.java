package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/** SLA e intervalo salen de propiedades; el contexto se cierra después para que el job no actúe en otras pruebas. */
@DirtiesContext
@TestPropertySource(properties = {
        "triage.jobs.enabled=true",
        "triage.jobs.sla-intervalo-ms=100",
        "triage.sla.critico-minutos=1"})
@DisplayName("Monitor de SLA configurable")
class SlaMonitorAutomaticoIT extends AbstractIT {

    @Test
    @DisplayName("con el SLA y el intervalo configurados escala solo, sin llamar a ejecutar()")
    void shouldEscalateAutomatically_WhenConfiguredSlaExpires() {
        UUID id = crearPaciente(PacienteFixtures.e1());
        webTestClient.get().uri("/triage/pacientes/{id}", id).exchange()
                .expectBody().jsonPath("$.slaMinutos").isEqualTo(1);

        clock.avanzar(Duration.ofMinutes(1));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(contar("paciente WHERE escalado AND id = '" + id + "'")).isEqualTo(1));
        assertThat(contar("alerta_medica")).isEqualTo(1);
        assertThat(databaseClient.sql("SELECT correlation_id FROM auditoria_clinica WHERE tipo_evento = 'paciente_escalado'")
                .map(fila -> fila.get(0, String.class)).one().block()).matches("[0-9a-f-]{36}");
    }
}
