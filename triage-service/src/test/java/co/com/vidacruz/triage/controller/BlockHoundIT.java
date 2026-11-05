package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.vidacruz.triage.job.AlertaDispatcherJob;
import co.com.vidacruz.triage.job.OutboxPublisherJob;
import co.com.vidacruz.triage.job.SlaMonitorJob;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.FakeWebhookServer;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.WebFilter;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * BlockHound sin lista de excepciones sobre el flujo completo: HTTP, base de datos, Kafka, jobs y webhook.
 * Si algo bloqueara un hilo no bloqueante, la petición o el job fallarían y las aserciones lo delatarían.
 */
@Import(BlockHoundIT.FiltroBloqueante.class)
@TestPropertySource(properties = {"triage.alertas.timeout-ms=2000", "triage.alertas.max-intentos=3"})
@DisplayName("Sin bloqueos en el event loop (BlockHound)")
class BlockHoundIT extends AbstractIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final FakeWebhookServer WEBHOOK = new FakeWebhookServer();

    static {
        BlockHound.install();
    }

    @DynamicPropertySource
    static void configurarWebhook(DynamicPropertyRegistry registry) {
        registry.add("triage.alertas.webhook-url", WEBHOOK::url);
    }

    @AfterAll
    static void detenerWebhook() {
        WEBHOOK.detener();
    }

    /** Solo para el control negativo: bloquea de verdad cuando se pide esa ruta. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FiltroBloqueante {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 20)
        WebFilter bloqueoDeControl() {
            return (exchange, chain) -> {
                if (exchange.getRequest().getPath().value().equals("/triage/control-negativo")) {
                    dormir();
                }
                return chain.filter(exchange);
            };
        }

        private static void dormir() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Autowired
    private SlaMonitorJob monitor;
    @Autowired
    private OutboxPublisherJob publicador;
    @Autowired
    private AlertaDispatcherJob despachador;

    @Test
    @DisplayName("control negativo: un Thread.sleep en un hilo no bloqueante es detectado")
    void shouldDetectBlocking_WhenSleepRunsOnParallelScheduler() {
        assertThatThrownBy(() -> Mono.fromCallable(() -> {
            Thread.sleep(10);
            return 1;
        }).subscribeOn(Schedulers.parallel()).block(ESPERA))
                .hasRootCauseInstanceOf(BlockingOperationError.class);
    }

    @Test
    @DisplayName("control negativo en el flujo HTTP: un filtro que bloquea el event loop responde 500")
    void shouldFailRequest_WhenFilterBlocksTheEventLoop() {
        webTestClient.get().uri("/triage/control-negativo").exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("registro, estado, consulta, auditoría, reporte, alertas, actuator y jobs corren sin bloquear")
    void shouldRunWholeFlowWithoutBlocking_WhenUsingTheApi() {
        UUID id = crearPaciente(PacienteFixtures.e1());
        UUID otro = crearPaciente(PacienteFixtures.e4());
        moverPaciente(otro, "EN_CONSULTA").expectStatus().isOk();
        webTestClient.get().uri("/triage/pacientes/{id}", id).exchange().expectStatus().isOk();
        webTestClient.get().uri("/triage/pacientes/{id}/auditoria", id).exchange().expectStatus().isOk();
        webTestClient.post().uri("/triage/pacientes").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}").exchange().expectStatus().isBadRequest();

        clock.fijar(TestClockConfig.INICIO.plus(Duration.ofMinutes(10)));
        monitor.ejecutar().block(ESPERA);
        publicador.ejecutar().block(ESPERA);
        despachador.ejecutar().block(ESPERA);

        webTestClient.get().uri("/triage/reportes/operacion?desde=2026-11-05T00:00:00Z&hasta=2026-11-06T00:00:00Z")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.filas[0].totalRegistrados").isEqualTo(2);
        webTestClient.get().uri("/triage/alertas").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].estado").isEqualTo("ENVIADA");
        webTestClient.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
        webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();

        assertThat(contar("paciente WHERE escalado")).isEqualTo(1);
        assertThat(contar("outbox_evento WHERE fecha_publicacion IS NOT NULL")).isEqualTo(4);
        assertThat(WEBHOOK.cuerpos()).hasSize(1);
    }
}
