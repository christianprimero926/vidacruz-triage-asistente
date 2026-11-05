package co.com.vidacruz.asistente.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Sin API key configurada el servicio no arranca: no existe un modo abierto por omisión (fail-closed). */
@Validated
@ConfigurationProperties(prefix = "asistente.seguridad")
public record SeguridadProperties(@NotBlank String apiKey) {
}
