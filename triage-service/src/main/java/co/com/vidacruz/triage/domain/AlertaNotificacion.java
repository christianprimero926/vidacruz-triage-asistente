package co.com.vidacruz.triage.domain;

import java.time.Instant;
import java.util.UUID;

/** Lo que se le comunica al equipo médico; deliberadamente sin nombre ni queja del paciente. */
public record AlertaNotificacion(UUID alertaId, UUID pacienteId, NivelTriage nivel, int slaMinutos,
                                 int minutosEspera, Instant fechaEscalamiento) {
}
