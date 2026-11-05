package co.com.vidacruz.triage.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Obligatoria: sin TRIAGE_API_KEY la aplicación no arranca (fail-closed), nunca queda abierta por omisión. */
@Data
@Validated
@ConfigurationProperties(prefix = "triage.security")
public class ApiKeyProperties {

    @NotBlank
    private String apiKey;
}
