package co.com.vidacruz.triage.repository;

import static co.com.vidacruz.triage.utilities.Constants.OUTBOX_LOCK_KEY;

import co.com.vidacruz.triage.domain.OutboxEvento;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** SQL a mano porque ordena por una columna (secuencia) que la entidad no mapea. */
@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private static final String SELECT_PENDIENTES = """
            SELECT id, paciente_id, tipo_evento, payload, correlation_id, traceparent, fecha_creacion
            FROM outbox_evento
            WHERE fecha_publicacion IS NULL AND fecha_descarte IS NULL
            ORDER BY secuencia
            LIMIT :lote
            """;

    private static final int LARGO_MAXIMO_ERROR = 500;

    private static final String RECHAZO_PERMANENTE = """
            UPDATE outbox_evento
            SET intentos = intentos + 1,
                ultimo_error = :error,
                fecha_descarte = CASE WHEN intentos + 1 >= :max THEN CAST(:fecha AS TIMESTAMPTZ) END
            WHERE id = :id
            RETURNING fecha_descarte IS NOT NULL
            """;

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    public Mono<OutboxEvento> guardar(OutboxEvento evento) {
        return template.insert(evento);
    }

    /** Lock de la transacción en curso: se libera solo al confirmar o revertir. */
    public Mono<Boolean> obtenerLockDePublicacion() {
        return databaseClient.sql("SELECT pg_try_advisory_xact_lock(:clave)")
                .bind("clave", OUTBOX_LOCK_KEY)
                .map(fila -> fila.get(0, Boolean.class))
                .one();
    }

    public Flux<OutboxEvento> pendientes(int lote) {
        return databaseClient.sql(SELECT_PENDIENTES)
                .bind("lote", lote)
                .map((fila, metadata) -> aEvento(fila))
                .all();
    }

    public Mono<Long> marcarPublicados(List<UUID> ids, Instant fecha) {
        return databaseClient.sql("UPDATE outbox_evento SET fecha_publicacion = :fecha WHERE id = ANY(:ids)")
                .bind("fecha", fecha)
                .bind("ids", ids.toArray(UUID[]::new))
                .fetch()
                .rowsUpdated();
    }

    /** Suma un rechazo permanente y descarta el registro al llegar al máximo; emite true si quedó descartado. */
    public Mono<Boolean> registrarRechazoPermanente(UUID id, String error, int maxIntentos, Instant fecha) {
        return databaseClient.sql(RECHAZO_PERMANENTE)
                .bind("id", id)
                .bind("error", recortar(error))
                .bind("max", maxIntentos)
                .bind("fecha", fecha)
                .map(fila -> fila.get(0, Boolean.class))
                .one();
    }

    private String recortar(String error) {
        return error.length() > LARGO_MAXIMO_ERROR ? error.substring(0, LARGO_MAXIMO_ERROR) : error;
    }

    public Mono<Long> contarPendientes() {
        return databaseClient.sql("SELECT COUNT(*) FROM outbox_evento "
                        + "WHERE fecha_publicacion IS NULL AND fecha_descarte IS NULL")
                .map(fila -> fila.get(0, Long.class))
                .one();
    }

    private OutboxEvento aEvento(Row fila) {
        return OutboxEvento.builder()
                .id(fila.get("id", UUID.class))
                .pacienteId(fila.get("paciente_id", UUID.class))
                .tipoEvento(fila.get("tipo_evento", String.class))
                .payload(fila.get("payload", String.class))
                .correlationId(fila.get("correlation_id", String.class))
                .traceparent(fila.get("traceparent", String.class))
                .fechaCreacion(fila.get("fecha_creacion", Instant.class))
                .build();
    }
}
