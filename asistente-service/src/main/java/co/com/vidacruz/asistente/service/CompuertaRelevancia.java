package co.com.vidacruz.asistente.service;

import co.com.vidacruz.asistente.config.RagProperties;
import co.com.vidacruz.asistente.domain.Protocolo;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.utilities.NormalizadorTexto;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Segunda barrera tras la búsqueda semántica: los embeddings en español no separan por score
 * las preguntas ajenas al corpus, así que se exige además una coincidencia léxica con términos
 * que identifican a un único protocolo (derivados del propio corpus) o un score muy alto.
 */
@Slf4j
@Component
public class CompuertaRelevancia {

    private final Set<String> terminosDiscriminantes;
    private final double scoreAlto;

    public CompuertaRelevancia(CorpusLoader corpusLoader, RagProperties properties) {
        this.terminosDiscriminantes = derivarTerminos(corpusLoader.protocolos());
        this.scoreAlto = properties.scoreAlto();
        log.info("Compuerta de relevancia: {} términos discriminantes", terminosDiscriminantes.size());
    }

    public boolean pasa(String pregunta, List<SegmentoRecuperado> recuperados) {
        if (recuperados.isEmpty()) {
            return false;
        }
        boolean scoreSuficiente = recuperados.stream().anyMatch(s -> s.score() >= scoreAlto);
        return scoreSuficiente || comparteTermino(pregunta);
    }

    public Set<String> terminosDiscriminantes() {
        return terminosDiscriminantes;
    }

    private boolean comparteTermino(String pregunta) {
        return !Collections.disjoint(NormalizadorTexto.raices(pregunta), terminosDiscriminantes);
    }

    private static Set<String> derivarTerminos(List<Protocolo> protocolos) {
        Map<String, Integer> protocolosPorRaiz = new HashMap<>();
        protocolos.forEach(protocolo -> raicesDe(protocolo)
                .forEach(raiz -> protocolosPorRaiz.merge(raiz, 1, Integer::sum)));
        return protocolosPorRaiz.entrySet().stream()
                .filter(entrada -> entrada.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> raicesDe(Protocolo protocolo) {
        String texto = Stream.concat(
                        Stream.of(protocolo.titulo()),
                        protocolo.secciones().stream().map(s -> s.nombre() + " " + s.contenido()))
                .collect(Collectors.joining(" "));
        return NormalizadorTexto.raices(texto);
    }
}
