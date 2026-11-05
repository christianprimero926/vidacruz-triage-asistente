package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.triage.config.TriageProperties;
import co.com.vidacruz.triage.domain.OutboxEvento;
import co.com.vidacruz.triage.repository.OutboxRepository;
import co.com.vidacruz.triage.service.MetricasTriage;
import co.com.vidacruz.triage.service.OutboxEmisor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisherJob: registros venenosos")
class OutboxPublisherJobTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:10:00Z");

    @Mock
    private OutboxRepository repository;
    @Mock
    private OutboxEmisor emisor;
    @Mock
    private TransactionalOperator transaccion;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OutboxPublisherJob job;
    private final OutboxEvento venenoso = evento();
    private final OutboxEvento sano = evento();

    @BeforeEach
    void setUp() {
        var properties = new TriageProperties();
        job = new OutboxPublisherJob(repository, emisor, transaccion,
                new ContextoJobs(properties, Clock.fixed(AHORA, ZoneOffset.UTC), new MetricasTriage(registry)));
        when(transaccion.transactional(any(Mono.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(repository.obtenerLockDePublicacion()).thenReturn(Mono.just(true));
        when(repository.pendientes(anyInt())).thenReturn(Flux.just(venenoso, sano));
        when(repository.contarPendientes()).thenReturn(Mono.just(0L));
    }

    private static OutboxEvento evento() {
        return OutboxEvento.builder().id(UUID.randomUUID()).pacienteId(UUID.randomUUID())
                .tipoEvento("paciente_registrado").payload("{}").build();
    }

    private void correr() {
        StepVerifier.create(job.ejecutar()).expectComplete().verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("al agotar los intentos descarta el venenoso, publica el resto y cuenta el descarte")
    void shouldDiscardPoisonAndPublishTheRest_WhenAttemptsAreExhausted() {
        when(emisor.enviar(venenoso)).thenReturn(Mono.error(new RecordTooLargeException("grande")));
        when(emisor.enviar(sano)).thenReturn(Mono.empty());
        when(repository.registrarRechazoPermanente(eq(venenoso.getId()), any(), eq(5), eq(AHORA)))
                .thenReturn(Mono.just(true));
        when(repository.marcarPublicados(any(), any())).thenReturn(Mono.just(1L));

        correr();

        ArgumentCaptor<List<UUID>> publicados = ArgumentCaptor.forClass(List.class);
        verify(repository).marcarPublicados(publicados.capture(), eq(AHORA));
        assertThat(publicados.getValue()).containsExactly(sano.getId());
        assertThat(registry.counter("triage.outbox.descartados").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("mientras el venenoso no agote sus intentos, el lote espera para no adelantarlo")
    void shouldHoldTheBatch_WhenPoisonStillHasAttempts() {
        when(emisor.enviar(venenoso)).thenReturn(Mono.error(new RecordTooLargeException("grande")));
        when(repository.registrarRechazoPermanente(any(), any(), anyInt(), any())).thenReturn(Mono.just(false));

        correr();

        verify(emisor, never()).enviar(sano);
        verify(repository, never()).marcarPublicados(any(), any());
        assertThat(registry.counter("triage.outbox.descartados").count()).isZero();
    }

    @Test
    @DisplayName("un error reintentable no cuenta intentos ni descarta nada")
    void shouldNotCountAttempts_WhenKafkaErrorIsRetriable() {
        when(emisor.enviar(venenoso)).thenReturn(Mono.error(new TimeoutException("broker caído")));

        correr();
        when(emisor.enviar(venenoso)).thenReturn(Mono.error(new NetworkException("red")));
        correr();

        verify(repository, never()).registrarRechazoPermanente(any(), any(), anyInt(), any());
        verify(emisor, never()).enviar(sano);
        assertThat(registry.counter("triage.outbox.descartados").count()).isZero();
    }
}
