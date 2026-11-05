package co.com.vidacruz.triage.job;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Outbox con un registro que Kafka rechaza de forma permanente")
class OutboxVenenosoIT extends AbstractIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final int MAX_INTENTOS = 5;

    @Autowired
    private OutboxPublisherJob job;
    @Autowired
    private MeterRegistry meterRegistry;

    /** El payload supera max.request.size (1 MB): el productor lo rechaza con RecordTooLargeException. */
    private UUID insertarVenenoso() {
        UUID id = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO outbox_evento (id, paciente_id, tipo_evento, payload, fecha_creacion)
                VALUES (:id, :paciente, 'paciente_registrado', repeat('x', 2000000), now())""")
                .bind("id", id).bind("paciente", UUID.randomUUID()).then().block(ESPERA);
        return id;
    }

    @Test
    @DisplayName("tras agotar los intentos queda descartado y los demás eventos se publican")
    void shouldDiscardPoisonAndPublishTheRest_WhenKafkaRejectsItPermanently() {
        UUID venenoso = insertarVenenoso();
        crearPaciente(PacienteFixtures.e1());
        crearPaciente(PacienteFixtures.e2());
        double antes = meterRegistry.counter("triage.outbox.descartados").count();

        job.ejecutar().block(ESPERA);

        assertThat(contar("outbox_evento WHERE id = '" + venenoso + "' AND intentos = 1 AND fecha_descarte IS NULL"))
                .isEqualTo(1);
        assertThat(contar("outbox_evento WHERE fecha_publicacion IS NOT NULL")).isZero();

        for (int corrida = 2; corrida <= MAX_INTENTOS; corrida++) {
            job.ejecutar().block(ESPERA);
        }

        assertThat(contar("outbox_evento WHERE id = '" + venenoso + "' AND intentos = " + MAX_INTENTOS
                + " AND fecha_descarte IS NOT NULL AND ultimo_error LIKE 'RecordTooLargeException%'")).isEqualTo(1);
        assertThat(contar("outbox_evento WHERE fecha_publicacion IS NOT NULL")).isEqualTo(2);
        assertThat(meterRegistry.counter("triage.outbox.descartados").count() - antes).isEqualTo(1.0);
        assertThat(meterRegistry.get("triage.outbox.pendientes").gauge().value()).isZero();
    }
}
