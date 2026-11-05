package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.KafkaTestConsumer;
import co.com.vidacruz.triage.support.PacienteFixtures;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/** Con el job habilitado el evento sale solo; el contexto se cierra después para que no robe eventos a otras pruebas. */
@DirtiesContext
@TestPropertySource(properties = {"triage.jobs.enabled=true", "triage.jobs.outbox-intervalo-ms=100"})
@DisplayName("Publicación automática del outbox")
class OutboxPublicacionAutomaticaIT extends AbstractIT {

    @Test
    @DisplayName("publica sin que nadie llame a ejecutar() cuando el job está habilitado")
    void shouldPublishAutomatically_WhenJobIsEnabled() {
        UUID id = crearPaciente(PacienteFixtures.e3());

        try (KafkaTestConsumer consumidor = new KafkaTestConsumer(bootstrapServers(), TOPIC)) {
            var registros = consumidor.leer(List.of(id), 1, Duration.ofSeconds(30), Duration.ofMillis(500));
            assertThat(registros).hasSize(1);
            assertThat(registros.get(0).key()).isEqualTo(id.toString());
        }
    }
}
