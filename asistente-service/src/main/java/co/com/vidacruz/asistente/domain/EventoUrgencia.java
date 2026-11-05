package co.com.vidacruz.asistente.domain;

/** Evento de triaje ya validado, con lo mínimo que necesita la proyección de urgencias. */
public record EventoUrgencia(TipoEvento tipo, String pacienteId, NivelTriage nivel) {
}
