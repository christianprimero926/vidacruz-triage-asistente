package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.AlertaReclamada;
import co.com.vidacruz.triage.domain.NivelTriage;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SQL del despacho. Cada sentencia confirma por su cuenta: el reclamo no mantiene una transacción abierta
 * mientras se espera la respuesta HTTP del canal.
 */
@Repository
@RequiredArgsConstructor
public class AlertaDespachoRepository {

    private static final int LARGO_MAXIMO_ERROR = 500;

    /** Un reclamo adelanta proximo_intento: así otro despachador no la ve y, si este muere, se reintenta sola. */
    private static final String RECLAMAR = """
            WITH reclamadas AS (
                UPDATE alerta_medica
                SET proximo_intento = :vencimiento, intentos = intentos + 1
                WHERE id IN (
                    SELECT id FROM alerta_medica
                    WHERE estado = 'PENDIENTE' AND proximo_intento <= :ahora
                    ORDER BY proximo_intento
                    LIMIT :lote
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, paciente_id, nivel, minutos_espera, intentos, fecha_creacion
            )
            SELECT r.id, r.paciente_id, r.nivel, r.minutos_espera, r.intentos, r.fecha_creacion, p.sla_minutos
            FROM reclamadas r
            JOIN paciente p ON p.id = r.paciente_id
            ORDER BY r.fecha_creacion
            """;

    private final DatabaseClient databaseClient;

    public Flux<AlertaReclamada> reclamar(Instant ahora, Instant vencimiento, int lote) {
        return databaseClient.sql(RECLAMAR)
                .bind("ahora", ahora)
                .bind("vencimiento", vencimiento)
                .bind("lote", lote)
                .map((fila, metadata) -> aReclamada(fila))
                .all();
    }

    public Mono<Long> marcarEnviada(UUID id, Instant fechaEnvio) {
        return databaseClient.sql("""
                        UPDATE alerta_medica SET estado = 'ENVIADA', fecha_envio = :fecha, ultimo_error = NULL
                        WHERE id = :id AND estado = 'PENDIENTE'""")
                .bind("fecha", fechaEnvio)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Mono<Long> reprogramar(UUID id, Instant proximoIntento, String error) {
        return databaseClient.sql("""
                        UPDATE alerta_medica SET proximo_intento = :proximo, ultimo_error = :error
                        WHERE id = :id AND estado = 'PENDIENTE'""")
                .bind("proximo", proximoIntento)
                .bind("error", recortar(error))
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Mono<Long> marcarFallida(UUID id, String error) {
        return databaseClient.sql("""
                        UPDATE alerta_medica SET estado = 'FALLIDA', ultimo_error = :error
                        WHERE id = :id AND estado = 'PENDIENTE'""")
                .bind("error", recortar(error))
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    private String recortar(String error) {
        return error.length() > LARGO_MAXIMO_ERROR ? error.substring(0, LARGO_MAXIMO_ERROR) : error;
    }

    private AlertaReclamada aReclamada(Row fila) {
        return new AlertaReclamada(
                fila.get("id", UUID.class),
                fila.get("paciente_id", UUID.class),
                NivelTriage.valueOf(fila.get("nivel", String.class)),
                fila.get("minutos_espera", Integer.class),
                fila.get("intentos", Integer.class),
                fila.get("sla_minutos", Integer.class),
                fila.get("fecha_creacion", Instant.class));
    }
}
