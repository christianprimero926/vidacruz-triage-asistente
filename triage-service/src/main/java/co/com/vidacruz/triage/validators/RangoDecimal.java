package co.com.vidacruz.triage.validators;

import java.math.BigDecimal;

/** Límites inclusivos de un valor decimal y el máximo de decimales que admite. */
record RangoDecimal(BigDecimal minimo, BigDecimal maximo, int decimalesMaximos) {
}
