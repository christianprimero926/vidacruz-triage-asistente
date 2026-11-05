package co.com.vidacruz.triage.service;

import co.com.vidacruz.triage.config.TriageProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Estrategia de reintento de alertas: espera exponencial con tope y un máximo de intentos. */
@Component
public class PoliticaReintento {

    private static final Duration TOPE = Duration.ofHours(1);
    private static final int EXPONENTE_MAXIMO = 20;

    private final long esperaBaseMs;
    private final int maxIntentos;

    public PoliticaReintento(TriageProperties properties) {
        this.esperaBaseMs = properties.getAlertas().getEsperaBaseMs();
        this.maxIntentos = properties.getAlertas().getMaxIntentos();
    }

    public boolean agotada(int intentos) {
        return intentos >= maxIntentos;
    }

    /** Espera tras el fallo del intento número dado: base, 2 x base, 4 x base... */
    public Duration espera(int intentos) {
        int exponente = Math.min(Math.max(intentos - 1, 0), EXPONENTE_MAXIMO);
        Duration espera = Duration.ofMillis(esperaBaseMs << exponente);
        return espera.compareTo(TOPE) > 0 ? TOPE : espera;
    }
}
