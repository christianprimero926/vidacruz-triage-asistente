package co.com.vidacruz.triage.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.job.SlaMonitorJob;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("Métricas de negocio contadas tras el commit")
class MetricasIT extends AbstractIT {

    @Autowired
    private MeterRegistry registry;
    @Autowired
    private SlaMonitorJob monitor;

    private double contador(String metrica, String tag, String valor) {
        var encontrado = registry.find(metrica).tag(tag, valor).counter();
        return encontrado == null ? 0 : encontrado.count();
    }

    @Test
    @DisplayName("cada registro suma a triage.clasificacion del nivel asignado")
    void shouldCountClassificationByLevel_WhenPatientsAreRegistered() {
        double criticos = contador("triage.clasificacion", "nivel", "CRITICO");
        double administrativos = contador("triage.clasificacion", "nivel", "NO_URGENTE_2");

        crearPaciente(PacienteFixtures.e1());
        crearPaciente(PacienteFixtures.e6());
        crearPaciente(PacienteFixtures.e5());

        assertThat(contador("triage.clasificacion", "nivel", "CRITICO")).isEqualTo(criticos + 2);
        assertThat(contador("triage.clasificacion", "nivel", "NO_URGENTE_2")).isEqualTo(administrativos + 1);
    }

    @Test
    @DisplayName("un registro inválido no cuenta")
    void shouldNotCount_WhenValidationFails() {
        double antes = contador("triage.clasificacion", "nivel", "CRITICO");

        webTestClient.post().uri("/triage/pacientes").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PacienteFixtures.base().frecuenciaCardiaca(10).build())
                .exchange().expectStatus().isBadRequest();

        assertThat(contador("triage.clasificacion", "nivel", "CRITICO")).isEqualTo(antes);
    }

    @Test
    @DisplayName("si la transacción se revierte no se cuenta la clasificación")
    void shouldNotCount_WhenTransactionRollsBack() {
        double antes = contador("triage.clasificacion", "nivel", "CRITICO");
        databaseClient.sql("CREATE FUNCTION falla_metricas() RETURNS TRIGGER AS 'BEGIN RAISE EXCEPTION ''fallo''; END;' "
                + "LANGUAGE plpgsql").then().block();
        databaseClient.sql("CREATE TRIGGER tr_falla_metricas BEFORE INSERT ON outbox_evento "
                + "FOR EACH ROW EXECUTE FUNCTION falla_metricas()").then().block();
        try {
            webTestClient.post().uri("/triage/pacientes").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(PacienteFixtures.e1())
                    .exchange().expectStatus().is5xxServerError();
        } finally {
            databaseClient.sql("DROP TRIGGER tr_falla_metricas ON outbox_evento").then().block();
            databaseClient.sql("DROP FUNCTION falla_metricas()").then().block();
        }

        assertThat(contador("triage.clasificacion", "nivel", "CRITICO")).isEqualTo(antes);
        assertThat(contar("paciente")).isZero();
    }

    @Test
    @DisplayName("el escalamiento suma una sola vez a triage.escalamientos del nivel")
    void shouldCountEscalationOnce_WhenMonitorRunsRepeatedly() {
        double antes = contador("triage.escalamientos", "nivel", "CRITICO");
        crearPaciente(PacienteFixtures.e1());
        clock.avanzar(Duration.ofMinutes(6));

        monitor.ejecutar().block(Duration.ofSeconds(30));
        monitor.ejecutar().block(Duration.ofSeconds(30));

        assertThat(contador("triage.escalamientos", "nivel", "CRITICO")).isEqualTo(antes + 1);
    }
}
