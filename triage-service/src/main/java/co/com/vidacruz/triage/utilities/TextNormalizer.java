package co.com.vidacruz.triage.utilities;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TextNormalizer {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    /** Recorta, pasa a minúsculas, quita tildes y colapsa espacios. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACIOS.matcher(sinTildes.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }
}
