package co.com.vidacruz.triage.utilities;

import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/** Los miembros se declaran static de forma explícita para poder importarlos de uno en uno. */
@UtilityClass
public class Constants {

    // Rutas
    public static final String RUTA_BASE_TRIAGE = "/triage";
    public static final String RUTA_PACIENTES = "/pacientes";
    public static final String RUTA_PACIENTE_POR_ID = "/pacientes/{id}";
    public static final String RUTA_ESTADO = "/pacientes/{id}/estado";
    public static final String RUTA_AUDITORIA = "/pacientes/{id}/auditoria";
    public static final String RUTA_REPORTE_OPERACION = "/reportes/operacion";
    public static final String RUTA_ALERTAS = "/alertas";

    // Alertas
    public static final int LIMITE_ALERTAS = 200;
    public static final String MSG_WEBHOOK_NO_CONFIGURADO = "webhook no configurado";
    public static final String MSG_ALERTA_TEXTO =
            "[TRIAGE] Paciente %s nivel %s escalado: superó SLA de %d min (lleva %d min)";

    // Reporte de operación
    public static final String MSG_FECHA_INVALIDA =
            "El campo '%s' debe ser una fecha ISO 8601, por ejemplo 2026-11-05T10:00:00Z";
    public static final String MSG_RANGO_ORDEN = "El campo 'hasta' debe ser posterior a 'desde'";
    public static final String MSG_RANGO_MAXIMO = "El rango entre 'desde' y 'hasta' no puede superar %d días";

    // Códigos y mensajes de transición de estado
    public static final String CODIGO_TRANSICION_INVALIDA = "TRANSICION_INVALIDA";
    public static final String CODIGO_CONFLICTO_CONCURRENTE = "CONFLICTO_CONCURRENTE";
    public static final String MSG_TRANSICION_INVALIDA = "No se puede pasar de %s a %s. Transiciones válidas desde %s: %s";
    public static final String MSG_SIN_TRANSICIONES = "ninguna";
    public static final String MSG_CONFLICTO_CONCURRENTE =
            "El paciente cambió de estado mientras se procesaba la solicitud; consulte su estado actual e intente de nuevo";
    public static final String MSG_ESTADO_INVALIDO = "El campo '%s' debe ser uno de: %s";

    // Outbox y Kafka
    public static final long OUTBOX_LOCK_KEY = 7_301_001L;
    public static final String HEADER_TIPO_EVENTO = "tipo-evento";
    public static final String HEADER_CORRELATION_KAFKA = "correlation-id";
    public static final String HEADER_TRACEPARENT = "traceparent";

    // Cabeceras, seguridad y correlación
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String ESQUEMA_API_KEY = "ApiKeyAuth";
    public static final String PATRON_CORRELATION_ID = "^[A-Za-z0-9-]{8,64}$";
    public static final String CODIGO_NO_AUTORIZADO = "NO_AUTORIZADO";
    public static final String MSG_NO_AUTORIZADO = "API key ausente o inválida";
    public static final List<String> RUTAS_PUBLICAS = List.of(
            "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**");

    // Métricas
    public static final String METRICA_CLASIFICACION = "triage.clasificacion";
    public static final String METRICA_ESCALAMIENTOS = "triage.escalamientos";
    public static final String METRICA_ALERTAS = "triage.alertas";
    public static final String METRICA_OUTBOX_PENDIENTES = "triage.outbox.pendientes";
    public static final String METRICA_OUTBOX_DESCARTADOS = "triage.outbox.descartados";
    public static final String TAG_NIVEL = "nivel";
    public static final String TAG_RESULTADO = "resultado";

    // Códigos de error
    public static final String CODIGO_VALIDACION = "VALIDACION_FALLIDA";
    public static final String CODIGO_SOLICITUD_INVALIDA = "SOLICITUD_INVALIDA";
    public static final String CODIGO_PACIENTE_NO_ENCONTRADO = "PACIENTE_NO_ENCONTRADO";
    public static final String CODIGO_RECURSO_NO_ENCONTRADO = "RECURSO_NO_ENCONTRADO";
    public static final String CODIGO_METODO_NO_PERMITIDO = "METODO_NO_PERMITIDO";
    public static final String CODIGO_MEDIA_TYPE_NO_SOPORTADO = "MEDIA_TYPE_NO_SOPORTADO";
    public static final String CODIGO_ERROR_INTERNO = "ERROR_INTERNO";
    public static final String CODIGO_BASE_DATOS_NO_DISPONIBLE = "BASE_DATOS_NO_DISPONIBLE";

    // Mensajes
    public static final String MSG_VALIDACION = "Hay campos inválidos: %s";
    public static final String MSG_CAMPO_OBLIGATORIO = "El campo '%s' es obligatorio";
    public static final String MSG_CAMPO_RANGO = "El campo '%s' debe estar entre %s y %s";
    public static final String MSG_CAMPO_LONGITUD = "El campo '%s' debe tener entre %d y %d caracteres";
    public static final String MSG_CAMPO_LONGITUD_MAXIMA = "El campo '%s' no puede superar %d caracteres";
    public static final String MSG_CAMPO_VACIO = "El campo '%s' no puede estar vacío";
    public static final String MSG_CAMPO_DECIMALES = "El campo '%s' admite como máximo %d decimales";
    public static final String MSG_PRESION_DIASTOLICA = "El campo '%s' debe ser menor que la presión sistólica";
    public static final String MSG_CAMPO_TIPO_INVALIDO = "El campo '%s' tiene un tipo o formato inválido";
    public static final String MSG_SOLICITUD_INVALIDA = "El cuerpo de la solicitud es obligatorio y debe ser un JSON válido";
    public static final String MSG_PARAMETRO_INVALIDO = "El parámetro '%s' tiene un valor inválido";
    public static final String MSG_PACIENTE_NO_ENCONTRADO = "No existe un paciente con id %s";
    public static final String MSG_RECURSO_NO_ENCONTRADO = "El recurso solicitado no existe";
    public static final String MSG_METODO_NO_PERMITIDO = "El método HTTP no está permitido para este recurso";
    public static final String MSG_MEDIA_TYPE_NO_SOPORTADO = "El tipo de contenido no está soportado, use application/json";
    public static final String MSG_BASE_DATOS_NO_DISPONIBLE =
            "El servicio no puede atender la solicitud en este momento. Intente de nuevo en unos segundos";
    public static final String SEGUNDOS_REINTENTO_BASE_DATOS = "5";
    public static final String MSG_ERROR_INTERNO = "Ocurrió un error inesperado. Intente de nuevo más tarde";

    // Quejas administrativas normalizadas (ver TextNormalizer): coincidencia exacta, no "contiene".
    public static final Set<String> QUEJAS_ADMINISTRATIVAS = Set.of(
            "documentos",
            "recoleccion de resultados",
            "renovacion de recetario",
            "vacunacion",
            "seguimiento");
}
