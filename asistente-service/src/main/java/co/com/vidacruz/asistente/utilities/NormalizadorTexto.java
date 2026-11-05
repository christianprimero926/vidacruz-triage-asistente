package co.com.vidacruz.asistente.utilities;

import static co.com.vidacruz.asistente.utilities.Constants.LONGITUD_MINIMA_PALABRA;
import static co.com.vidacruz.asistente.utilities.Constants.LONGITUD_RAIZ;
import static co.com.vidacruz.asistente.utilities.Constants.STOPWORDS;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NormalizadorTexto {

    /**
     * Raíces de las palabras significativas: minúsculas, sin tildes, solo letras, sin stopwords.
     * Truncar a pocos caracteres agrupa variantes ("hipertensiva" e "hipertensión") sin un stemmer.
     */
    public static Set<String> raices(String texto) {
        String sinTildes = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return Arrays.stream(sinTildes.split("[^a-z]+"))
                .filter(palabra -> palabra.length() >= LONGITUD_MINIMA_PALABRA)
                .filter(palabra -> !STOPWORDS.contains(palabra))
                .map(palabra -> palabra.substring(0, Math.min(LONGITUD_RAIZ, palabra.length())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
