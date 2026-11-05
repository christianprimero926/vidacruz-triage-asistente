package co.com.vidacruz.asistente.consumer;

import io.lettuce.core.RedisCommandExecutionException;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Distingue la respuesta de error de un comando (WRONGTYPE, OOM...), que reintentar no arregla, de los fallos de
 * conexión o de disponibilidad temporal del servidor, que sí se recuperan solos.
 */
@UtilityClass
public class RedisErrorPermanente {

    private static final List<String> PREFIJOS_TRANSITORIOS =
            List.of("LOADING", "BUSY", "TRYAGAIN", "CLUSTERDOWN", "MASTERDOWN", "READONLY", "NOREPLICAS");

    public static boolean esPermanente(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof RedisCommandExecutionException respuesta) {
                return !esTransitorio(respuesta.getMessage());
            }
        }
        return false;
    }

    private static boolean esTransitorio(String mensaje) {
        return mensaje != null && PREFIJOS_TRANSITORIOS.stream().anyMatch(mensaje::startsWith);
    }
}
