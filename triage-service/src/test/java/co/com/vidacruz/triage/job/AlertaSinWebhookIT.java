package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "triage.alertas.webhook-url=")
@DisplayName("Alertas sin webhook configurado")
class AlertaSinWebhookIT extends AbstractIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Autowired
    private AlertaDispatcherJob dispatcher;
    @Autowired
    private SlaMonitorJob monitor;

    @Test
    @DisplayName("pasa directo a FALLIDA con 'webhook no configurado' y sin reintentos")
    void shouldFailImmediately_WhenWebhookIsNotConfigured() {
        UUID id = crearPaciente(PacienteFixtures.e1());
        clock.fijar(TestClockConfig.INICIO.plus(Duration.ofMinutes(5)));
        monitor.ejecutar().block(ESPERA);

        dispatcher.ejecutar().block(ESPERA);

        webTestClient.get().uri("/triage/alertas?estado=FALLIDA").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].pacienteId").isEqualTo(id.toString())
                .jsonPath("$[0].estado").isEqualTo("FALLIDA")
                .jsonPath("$[0].intentos").isEqualTo(1)
                .jsonPath("$[0].ultimoError").isEqualTo("webhook no configurado");
        assertThat(contar("alerta_medica WHERE estado = 'PENDIENTE'")).isZero();
    }
}
