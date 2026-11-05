package co.com.vidacruz.triage.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "triage.kafka")
public class TriageKafkaProperties {

    @NotBlank
    private String bootstrapServers;
    @NotBlank
    private String topic = "triaje.eventos";
    /** Límites cortos: con Kafka caído el publicador falla rápido y reintenta en el siguiente tick. */
    @Min(1)
    private int maxBlockMs = 5000;
    @Min(1)
    private int requestTimeoutMs = 5000;
    @Min(1)
    private int deliveryTimeoutMs = 10000;
}
