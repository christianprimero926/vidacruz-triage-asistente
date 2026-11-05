package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.job.OutboxPublisherJob;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.KafkaTestConsumer;
import co.com.vidacruz.triage.support.PacienteFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"management.tracing.enabled=true", "management.otlp.tracing.export.enabled=false"})
@DisplayName("Correlación y traceparent de punta a punta")
class CorrelacionTrazasIT extends AbstractIT {

    private static final String FORMATO_TRACEPARENT = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]";

    @Autowired
    private OutboxPublisherJob publicador;
    @Autowired
    private ObjectMapper objectMapper;

    private String consulta(String sql) {
        return databaseClient.sql(sql).map(fila -> String.valueOf(fila.get(0, String.class))).one().block();
    }

    private UUID registrarConCorrelacion(String correlacion) {
        webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlacion)
                .bodyValue(PacienteFixtures.e1())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody().returnResult();
        return unicoPaciente();
    }

    private UUID unicoPaciente() {
        return UUID.fromString(consulta("SELECT id::text FROM paciente"));
    }

    @Test
    @DisplayName("la correlación recibida llega a la auditoría, al outbox, al evento y a la cabecera de Kafka")
    void shouldPropagateCorrelationEverywhere_WhenRequestCarriesIt() throws Exception {
        UUID id = registrarConCorrelacion("pedido-12345678");

        assertThat(consulta("SELECT correlation_id FROM auditoria_clinica")).isEqualTo("pedido-12345678");
        assertThat(consulta("SELECT correlation_id FROM outbox_evento")).isEqualTo("pedido-12345678");
        assertThat(objectMapper.readTree(consulta("SELECT payload FROM outbox_evento")).get("correlationId").asText())
                .isEqualTo("pedido-12345678");

        String traceparent = consulta("SELECT traceparent FROM outbox_evento");
        assertThat(traceparent).matches(FORMATO_TRACEPARENT);

        publicador.ejecutar().block(Duration.ofSeconds(30));
        try (KafkaTestConsumer consumidor = new KafkaTestConsumer(bootstrapServers(), TOPIC)) {
            List<ConsumerRecord<String, String>> registros =
                    consumidor.leer(List.of(id), 1, Duration.ofSeconds(30), Duration.ofMillis(300));
            assertThat(registros).hasSize(1);
            var cabeceras = registros.get(0).headers();
            assertThat(new String(cabeceras.lastHeader("correlation-id").value(), StandardCharsets.UTF_8))
                    .isEqualTo("pedido-12345678");
            assertThat(new String(cabeceras.lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                    .isEqualTo(traceparent);
        }
    }

    @Test
    @DisplayName("un cambio de estado usa la correlación de su propia petición")
    void shouldUseTheCorrelationOfTheStateChangeRequest_WhenPatching() {
        UUID id = registrarConCorrelacion("registro-1234567");

        webTestClient.patch().uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "cambio-12345678")
                .bodyValue("{\"estado\":\"EN_CONSULTA\"}")
                .exchange().expectStatus().isOk();

        assertThat(consulta("SELECT correlation_id FROM auditoria_clinica WHERE tipo_evento = 'paciente_en_consulta'"))
                .isEqualTo("cambio-12345678");
        assertThat(consulta("SELECT correlation_id FROM outbox_evento WHERE tipo_evento = 'paciente_en_consulta'"))
                .isEqualTo("cambio-12345678");
    }

    @Test
    @DisplayName("una correlación con formato inválido se reemplaza por una generada y esa es la que se guarda")
    void shouldReplaceInvalidCorrelation_WhenFormatIsWrong() {
        String generada = webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "corta")
                .bodyValue(PacienteFixtures.e4())
                .exchange()
                .expectStatus().isCreated()
                .expectBody().returnResult().getResponseHeaders().getFirst("X-Correlation-Id");

        assertThat(generada).isNotEqualTo("corta").matches("[0-9a-f-]{36}");
        assertThat(consulta("SELECT correlation_id FROM auditoria_clinica")).isEqualTo(generada);
    }

    @Test
    @DisplayName("un error de validación devuelve la misma correlación en el cuerpo y en la cabecera")
    void shouldReturnCorrelationInErrorBody_WhenValidationFails() {
        webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "error-12345678")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals("X-Correlation-Id", "error-12345678")
                .expectBody().jsonPath("$.correlationId").isEqualTo("error-12345678");
    }
}
