package co.com.vidacruz.triage.service;

import static co.com.vidacruz.triage.utilities.Constants.METRICA_ALERTAS;
import static co.com.vidacruz.triage.utilities.Constants.METRICA_CLASIFICACION;
import static co.com.vidacruz.triage.utilities.Constants.METRICA_ESCALAMIENTOS;
import static co.com.vidacruz.triage.utilities.Constants.TAG_NIVEL;
import static co.com.vidacruz.triage.utilities.Constants.TAG_RESULTADO;

import co.com.vidacruz.triage.domain.NivelTriage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Los contadores se invocan después del commit, desde fuera de los métodos transaccionales. */
@Component
@RequiredArgsConstructor
public class MetricasTriage {

    private final MeterRegistry registry;

    public void clasificacion(NivelTriage nivel) {
        registry.counter(METRICA_CLASIFICACION, TAG_NIVEL, nivel.name()).increment();
    }

    public void escalamiento(NivelTriage nivel) {
        registry.counter(METRICA_ESCALAMIENTOS, TAG_NIVEL, nivel.name()).increment();
    }

    /** resultado: enviada, reintento o fallida. */
    public void alerta(String resultado) {
        registry.counter(METRICA_ALERTAS, TAG_RESULTADO, resultado).increment();
    }
}
