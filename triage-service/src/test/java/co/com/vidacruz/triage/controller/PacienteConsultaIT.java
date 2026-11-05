package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("GET /triage/pacientes/{id} y errores comunes")
class PacienteConsultaIT extends AbstractIT {

    private PacienteRegistradoResponseDTO registrar(PacienteRequestDTO request) {
        return webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PacienteRegistradoResponseDTO.class)
                .returnResult().getResponseBody();
    }

    @Nested
    @DisplayName("Consulta de un paciente")
    class Consulta {

        @Test
        @DisplayName("devuelve todos los datos, el estado y los flags")
        void shouldReturnCompletePaciente_WhenItExists() {
            var registrado = registrar(PacienteFixtures.e2());

            webTestClient.get().uri("/triage/pacientes/{id}", registrado.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(registrado.getId().toString())
                    .jsonPath("$.nombreCompleto").isEqualTo("Paciente de Prueba")
                    .jsonPath("$.edad").isEqualTo(40)
                    .jsonPath("$.frecuenciaCardiaca").isEqualTo(95)
                    .jsonPath("$.presionSistolica").isEqualTo(150)
                    .jsonPath("$.presionDiastolica").isEqualTo(95)
                    .jsonPath("$.spo2").isEqualTo(91)
                    .jsonPath("$.temperatura").isEqualTo(37.0)
                    .jsonPath("$.dolor").isEqualTo(4)
                    .jsonPath("$.gcs").isEqualTo(15)
                    .jsonPath("$.quejaPrincipal").isEqualTo("Dificultad para respirar")
                    .jsonPath("$.nivel").isEqualTo("URGENTE")
                    .jsonPath("$.slaMinutos").isEqualTo(10)
                    .jsonPath("$.estado").isEqualTo("REGISTRADO")
                    .jsonPath("$.escalado").isEqualTo(false)
                    .jsonPath("$.fechaEscalamiento").doesNotExist()
                    .jsonPath("$.fechaRegistro").isEqualTo(TestClockConfig.INICIO.toString())
                    .jsonPath("$.fechaEstadoActual").isEqualTo(TestClockConfig.INICIO.toString())
                    .jsonPath("$.slaVenceEn").isEqualTo(TestClockConfig.INICIO.plusSeconds(600).toString());
        }

        @Test
        @DisplayName("informa el tiempo en el estado actual en minutos y segundos")
        void shouldReturnTimeInCurrentState_WhenTimePasses() {
            var registrado = registrar(PacienteFixtures.e4());

            clock.avanzar(Duration.ofMinutes(7).plusSeconds(30));

            webTestClient.get().uri("/triage/pacientes/{id}", registrado.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.tiempoEnEstadoActualMinutos").isEqualTo(7)
                    .jsonPath("$.tiempoEnEstadoActualSegundos").isEqualTo(450);
        }

        @Test
        @DisplayName("recién registrado lleva cero segundos en el estado")
        void shouldReturnZeroTime_WhenPacienteWasJustRegistered() {
            var registrado = registrar(PacienteFixtures.e4());

            webTestClient.get().uri("/triage/pacientes/{id}", registrado.getId())
                    .exchange()
                    .expectBody()
                    .jsonPath("$.tiempoEnEstadoActualMinutos").isEqualTo(0)
                    .jsonPath("$.tiempoEnEstadoActualSegundos").isEqualTo(0);
        }

        @Test
        @DisplayName("responde 404 con el formato común cuando no existe")
        void shouldRespondNotFound_WhenPacienteDoesNotExist() {
            UUID id = UUID.randomUUID();

            webTestClient.get().uri("/triage/pacientes/{id}", id)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(404)
                    .jsonPath("$.codigo").isEqualTo("PACIENTE_NO_ENCONTRADO")
                    .jsonPath("$.mensaje").isEqualTo("No existe un paciente con id " + id)
                    .jsonPath("$.path").isEqualTo("/triage/pacientes/" + id);
        }

        @Test
        @DisplayName("responde 400 nombrando el campo cuando el id no es un UUID")
        void shouldRespondBadRequestNamingId_WhenIdIsNotUuid() {
            webTestClient.get().uri("/triage/pacientes/no-es-un-uuid")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                    .jsonPath("$.mensaje").isEqualTo("Hay campos inválidos: id")
                    .jsonPath("$.errores[0].campo").isEqualTo("id")
                    .jsonPath("$.errores[0].mensaje").isEqualTo("El parámetro 'id' tiene un valor inválido");
        }
    }

    @Nested
    @DisplayName("Errores comunes con el mismo formato")
    class ErroresComunes {

        @Test
        @DisplayName("una ruta inexistente responde 404")
        void shouldRespondNotFound_WhenRouteDoesNotExist() {
            webTestClient.get().uri("/triage/no-existe")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("RECURSO_NO_ENCONTRADO")
                    .jsonPath("$.path").isEqualTo("/triage/no-existe");
        }

        @Test
        @DisplayName("un método no soportado responde 405")
        void shouldRespondMethodNotAllowed_WhenMethodIsNotSupported() {
            webTestClient.delete().uri("/triage/pacientes/{id}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isEqualTo(405)
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("METODO_NO_PERMITIDO")
                    .jsonPath("$.status").isEqualTo(405);
        }

        @Test
        @DisplayName("un tipo de contenido no soportado responde 415")
        void shouldRespondUnsupportedMediaType_WhenContentTypeIsNotJson() {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue("hola")
                    .exchange()
                    .expectStatus().isEqualTo(415)
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("MEDIA_TYPE_NO_SOPORTADO");
        }
    }

    @Nested
    @DisplayName("Fallos inesperados")
    class FallosInesperados {

        @Test
        @DisplayName("si falla la auditoría responde 500 genérico y no queda el paciente guardado")
        void shouldRollbackAndHideDetails_WhenAuditoriaInsertFails() {
            databaseClient.sql("""
                    CREATE FUNCTION falla_auditoria() RETURNS TRIGGER AS $$
                    BEGIN RAISE EXCEPTION 'detalle interno de la base'; END;
                    $$ LANGUAGE plpgsql
                    """).then().block();
            databaseClient.sql("CREATE TRIGGER tr_falla BEFORE INSERT ON auditoria_clinica "
                    + "FOR EACH ROW EXECUTE FUNCTION falla_auditoria()").then().block();
            try {
                webTestClient.post().uri("/triage/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(PacienteFixtures.e1())
                        .exchange()
                        .expectStatus().is5xxServerError()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(500)
                        .jsonPath("$.codigo").isEqualTo("ERROR_INTERNO")
                        .jsonPath("$.mensaje").value(mensaje ->
                                assertThat(mensaje.toString()).doesNotContain("detalle interno"));

                Long pacientes = databaseClient.sql("SELECT COUNT(*) FROM paciente")
                        .map(fila -> fila.get(0, Long.class)).one().block();
                assertThat(pacientes).isZero();
            } finally {
                databaseClient.sql("DROP TRIGGER tr_falla ON auditoria_clinica").then().block();
                databaseClient.sql("DROP FUNCTION falla_auditoria()").then().block();
            }
        }
    }
}
