package co.com.vidacruz.asistente.utilities;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Saneador {

    private static final int LONGITUD_MAXIMA = 100;

    /** Valores que vienen de fuera (topic, rutas) se limpian antes de loguearlos o copiarlos a cabeceras. */
    public static String limpiar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinControles = valor.replaceAll("\\p{Cntrl}+", " ").strip();
        return sinControles.length() > LONGITUD_MAXIMA ? sinControles.substring(0, LONGITUD_MAXIMA) : sinControles;
    }
}
