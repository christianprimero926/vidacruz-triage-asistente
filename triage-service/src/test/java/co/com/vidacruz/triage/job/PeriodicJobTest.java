package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@DisplayName("PeriodicJob")
class PeriodicJobTest {

    private static final Duration PERIODO = Duration.ofMillis(20);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private PruebaJob job;

    @AfterEach
    void detener() {
        if (job != null) {
            job.stop();
        }
    }

    @Test
    @DisplayName("no arranca si está deshabilitado")
    void shouldNotRun_WhenDisabled() {
        job = new PruebaJob(false, TIMEOUT, ejecucion -> Mono.empty());

        job.start();
        assertThat(job.isRunning()).isFalse();
        await().during(Duration.ofMillis(150)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(job.ejecuciones.get()).isZero());
    }

    @Test
    @DisplayName("ejecuta de forma periódica y se detiene con stop")
    void shouldRunPeriodicallyUntilStopped_WhenEnabled() {
        job = new PruebaJob(true, TIMEOUT, ejecucion -> Mono.empty());

        job.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> job.ejecuciones.get() >= 3);
        job.stop();
        int tras = job.ejecuciones.get();

        assertThat(job.isRunning()).isFalse();
        await().during(Duration.ofMillis(150)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(job.ejecuciones.get()).isLessThanOrEqualTo(tras + 1));
    }

    @Test
    @DisplayName("nunca solapa corridas aunque una tarde más que el periodo")
    void shouldNeverOverlapRuns_WhenARunIsSlowerThanThePeriod() {
        job = new PruebaJob(true, TIMEOUT, ejecucion -> Mono.delay(Duration.ofMillis(100)).then());

        job.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> job.ejecuciones.get() >= 3);

        assertThat(job.maximoSimultaneas.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("un error en una corrida no detiene las siguientes")
    void shouldKeepRunning_WhenARunFails() {
        job = new PruebaJob(true, TIMEOUT, ejecucion -> ejecucion == 1
                ? Mono.error(new IllegalStateException("falla"))
                : Mono.empty());

        job.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> job.ejecuciones.get() >= 3);
        assertThat(job.isRunning()).isTrue();
    }

    @Test
    @DisplayName("una corrida que nunca termina se cancela por timeout y no impide la siguiente")
    void shouldRunNextTick_WhenARunNeverFinishes() {
        job = new PruebaJob(true, Duration.ofMillis(100),
                ejecucion -> ejecucion == 1 ? Mono.never() : Mono.empty());

        job.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> job.ejecuciones.get() >= 3);
        assertThat(job.isRunning()).isTrue();
    }

    private static class PruebaJob extends PeriodicJob {

        private final AtomicInteger ejecuciones = new AtomicInteger();
        private final AtomicInteger simultaneas = new AtomicInteger();
        private final AtomicInteger maximoSimultaneas = new AtomicInteger();
        private final IntFunction<Mono<Void>> comportamiento;

        PruebaJob(boolean habilitado, Duration timeout, IntFunction<Mono<Void>> comportamiento) {
            super("prueba", PERIODO, timeout, habilitado);
            this.comportamiento = comportamiento;
        }

        @Override
        public Mono<Void> ejecutar() {
            int numero = ejecuciones.incrementAndGet();
            int actuales = simultaneas.incrementAndGet();
            maximoSimultaneas.accumulateAndGet(actuales, Math::max);
            return comportamiento.apply(numero).doOnTerminate(simultaneas::decrementAndGet);
        }
    }
}
