package co.com.vidacruz.asistente.config;

import static co.com.vidacruz.asistente.utilities.Constants.COD_NO_AUTORIZADO;
import static co.com.vidacruz.asistente.utilities.Constants.HEADER_API_KEY;
import static co.com.vidacruz.asistente.utilities.Constants.MSG_NO_AUTORIZADO;
import static co.com.vidacruz.asistente.utilities.Constants.RUTAS_PUBLICAS;

import co.com.vidacruz.asistente.dto.response.ErrorResponseDTO;
import co.com.vidacruz.asistente.utilities.Saneador;
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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/** Exige X-API-Key en todo lo que no sea sonda de salud o documentación; corre justo después de la correlación. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiKeyWebFilter implements WebFilter {

    private final byte[] claveEsperada;
    private final ObjectMapper objectMapper;
    private final ErrorResponseFactory errorResponseFactory;
    private final List<PathPattern> rutasPublicas;

    public ApiKeyWebFilter(SeguridadProperties properties, ObjectMapper objectMapper,
                           ErrorResponseFactory errorResponseFactory) {
        this.claveEsperada = properties.apiKey().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.errorResponseFactory = errorResponseFactory;
        this.rutasPublicas = RUTAS_PUBLICAS.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (esPublica(exchange) || claveValida(exchange.getRequest().getHeaders().getFirst(HEADER_API_KEY))) {
            return chain.filter(exchange);
        }
        log.warn("Solicitud rechazada por API key ausente o inválida: {}",
                Saneador.limpiar(exchange.getRequest().getPath().value()));
        return rechazar(exchange);
    }

    private boolean esPublica(ServerWebExchange exchange) {
        PathContainer ruta = exchange.getRequest().getPath().pathWithinApplication();
        return rutasPublicas.stream().anyMatch(patron -> patron.matches(ruta));
    }

    /** Comparación en tiempo constante para no filtrar la clave por diferencias de tiempo. */
    private boolean claveValida(String recibida) {
        return recibida != null && MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> rechazar(ServerWebExchange exchange) {
        ServerHttpResponse respuesta = exchange.getResponse();
        respuesta.setStatusCode(HttpStatus.UNAUTHORIZED);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponseDTO cuerpo = errorResponseFactory.crear(exchange, HttpStatus.UNAUTHORIZED, COD_NO_AUTORIZADO,
                MSG_NO_AUTORIZADO, null);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(cuerpo);
            return respuesta.writeWith(Mono.just(respuesta.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return respuesta.setComplete();
        }
    }
}
