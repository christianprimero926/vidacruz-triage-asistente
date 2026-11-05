package co.com.vidacruz.triage.job;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.service.MetricasTriage;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Lo que comparten todos los procesos de fondo: configuración, reloj y métricas. */
@Component
public record ContextoJobs(TriageProperties properties, Clock clock, MetricasTriage metricas) {

    public TriageProperties.Jobs jobs() {
        return properties.getJobs();
    }
}
