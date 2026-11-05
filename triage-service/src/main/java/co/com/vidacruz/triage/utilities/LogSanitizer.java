package co.com.vidacruz.triage.utilities;

import lombok.experimental.UtilityClass;

/** Evita la inyección de líneas en los logs y el eco de valores enormes controlados por quien hace la petición. */
@UtilityClass
public class LogSanitizer {

    static final int LARGO_MAXIMO = 100;

    /** Reemplaza los caracteres de control por un espacio y recorta a 100 caracteres. */
    public static String sanear(String valor) {
        if (valor == null) {
            return "";
        }
        String limpio = valor.codePoints()
                .map(codigo -> Character.isISOControl(codigo) ? ' ' : codigo)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return limpio.length() > LARGO_MAXIMO ? limpio.substring(0, LARGO_MAXIMO) : limpio;
    }
}
