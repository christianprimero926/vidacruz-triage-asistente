package co.com.vidacruz.asistente.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Fracción mínima de las raíces léxicas de la respuesta que debe estar respaldada por las fuentes citadas. */
@Validated
@ConfigurationProperties(prefix = "asistente.validacion")
public record ValidacionProperties(@DecimalMin("0.0") @DecimalMax("1.0") double coberturaMinima) {
}
