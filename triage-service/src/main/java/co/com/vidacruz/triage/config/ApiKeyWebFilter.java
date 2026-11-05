package co.com.vidacruz.triage.config;

import static co.com.vidacruz.triage.utilities.Constants.CODIGO_NO_AUTORIZADO;
import static co.com.vidacruz.triage.utilities.Constants.HEADER_API_KEY;
import static co.com.vidacruz.triage.utilities.Constants.MSG_NO_AUTORIZADO;
import static co.com.vidacruz.triage.utilities.Constants.RUTAS_PUBLICAS;

import co.com.vidacruz.triage.exception.ErrorResponseFactory;
import co.com.vidacruz.triage.utilities.LogSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/** Autenticación por API key. Todo lo que no sea una ruta pública exige la cabecera X-API-Key. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyWebFilter implements WebFilter {

    private final byte[] claveEsperada;
    private final List<PathPattern> rutasPublicas;
    private final ErrorResponseFactory errorFactory;
    private final ObjectMapper objectMapper;

    public ApiKeyWebFilter(ApiKeyProperties properties, ErrorResponseFactory errorFactory, ObjectMapper objectMapper) {
        this.claveEsperada = properties.getApiKey().getBytes(StandardCharsets.UTF_8);
        PathPatternParser parser = new PathPatternParser();
        this.rutasPublicas = RUTAS_PUBLICAS.stream().map(parser::parse).toList();
        this.errorFactory = errorFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (esPublica(exchange) || claveValida(exchange.getRequest().getHeaders().getFirst(HEADER_API_KEY))) {
            return chain.filter(exchange);
        }
        log.warn("Acceso rechazado sin API key válida a {}", LogSanitizer.sanear(exchange.getRequest().getPath().value()));
        return rechazar(exchange);
    }

    private boolean esPublica(ServerWebExchange exchange) {
        PathContainer ruta = exchange.getRequest().getPath().pathWithinApplication();
        return rutasPublicas.stream().anyMatch(patron -> patron.matches(ruta));
    }

    /** Comparación de tiempo constante para no filtrar la clave por diferencias de tiempo de respuesta. */
    private boolean claveValida(String recibida) {
        return recibida != null && MessageDigest.isEqual(recibida.getBytes(StandardCharsets.UTF_8), claveEsperada);
    }

    private Mono<Void> rechazar(ServerWebExchange exchange) {
        var cuerpo = errorFactory.crear(HttpStatus.UNAUTHORIZED, CODIGO_NO_AUTORIZADO, MSG_NO_AUTORIZADO, null, exchange);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(cuerpo);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException error) {
            return Mono.error(error);
        }
    }
}
