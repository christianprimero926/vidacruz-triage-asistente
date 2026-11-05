package co.com.vidacruz.asistente.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Proveedor compatible con la API de OpenAI. La clave puede venir vacía: el servicio arranca en modo degradado. */
@Validated
@ConfigurationProperties(prefix = "asistente.llm")
public record LlmProperties(
        String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model,
        @Min(1) int timeoutSegundos,
        @Min(1) int hilos,
        @Min(1) int cola) {

    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }
}
