package co.com.vidacruz.triage.domain;

import java.time.Instant;
import java.util.UUID;

/** Alerta tomada por un despachador, ya con su intento contado. */
public record AlertaReclamada(UUID id, UUID pacienteId, NivelTriage nivel, int minutosEspera, int intentos,
                              int slaMinutos, Instant fechaCreacion) {

    public AlertaNotificacion aNotificacion() {
        return new AlertaNotificacion(id, pacienteId, nivel, slaMinutos, minutosEspera, fechaCreacion);
    }
}
