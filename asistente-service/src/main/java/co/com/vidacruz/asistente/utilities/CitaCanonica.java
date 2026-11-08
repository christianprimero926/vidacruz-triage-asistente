package co.com.vidacruz.asistente.utilities;

import static co.com.vidacruz.asistente.utilities.Constants.SEPARADOR_TITULO_SECCION;

import co.com.vidacruz.asistente.domain.FuenteCitada;
import co.com.vidacruz.asistente.domain.RespuestaLlm;
import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * El contexto muestra cada fragmento como "título — sección: contenido", y algunos modelos citan la sección con el
 * título delante. Esa cita se reduce a la sección recuperada del mismo protocolo; cualquier otra queda igual y la
 * juzga el validador.
 */
@UtilityClass
public class CitaCanonica {

    public static RespuestaLlm aplicar(RespuestaLlm respuesta, List<SegmentoRecuperado> recuperados) {
        if (respuesta == null || respuesta.fuentes() == null) {
            return respuesta;
        }
        List<FuenteCitada> fuentes = respuesta.fuentes().stream().map(fuente -> canonica(fuente, recuperados)).toList();
        return new RespuestaLlm(respuesta.respuesta(), fuentes, respuesta.sinProtocolo());
    }

    private static FuenteCitada canonica(FuenteCitada fuente, List<SegmentoRecuperado> recuperados) {
        if (fuente == null || fuente.protocolo() == null || fuente.seccion() == null) {
            return fuente;
        }
        String seccion = fuente.seccion().strip();
        return recuperados.stream()
                .filter(segmento -> segmento.protocolo().equals(fuente.protocolo()))
                .filter(segmento -> seccion.equals(segmento.seccion())
                        || seccion.endsWith(SEPARADOR_TITULO_SECCION + segmento.seccion()))
                .findFirst()
                .map(segmento -> new FuenteCitada(segmento.protocolo(), segmento.seccion()))
                .orElse(fuente);
    }
}
