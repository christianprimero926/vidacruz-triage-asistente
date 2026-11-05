package co.com.vidacruz.triage.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.service.MetricasTriage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.service.EscalamientoService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlaMonitorJob")
class SlaMonitorJobTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:10:00Z");

    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private EscalamientoService escalamientoService;

    private SlaMonitorJob job() {
        var properties = new TriageProperties();
        properties.getJobs().setSlaLote(7);
        return new SlaMonitorJob(pacienteRepository, escalamientoService,
                new ContextoJobs(properties, Clock.fixed(AHORA, ZoneOffset.UTC),
                        new MetricasTriage(new SimpleMeterRegistry())));
    }

    @Test
    @DisplayName("escala a cada paciente vencido que le devuelve la consulta, con el lote y la hora configurados")
    void shouldEscalateEveryExpiredPaciente_WhenRunning() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(pacienteRepository.findIdsConSlaVencido(AHORA, 7)).thenReturn(Flux.just(a, b));
        when(escalamientoService.escalar(a)).thenReturn(Mono.just(NivelTriage.CRITICO));
        when(escalamientoService.escalar(b)).thenReturn(Mono.just(NivelTriage.CRITICO));

        StepVerifier.create(job().ejecutar()).expectComplete().verify(Duration.ofSeconds(2));

        verify(escalamientoService).escalar(a);
        verify(escalamientoService).escalar(b);
    }

    @Test
    @DisplayName("el error de un paciente no detiene al resto")
    void shouldContinueWithTheRest_WhenOnePacienteFails() {
        UUID falla = UUID.randomUUID();
        UUID sigue = UUID.randomUUID();
        when(pacienteRepository.findIdsConSlaVencido(any(Instant.class), anyInt())).thenReturn(Flux.just(falla, sigue));
        when(escalamientoService.escalar(falla)).thenReturn(Mono.error(new IllegalStateException("falla")));
        when(escalamientoService.escalar(sigue)).thenReturn(Mono.just(NivelTriage.CRITICO));

        StepVerifier.create(job().ejecutar()).expectComplete().verify(Duration.ofSeconds(2));

        verify(escalamientoService).escalar(sigue);
    }
}
