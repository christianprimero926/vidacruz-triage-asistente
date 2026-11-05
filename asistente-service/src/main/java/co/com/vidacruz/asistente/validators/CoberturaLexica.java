package co.com.vidacruz.asistente.validators;

import static co.com.vidacruz.asistente.utilities.Constants.PALABRAS_META;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.service.CorpusLoader;
import co.com.vidacruz.asistente.utilities.NormalizadorTexto;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mide qué fracción de las palabras con contenido de la respuesta aparece en las fuentes citadas. Detecta texto
 * clínico ajeno (un fármaco, una vía o una cifra en palabras) que las comprobaciones de fuente y de dígitos no ven.
 */
@Component
@RequiredArgsConstructor
public class CoberturaLexica {

    private static final Pattern ID_PROTOCOLO = Pattern.compile("PROTO-\\d+");

    /** Raíces de las palabras meta: no cuentan ni a favor ni en contra de la cobertura. */
    private static final Set<String> RAICES_META = NormalizadorTexto.raices(String.join(" ", PALABRAS_META));

    private final CorpusLoader corpusLoader;

    /** Las palabras de la pregunta no cuentan: el modelo las repite sin que digan nada de las fuentes. */
    public double calcular(String respuesta, String pregunta, List<FuenteCitada> fuentes) {
        Set<String> evaluadas = NormalizadorTexto.raices(ID_PROTOCOLO.matcher(respuesta).replaceAll(" "));
        evaluadas.removeAll(NormalizadorTexto.raices(pregunta));
        evaluadas.removeAll(RAICES_META);
        if (evaluadas.isEmpty()) {
            return 1.0;
        }
        Set<String> respaldadas = raicesDeFuentes(fuentes);
        long cubiertas = evaluadas.stream().filter(respaldadas::contains).count();
        return (double) cubiertas / evaluadas.size();
    }

    private Set<String> raicesDeFuentes(List<FuenteCitada> fuentes) {
        Set<String> raices = new HashSet<>();
        fuentes.forEach(fuente -> corpusLoader.textoReferencia(fuente.protocolo(), fuente.seccion())
                .ifPresent(texto -> raices.addAll(NormalizadorTexto.raices(texto))));
        return raices;
    }
}
