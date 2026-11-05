package co.com.vidacruz.triage.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.domain.NivelTriage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MetricasTriage")
class MetricasTriageTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MetricasTriage metricas = new MetricasTriage(registry);

    @Test
    @DisplayName("cuenta clasificaciones y escalamientos por nivel y alertas por resultado")
    void shouldCountByTag_WhenRecording() {
        metricas.clasificacion(NivelTriage.CRITICO);
        metricas.clasificacion(NivelTriage.CRITICO);
        metricas.clasificacion(NivelTriage.URGENTE);
        metricas.escalamiento(NivelTriage.URGENTE_2);
        metricas.alerta("enviada");
        metricas.alerta("fallida");
        metricas.alerta("fallida");

        assertThat(registry.get("triage.clasificacion").tag("nivel", "CRITICO").counter().count()).isEqualTo(2);
        assertThat(registry.get("triage.clasificacion").tag("nivel", "URGENTE").counter().count()).isEqualTo(1);
        assertThat(registry.get("triage.escalamientos").tag("nivel", "URGENTE_2").counter().count()).isEqualTo(1);
        assertThat(registry.get("triage.alertas").tag("resultado", "fallida").counter().count()).isEqualTo(2);
        assertThat(registry.get("triage.alertas").tag("resultado", "enviada").counter().count()).isEqualTo(1);
    }
}
