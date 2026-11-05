package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.job.SlaMonitorJob;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GET /triage/alertas")
class AlertaIT extends AbstractIT {

    @Autowired
    private SlaMonitorJob monitor;

    private UUID escalar(PacienteRequestDTO paciente, Duration despues) {
        UUID id = crearPaciente(paciente);
        clock.fijar(TestClockConfig.INICIO.plus(despues));
        monitor.ejecutar().block(Duration.ofSeconds(30));
        return id;
    }

    @Test
    @DisplayName("lista las más recientes primero y filtra por estado")
    void shouldListNewestFirstAndFilterByState_WhenAlertsExist() {
        UUID critico = escalar(PacienteFixtures.e1(), Duration.ofMinutes(5));
        UUID urgente = escalar(PacienteFixtures.e2(), Duration.ofMinutes(15));
        databaseClient.sql("UPDATE alerta_medica SET estado = 'ENVIADA' WHERE paciente_id = :id")
                .bind("id", critico).then().block();

        webTestClient.get().uri("/triage/alertas").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].pacienteId").isEqualTo(urgente.toString())
                .jsonPath("$[0].estado").isEqualTo("PENDIENTE")
                .jsonPath("$[0].nivel").isEqualTo("URGENTE")
                .jsonPath("$[0].minutosEspera").isEqualTo(10)
                .jsonPath("$[1].pacienteId").isEqualTo(critico.toString());

        webTestClient.get().uri("/triage/alertas?estado=ENVIADA").exchange()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].pacienteId").isEqualTo(critico.toString());
        webTestClient.get().uri("/triage/alertas?estado=FALLIDA").exchange()
                .expectBody().jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("un estado desconocido responde 400 nombrando el campo")
    void shouldRespondBadRequest_WhenStateIsUnknown() {
        webTestClient.get().uri("/triage/alertas?estado=LEIDA").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errores[0].campo").isEqualTo("estado")
                .jsonPath("$.errores[0].mensaje")
                .isEqualTo("El campo 'estado' debe ser uno de: PENDIENTE, ENVIADA, FALLIDA");
    }

    @Test
    @DisplayName("sin alertas devuelve una lista vacía")
    void shouldReturnEmptyList_WhenThereAreNoAlerts() {
        assertThat(contar("alerta_medica")).isZero();

        webTestClient.get().uri("/triage/alertas").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(0);
    }
}
