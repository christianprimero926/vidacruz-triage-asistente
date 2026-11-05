package co.com.vidacruz.triage.config;

import static co.com.vidacruz.triage.utilities.Constants.CORRELATION_ID_KEY;
import static co.com.vidacruz.triage.utilities.Constants.HEADER_CORRELATION_ID;
import static co.com.vidacruz.triage.utilities.Constants.PATRON_CORRELATION_ID;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Toma la correlación de la cabecera solo si tiene el formato esperado (evita inyectar texto arbitrario en los
 * logs); si no, genera una. La devuelve en la respuesta y la deja en el contexto de Reactor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    private static final Pattern FORMATO_VALIDO = Pattern.compile(PATRON_CORRELATION_ID);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolver(exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID));
        exchange.getResponse().getHeaders().set(HEADER_CORRELATION_ID, correlationId);
        return chain.filter(exchange).contextWrite(contexto -> contexto.put(CORRELATION_ID_KEY, correlationId));
    }

    private String resolver(String recibido) {
        return recibido != null && FORMATO_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
    }
}
