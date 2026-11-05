package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@DisplayName("PATCH /triage/pacientes/{id}/estado y GET .../auditoria")
class EstadoPacienteIT extends AbstractIT {

    private UUID registrar(PacienteRequestDTO request) {
        return webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PacienteRegistradoResponseDTO.class)
                .returnResult().getResponseBody().getId();
    }

    private WebTestClient.ResponseSpec cambiar(UUID id, String cuerpo) {
        return webTestClient.patch().uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange();
    }

    private WebTestClient.ResponseSpec cambiarA(UUID id, String estado) {
        return cambiar(id, "{\"estado\":\"" + estado + "\"}");
    }

    @Nested
    @DisplayName("Transiciones")
    class Transiciones {

        @Test
        @DisplayName("REGISTRADO a EN_CONSULTA devuelve el paciente con el estado nuevo y el tiempo reiniciado")
        void shouldMoveToConsulta_WhenTransitionIsValid() {
            UUID id = registrar(PacienteFixtures.e2());
            clock.avanzar(Duration.ofMinutes(4));

            cambiarA(id, "EN_CONSULTA")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(id.toString())
                    .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                    .jsonPath("$.fechaEstadoActual").isEqualTo(TestClockConfig.INICIO.plusSeconds(240).toString())
                    .jsonPath("$.tiempoEnEstadoActualSegundos").isEqualTo(0)
                    .jsonPath("$.fechaRegistro").isEqualTo(TestClockConfig.INICIO.toString());

            clock.avanzar(Duration.ofMinutes(2));
            webTestClient.get().uri("/triage/pacientes/{id}", id).exchange()
                    .expectBody()
                    .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                    .jsonPath("$.tiempoEnEstadoActualMinutos").isEqualTo(2);
        }

        @Test
        @DisplayName("REGISTRADO a ALTA directa es válido")
        void shouldAllowDirectDischarge_WhenPacienteIsRegistrado() {
            UUID id = registrar(PacienteFixtures.e4());

            cambiarA(id, "ALTA").expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("ALTA");
        }

        @Test
        @DisplayName("ALTA a EN_CONSULTA responde 409 con mensaje descriptivo y no cambia nada")
        void shouldRespondConflict_WhenTransitionIsInvalid() {
            UUID id = registrar(PacienteFixtures.e4());
            cambiarA(id, "ALTA").expectStatus().isOk();

            cambiarA(id, "EN_CONSULTA")
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(409)
                    .jsonPath("$.codigo").isEqualTo("TRANSICION_INVALIDA")
                    .jsonPath("$.mensaje").isEqualTo(
                            "No se puede pasar de ALTA a EN_CONSULTA. Transiciones válidas desde ALTA: ninguna");

            webTestClient.get().uri("/triage/pacientes/{id}", id).exchange()
                    .expectBody().jsonPath("$.estado").isEqualTo("ALTA");
            assertThat(contarAuditorias(id)).isEqualTo(2);
        }

        @Test
        @DisplayName("repetir el mismo estado es una transición inválida")
        void shouldRespondConflict_WhenStateDoesNotChange() {
            UUID id = registrar(PacienteFixtures.e4());

            cambiarA(id, "REGISTRADO").expectStatus().isEqualTo(409);
        }

        @Test
        @DisplayName("dos cambios simultáneos al mismo estado: uno gana y el otro recibe 409, con una sola auditoría")
        void shouldAllowOnlyOne_WhenSameTransitionRunsConcurrently() {
            UUID id = registrar(PacienteFixtures.e4());

            var primero = CompletableFuture.supplyAsync(() -> cambiarA(id, "EN_CONSULTA").returnResult(String.class)
                    .getStatus().value());
            var segundo = CompletableFuture.supplyAsync(() -> cambiarA(id, "EN_CONSULTA").returnResult(String.class)
                    .getStatus().value());

            assertThat(List.of(primero.join(), segundo.join())).containsExactlyInAnyOrder(200, 409);
            assertThat(contarAuditorias(id)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Solicitudes inválidas")
    class Invalidas {

        @Test
        @DisplayName("un estado inexistente responde 400 nombrando el campo")
        void shouldRespondBadRequest_WhenStateDoesNotExist() {
            cambiarA(registrar(PacienteFixtures.e4()), "DESCONOCIDO")
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errores[0].campo").isEqualTo("estado");
        }

        @Test
        @DisplayName("un estado vacío o ausente responde 400")
        void shouldRespondBadRequest_WhenStateIsEmptyOrMissing() {
            UUID id = registrar(PacienteFixtures.e4());

            cambiar(id, "{\"estado\":\"\"}").expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.errores[0].campo").isEqualTo("estado");
            cambiar(id, "{}").expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.errores[0].mensaje").isEqualTo("El campo 'estado' es obligatorio");
        }

        @Test
        @DisplayName("un id que no es UUID responde 400 y uno inexistente 404")
        void shouldRespondBadRequestOrNotFound_WhenIdIsWrong() {
            webTestClient.patch().uri("/triage/pacientes/abc/estado")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"estado\":\"ALTA\"}")
                    .exchange().expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.errores[0].campo").isEqualTo("id");

            cambiarA(UUID.randomUUID(), "ALTA").expectStatus().isNotFound()
                    .expectBody().jsonPath("$.codigo").isEqualTo("PACIENTE_NO_ENCONTRADO");
        }
    }

    @Nested
    @DisplayName("Auditoría")
    class Auditoria {

        @Test
        @DisplayName("registra cada cambio con anterior, nuevo, tipo y fecha, en orden cronológico")
        void shouldListEveryChangeInOrder_WhenPacienteGoesThroughTheFlow() {
            UUID id = registrar(PacienteFixtures.e1());
            clock.avanzar(Duration.ofMinutes(3));
            cambiarA(id, "EN_CONSULTA").expectStatus().isOk();
            clock.avanzar(Duration.ofMinutes(20));
            cambiarA(id, "ALTA").expectStatus().isOk();

            webTestClient.get().uri("/triage/pacientes/{id}/auditoria", id)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3)
                    .jsonPath("$[0].tipoEvento").isEqualTo("paciente_registrado")
                    .jsonPath("$[0].estadoAnterior").doesNotExist()
                    .jsonPath("$[0].estadoNuevo").isEqualTo("REGISTRADO")
                    .jsonPath("$[0].fechaEvento").isEqualTo(TestClockConfig.INICIO.toString())
                    .jsonPath("$[0].pacienteId").isEqualTo(id.toString())
                    .jsonPath("$[1].tipoEvento").isEqualTo("paciente_en_consulta")
                    .jsonPath("$[1].estadoAnterior").isEqualTo("REGISTRADO")
                    .jsonPath("$[1].estadoNuevo").isEqualTo("EN_CONSULTA")
                    .jsonPath("$[1].fechaEvento").isEqualTo(TestClockConfig.INICIO.plusSeconds(180).toString())
                    .jsonPath("$[2].tipoEvento").isEqualTo("paciente_alta")
                    .jsonPath("$[2].estadoAnterior").isEqualTo("EN_CONSULTA")
                    .jsonPath("$[2].estadoNuevo").isEqualTo("ALTA")
                    .jsonPath("$[2].fechaEvento").isEqualTo(TestClockConfig.INICIO.plusSeconds(1380).toString());
        }

        @Test
        @DisplayName("un paciente inexistente responde 404")
        void shouldRespondNotFound_WhenPacienteDoesNotExist() {
            webTestClient.get().uri("/triage/pacientes/{id}/auditoria", UUID.randomUUID())
                    .exchange().expectStatus().isNotFound();
        }

        @Test
        @DisplayName("no existe ninguna operación de borrado ni de modificación de la auditoría")
        void shouldNotExposeDeleteOrUpdate_WhenCallingAuditoriaEndpoint() {
            UUID id = registrar(PacienteFixtures.e4());

            webTestClient.delete().uri("/triage/pacientes/{id}/auditoria", id)
                    .exchange().expectStatus().isEqualTo(405);
            webTestClient.put().uri("/triage/pacientes/{id}/auditoria", id)
                    .exchange().expectStatus().isEqualTo(405);
            assertThat(contarAuditorias(id)).isEqualTo(1);
        }
    }

    private Long contarAuditorias(UUID id) {
        return databaseClient.sql("SELECT COUNT(*) FROM auditoria_clinica WHERE paciente_id = :id")
                .bind("id", id).map(fila -> fila.get(0, Long.class)).one().block();
    }
}
