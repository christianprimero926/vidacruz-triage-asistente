package co.com.vidacruz.asistente.config;

import static co.com.vidacruz.asistente.utilities.Constants.HEADER_CORRELACION;

import co.com.vidacruz.asistente.utilities.Correlacion;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Asigna el correlationId de la solicitud. Solo se acepta el que envía el cliente si tiene un formato seguro,
 * para que no pueda inyectar saltos de línea ni contenido arbitrario en los logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String recibido = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELACION);
        String correlationId = Correlacion.esValido(recibido)
                ? recibido
                : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER_CORRELACION, correlationId);
        return chain.filter(exchange).contextWrite(contexto -> Correlacion.enContexto(contexto, correlationId));
    }
}
