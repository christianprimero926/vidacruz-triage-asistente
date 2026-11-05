package co.com.vidacruz.triage.dto.request;

import java.time.Instant;

/** Rango semiabierto [desde, hasta) ya validado. */
public record RangoReporteDTO(Instant desde, Instant hasta) {
}
