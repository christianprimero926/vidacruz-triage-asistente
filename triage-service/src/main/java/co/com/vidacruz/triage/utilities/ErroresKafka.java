package co.com.vidacruz.triage.utilities;

import lombok.experimental.UtilityClass;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;

@UtilityClass
public class ErroresKafka {

    /**
     * Un rechazo es permanente si reintentar no lo arregla (registro demasiado grande, serialización,
     * autorización, tópico inválido). Los transitorios (broker caído, timeout) y los desconocidos no lo son:
     * Kafka caído una hora no debe descartar nada.
     */
    public static boolean esPermanente(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof RetriableException) {
                return false;
            }
            if (actual instanceof ApiException || actual instanceof SerializationException) {
                return true;
            }
        }
        return false;
    }
}
