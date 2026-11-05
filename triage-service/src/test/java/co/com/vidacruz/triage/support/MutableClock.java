package co.com.vidacruz.triage.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Reloj controlable para probar tiempos en estado y vencimientos de SLA sin esperar. */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instante;

    public MutableClock(Instant inicial) {
        this.instante = new AtomicReference<>(inicial);
    }

    public void fijar(Instant nuevo) {
        instante.set(nuevo);
    }

    public void avanzar(Duration duracion) {
        instante.updateAndGet(actual -> actual.plus(duracion));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instante.get();
    }
}
