# ADR 0006: Autenticación con API key por servicio

Estado: aceptado

## Contexto

Los endpoints exponen datos clínicos y el asistente consume un LLM con costo. El enunciado no define usuarios, roles ni proveedor de identidad, pero un servicio abierto no es aceptable. Los probes de Kubernetes y Swagger tienen que funcionar sin credenciales.

## Decisión

Cada servicio exige la cabecera `X-API-Key` mediante un `WebFilter` (`ApiKeyWebFilter`), con claves independientes (`TRIAGE_API_KEY` y `ASISTENTE_API_KEY`).

- La comparación usa `MessageDigest.isEqual` (tiempo constante) para no filtrar la clave por temporización.
- Fail-closed: la propiedad es obligatoria y está validada; si falta, la aplicación no arranca. En el código no hay valor por defecto.
- Rutas públicas: `/actuator/health/**`, Swagger UI, `/v3/api-docs/**` y `/webjars/**`. Todo lo demás, incluido `/actuator/prometheus`, exige clave.
- El 401 usa el mismo formato de error del resto de la API. El filtro de correlación corre antes, así que el 401 también lleva `correlationId`.
- OpenAPI declara el esquema de seguridad para poder probar desde Swagger.
- Las claves llegan por variable de entorno (compose) o por un secret de Kubernetes (Azure). El `.env.example` solo trae valores para local.

## Alternativas consideradas

- Sin autenticación: descartado.
- OAuth2 con JWT de Entra ID desde ya: es lo correcto para producción, pero exige registrar aplicaciones y contar con un tenant, lo que impide ejecutar el proyecto con un solo comando.
- mTLS entre servicios: sirve para tráfico interno, no resuelve a los usuarios finales.

## Consecuencias

- Una clave compartida no identifica a quien llama, no tiene roles y rotarla implica reiniciar el servicio. No sirve para auditar usuarios.
- La clave viaja en una cabecera, así que en producción debe ir sobre HTTPS. El ingress de Azure de este proyecto está en HTTP (ver [ADR 0007](0007-despliegue-en-azure.md)).
- Evolución: agregar `spring-boot-starter-oauth2-resource-server` para validar JWT emitidos por Entra ID, mapear roles de aplicación (por ejemplo enfermería, médico, administrador) a permisos por endpoint e incluir el `oid` o `sub` del usuario en la auditoría clínica. El filtro de API key puede quedar solo para integraciones entre sistemas.
