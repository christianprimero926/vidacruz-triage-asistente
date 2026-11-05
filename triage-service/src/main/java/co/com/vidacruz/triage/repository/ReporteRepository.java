package co.com.vidacruz.triage.repository;

import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.dto.response.FilaReporteDTO;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Todo el cálculo ocurre en un único query: ROW_NUMBER para quedarse con la primera consulta de cada
 * paciente, agregados con FILTER para los conteos por nivel y percentile_cont para el p95.
 */
@Repository
@RequiredArgsConstructor
public class ReporteRepository {

    private static final String SQL_OPERACION = """
            WITH registrados AS (
                SELECT id, nivel, fecha_registro
                FROM paciente
                WHERE fecha_registro >= :desde AND fecha_registro < :hasta
            ), primera_consulta AS (
                SELECT a.paciente_id, a.fecha_evento,
                       ROW_NUMBER() OVER (PARTITION BY a.paciente_id ORDER BY a.fecha_evento, a.id) AS orden
                FROM auditoria_clinica a
                JOIN registrados r ON r.id = a.paciente_id
                WHERE a.tipo_evento = 'paciente_en_consulta'
            ), espera AS (
                SELECT p.id, p.nivel, date_trunc('hour', p.fecha_registro, 'UTC') AS hora,
                       EXTRACT(EPOCH FROM (pc.fecha_evento - p.fecha_registro)) / 60.0 AS minutos
                FROM registrados p
                LEFT JOIN primera_consulta pc ON pc.paciente_id = p.id AND pc.orden = 1
            )
            SELECT hora,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE nivel = 'CRITICO') AS critico,
                   COUNT(*) FILTER (WHERE nivel = 'URGENTE') AS urgente,
                   COUNT(*) FILTER (WHERE nivel = 'URGENTE_2') AS urgente_2,
                   COUNT(*) FILTER (WHERE nivel = 'NO_URGENTE') AS no_urgente,
                   COUNT(*) FILTER (WHERE nivel = 'NO_URGENTE_2') AS no_urgente_2,
                   COUNT(minutos) AS atendidos,
                   ROUND(AVG(minutos)::numeric, 2) AS espera_promedio,
                   ROUND((percentile_cont(0.95) WITHIN GROUP (ORDER BY minutos))::numeric, 2) AS espera_p95
            FROM espera
            GROUP BY hora
            ORDER BY hora
            """;

    private final DatabaseClient databaseClient;

    public Flux<FilaReporteDTO> operacionPorHora(Instant desde, Instant hasta) {
        return databaseClient.sql(SQL_OPERACION)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .map((fila, metadata) -> aFila(fila))
                .all();
    }

    private FilaReporteDTO aFila(Row fila) {
        Map<NivelTriage, Long> conteo = new EnumMap<>(NivelTriage.class);
        conteo.put(NivelTriage.CRITICO, fila.get("critico", Long.class));
        conteo.put(NivelTriage.URGENTE, fila.get("urgente", Long.class));
        conteo.put(NivelTriage.URGENTE_2, fila.get("urgente_2", Long.class));
        conteo.put(NivelTriage.NO_URGENTE, fila.get("no_urgente", Long.class));
        conteo.put(NivelTriage.NO_URGENTE_2, fila.get("no_urgente_2", Long.class));
        return FilaReporteDTO.builder()
                .hora(fila.get("hora", Instant.class))
                .totalRegistrados(fila.get("total", Long.class))
                .conteoPorNivel(conteo)
                .pacientesAtendidos(fila.get("atendidos", Long.class))
                .esperaPromedioMinutos(fila.get("espera_promedio", BigDecimal.class))
                .esperaP95Minutos(fila.get("espera_p95", BigDecimal.class))
                .build();
    }
}
