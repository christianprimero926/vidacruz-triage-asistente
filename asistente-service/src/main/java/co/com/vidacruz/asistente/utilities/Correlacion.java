package co.com.vidacruz.asistente.utilities;

import static co.com.vidacruz.asistente.utilities.Constants.CLAVE_CORRELACION;
import static co.com.vidacruz.asistente.utilities.Constants.PATRON_CORRELACION;

import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;

@UtilityClass
public class Correlacion {

    private static final Pattern FORMATO_VALIDO = Pattern.compile(PATRON_CORRELACION);

    /** Solo se aceptan identificadores seguros: evita que un cliente inyecte contenido arbitrario en los logs. */
    public static boolean esValido(String correlationId) {
        return correlationId != null && FORMATO_VALIDO.matcher(correlationId).matches();
    }

    /** El accessor de MDC de context-propagation lee del contexto de Reactor un mapa bajo su propia clave. */
    public static Context enContexto(Context contexto, String correlationId) {
        return contexto.put(Slf4jThreadLocalAccessor.KEY, Map.of(CLAVE_CORRELACION, correlationId));
    }
}
