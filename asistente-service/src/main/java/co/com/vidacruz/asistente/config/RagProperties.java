package co.com.vidacruz.asistente.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de la recuperación semántica. El score es la relevancia de LangChain4j, es decir
 * (coseno + 1) / 2, en el rango 0..1. {@code scoreMinimo} recorta el contexto que va al LLM y
 * {@code scoreAlto} deja pasar una pregunta sin coincidencia de términos.
 */
@Validated
@ConfigurationProperties(prefix = "asistente.rag")
public record RagProperties(
        @Min(1) int topK,
        @DecimalMin("0.0") @DecimalMax("1.0") double scoreMinimo,
        @DecimalMin("0.0") @DecimalMax("1.0") double scoreAlto) {
}
