package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.FakeWebhookServer;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

@TestPropertySource(properties = {
        "triage.alertas.timeout-ms=500",
        "triage.alertas.max-intentos=3",
        "triage.alertas.espera-base-ms=1000"})
@DisplayName("Despacho de alertas (RF-06)")
class AlertaDispatcherJobIT extends AbstractIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final FakeWebhookServer WEBHOOK = new FakeWebhookServer();
    private static final String UTC_PROXIMO =
            "to_char(proximo_intento AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS')";

    @DynamicPropertySource
    static void configurarWebhook(DynamicPropertyRegistry registry) {
        registry.add("triage.alertas.webhook-url", WEBHOOK::url);
    }

    @AfterAll
    static void detenerWebhook() {
        WEBHOOK.detener();
    }

    @Autowired
    private AlertaDispatcherJob dispatcher;
    @Autowired
    private SlaMonitorJob monitor;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry registry;

    private double alertas(String resultado) {
        var contador = registry.find("triage.alertas").tag("resultado", resultado).counter();
        return contador == null ? 0 : contador.count();
    }

    @BeforeEach
    void reiniciarWebhook() {
        WEBHOOK.reiniciar();
    }

    /** Registra un paciente crítico y deja pasar su SLA para que el monitor genere la alerta pendiente. */
    private UUID pacienteEscalado() {
        UUID id = crearPaciente(PacienteFixtures.e1());
        clock.fijar(TestClockConfig.INICIO.plus(Duration.ofMinutes(5)));
        monitor.ejecutar().block(ESPERA);
        return id;
    }

    private String campo(String columna, UUID pacienteId) {
        return databaseClient.sql("SELECT " + columna + "::text FROM alerta_medica WHERE paciente_id = :id")
                .bind("id", pacienteId).map(fila -> String.valueOf(fila.get(0, String.class))).one().block();
    }

    @Test
    @DisplayName("envía al webhook el cuerpo esperado, sin nombre ni queja, y queda ENVIADA")
    void shouldSendExpectedBodyAndMarkSent_WhenWebhookAnswersOk() throws Exception {
        double enviadasAntes = alertas("enviada");
        UUID id = pacienteEscalado();

        dispatcher.ejecutar().block(ESPERA);

        assertThat(WEBHOOK.cuerpos()).hasSize(1);
        assertThat(WEBHOOK.contentTypes().get(0)).startsWith("application/json");
        String cuerpo = WEBHOOK.cuerpos().get(0);
        JsonNode json = objectMapper.readTree(cuerpo);
        assertThat(json.get("text").asText()).isEqualTo(
                "[TRIAGE] Paciente " + id + " nivel CRITICO escalado: superó SLA de 5 min (lleva 5 min)");
        assertThat(json.get("pacienteId").asText()).isEqualTo(id.toString());
        assertThat(json.get("alertaId").asText()).isEqualTo(campo("id", id));
        assertThat(json.get("nivel").asText()).isEqualTo("CRITICO");
        assertThat(json.get("slaMinutos").asInt()).isEqualTo(5);
        assertThat(json.get("minutosEspera").asInt()).isEqualTo(5);
        assertThat(json.get("fechaEscalamiento").asText()).isEqualTo("2026-11-05T15:05:00Z");
        assertThat(cuerpo).doesNotContain("Paciente de Prueba").doesNotContain("Dolor tor");

        assertThat(campo("estado", id)).isEqualTo("ENVIADA");
        assertThat(alertas("enviada")).isEqualTo(enviadasAntes + 1);
        assertThat(campo("intentos", id)).isEqualTo("1");
        assertThat(campo("to_char(fecha_envio AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS')", id)).isEqualTo("2026-11-05 15:05:00");
    }

    @Test
    @DisplayName("ante un 500 la alerta sigue pendiente con espera exponencial y luego se envía")
    void shouldRetryAfterBackoff_WhenWebhookFailsOnce() {
        double reintentosAntes = alertas("reintento");
        UUID id = pacienteEscalado();
        WEBHOOK.responder(500, 0);

        dispatcher.ejecutar().block(ESPERA);

        assertThat(campo("estado", id)).isEqualTo("PENDIENTE");
        assertThat(campo("intentos", id)).isEqualTo("1");
        assertThat(campo("ultimo_error", id)).isEqualTo("HTTP 500");
        assertThat(campo(UTC_PROXIMO, id)).isEqualTo("2026-11-05 15:05:01");
        assertThat(alertas("reintento")).isEqualTo(reintentosAntes + 1);

        WEBHOOK.responder(200, 0);
        dispatcher.ejecutar().block(ESPERA);
        assertThat(WEBHOOK.cuerpos()).hasSize(1);

        clock.avanzar(Duration.ofSeconds(1));
        dispatcher.ejecutar().block(ESPERA);

        assertThat(WEBHOOK.cuerpos()).hasSize(2);
        assertThat(campo("estado", id)).isEqualTo("ENVIADA");
        assertThat(campo("intentos", id)).isEqualTo("2");
        assertThat(campo("ultimo_error", id)).isEqualTo("null");
    }

    @Test
    @DisplayName("con 500 permanente pasa a FALLIDA al agotar los intentos y ya no se vuelve a enviar")
    void shouldFailAfterMaxAttempts_WhenWebhookAlwaysFails() {
        double fallidasAntes = alertas("fallida");
        UUID id = pacienteEscalado();
        WEBHOOK.responder(500, 0);

        dispatcher.ejecutar().block(ESPERA);
        clock.avanzar(Duration.ofSeconds(1));
        dispatcher.ejecutar().block(ESPERA);
        assertThat(campo(UTC_PROXIMO, id)).isEqualTo("2026-11-05 15:05:03");
        clock.avanzar(Duration.ofSeconds(2));
        dispatcher.ejecutar().block(ESPERA);

        assertThat(campo("estado", id)).isEqualTo("FALLIDA");
        assertThat(alertas("fallida")).isEqualTo(fallidasAntes + 1);
        assertThat(campo("intentos", id)).isEqualTo("3");
        assertThat(campo("ultimo_error", id)).isEqualTo("HTTP 500");
        assertThat(WEBHOOK.cuerpos()).hasSize(3);

        clock.avanzar(Duration.ofHours(1));
        dispatcher.ejecutar().block(ESPERA);
        assertThat(WEBHOOK.cuerpos()).hasSize(3);
    }

    @Test
    @DisplayName("un webhook más lento que el timeout cuenta como fallo y se reintenta")
    void shouldTreatSlowWebhookAsFailure_WhenTimeoutExpires() {
        UUID id = pacienteEscalado();
        WEBHOOK.responder(200, 2000);

        dispatcher.ejecutar().block(ESPERA);

        assertThat(campo("estado", id)).isEqualTo("PENDIENTE");
        assertThat(campo("ultimo_error", id)).isEqualTo("timeout tras 500 ms");
    }

    @Test
    @DisplayName("dos despachadores simultáneos no envían dos veces la misma alerta")
    void shouldNotSendTwice_WhenTwoDispatchersRunConcurrently() {
        for (int i = 0; i < 4; i++) {
            crearPaciente(PacienteFixtures.e1());
        }
        clock.fijar(TestClockConfig.INICIO.plus(Duration.ofMinutes(5)));
        monitor.ejecutar().block(ESPERA);
        WEBHOOK.responder(200, 300);

        Mono.when(dispatcher.ejecutar(), dispatcher.ejecutar()).block(ESPERA);

        List<String> cuerpos = WEBHOOK.cuerpos();
        assertThat(cuerpos).hasSize(4);
        assertThat(cuerpos.stream().map(this::alertaId).distinct()).hasSize(4);
        assertThat(contar("alerta_medica WHERE estado = 'ENVIADA' AND intentos = 1")).isEqualTo(4);
    }

    private String alertaId(String cuerpo) {
        try {
            return objectMapper.readTree(cuerpo).get("alertaId").asText();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
