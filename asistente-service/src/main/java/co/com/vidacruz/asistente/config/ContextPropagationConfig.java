package co.com.vidacruz.asistente.config;

import static co.com.vidacruz.asistente.utilities.Constants.CLAVE_CORRELACION;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/** Lleva el correlationId del contexto de Reactor al MDC en cada hilo, para que salga en todos los logs. */
@Configuration
public class ContextPropagationConfig {

    @PostConstruct
    void registrarCorrelacionEnMdc() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(CLAVE_CORRELACION));
    }
}
