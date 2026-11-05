package co.com.vidacruz.triage.utilities;

import static co.com.vidacruz.triage.utilities.Constants.CORRELATION_ID_KEY;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import reactor.util.context.ContextView;

/** Lee del contexto de Reactor la correlación y la traza de la petición que originó el trabajo. */
@UtilityClass
public class CorrelationContext {

    public static Optional<String> correlationId(ContextView contexto) {
        return contexto.<String>getOrEmpty(CORRELATION_ID_KEY);
    }

    /** Formato W3C traceparent; vacío si la traza no está activa en la petición. */
    public static Optional<String> traceparent(ContextView contexto) {
        return contexto.<Observation>getOrEmpty(ObservationThreadLocalAccessor.KEY)
                .map(CorrelationContext::contextoDeTraza)
                .filter(contextoSpan -> !contextoSpan.traceId().isBlank() && !contextoSpan.spanId().isBlank())
                .map(CorrelationContext::formatear);
    }

    private static TraceContext contextoDeTraza(Observation observacion) {
        TracingContext tracing = observacion.getContextView().get(TracingContext.class);
        Span span = tracing == null ? null : tracing.getSpan();
        return span == null ? Span.NOOP.context() : span.context();
    }

    private static String formatear(TraceContext contextoSpan) {
        String banderas = Boolean.TRUE.equals(contextoSpan.sampled()) ? "01" : "00";
        return "00-" + contextoSpan.traceId() + "-" + contextoSpan.spanId() + "-" + banderas;
    }
}
