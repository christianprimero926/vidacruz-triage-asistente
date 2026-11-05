package co.com.vidacruz.asistente.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "asistente.kafka")
public record EventosKafkaProperties(
        @NotBlank String bootstrapServers,
        @NotBlank String topic,
        @NotBlank String topicDlt,
        @NotBlank String grupo,
        @Min(1) int maxPollRecords,
        boolean consumidorHabilitado) {
}
