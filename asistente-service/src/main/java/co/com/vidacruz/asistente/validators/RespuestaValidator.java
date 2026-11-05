package co.com.vidacruz.asistente.validators;

import co.com.vidacruz.asistente.config.ValidacionProperties;
import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.CorpusLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RN-05: una respuesta solo es válida si cita fuentes reales, no menciona protocolos que no cita, no introduce cifras
 * ajenas a sus fuentes y su vocabulario está respaldado por ellas.
 */
@Component
@RequiredArgsConstructor
public class RespuestaValidator {

    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern ID_PROTOCOLO = Pattern.compile("PROTO-\\d+");

    private final CorpusLoader corpusLoader;
    private final CoberturaLexica coberturaLexica;
    private final ValidacionProperties properties;

    public boolean esValida(String pregunta, RespuestaLlm respuesta, List<SegmentoRecuperado> recuperados) {
        if (respuesta == null || Boolean.TRUE.equals(respuesta.sinProtocolo())
                || respuesta.respuesta() == null || respuesta.respuesta().isBlank()) {
            return false;
        }
        List<FuenteCitada> fuentes = respuesta.fuentes();
        if (fuentes == null || fuentes.isEmpty()) {
            return false;
        }
        String texto = respuesta.respuesta();
        return fuentes.stream().allMatch(fuente -> fuenteReal(fuente, recuperados))
                && protocolosMencionadosCitados(texto, fuentes)
                && cifrasSustentadas(texto, fuentes)
                && coberturaLexica.calcular(texto, pregunta, fuentes) >= properties.coberturaMinima();
    }

    private boolean fuenteReal(FuenteCitada fuente, List<SegmentoRecuperado> recuperados) {
        return fuente != null
                && corpusLoader.existe(fuente.protocolo(), fuente.seccion())
                && recuperados.stream().anyMatch(s -> s.protocolo().equals(fuente.protocolo())
                        && s.seccion().equals(fuente.seccion()));
    }

    /** Todo PROTO-n que aparezca en el texto debe existir en el corpus y estar entre las fuentes citadas. */
    private boolean protocolosMencionadosCitados(String texto, List<FuenteCitada> fuentes) {
        Matcher mencion = ID_PROTOCOLO.matcher(texto);
        while (mencion.find()) {
            String id = mencion.group();
            boolean citado = fuentes.stream().anyMatch(fuente -> id.equals(fuente.protocolo()));
            if (!citado || !corpusLoader.existeProtocolo(id)) {
                return false;
            }
        }
        return true;
    }

    private boolean cifrasSustentadas(String respuesta, List<FuenteCitada> fuentes) {
        Set<String> cifrasCitadas = new HashSet<>();
        fuentes.forEach(fuente -> corpusLoader.contenido(fuente.protocolo(), fuente.seccion())
                .ifPresent(contenido -> cifrasCitadas.addAll(cifrasDe(contenido))));
        return cifrasCitadas.containsAll(cifrasDe(ID_PROTOCOLO.matcher(respuesta).replaceAll(" ")));
    }

    private static Set<String> cifrasDe(String texto) {
        Set<String> cifras = new HashSet<>();
        Matcher coincidencia = NUMERO.matcher(texto);
        while (coincidencia.find()) {
            cifras.add(coincidencia.group().replace(',', '.'));
        }
        return cifras;
    }
}
