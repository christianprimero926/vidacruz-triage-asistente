package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Logs estructurados ECS con correlación")
class LogEstructuradoIT extends AbstractIT {

    @Test
    @DisplayName("el log de negocio sale en JSON ECS con la correlationId de la petición y sin datos personales")
    void shouldLogJsonWithCorrelation_WhenRegisteringPatient(CapturedOutput salida) {
        webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "log-12345678")
                .bodyValue(PacienteFixtures.e1())
                .exchange()
                .expectStatus().isCreated();

        String linea = salida.getAll().lines()
                .filter(texto -> texto.contains("Paciente registrado id="))
                .findFirst().orElseThrow();
        assertThat(linea).startsWith("{").endsWith("}")
                .contains("\"ecs\":{\"version\"")
                .contains("\"correlationId\":\"log-12345678\"")
                .contains("\"log\":{\"level\":\"INFO\"")
                .contains("nivel=CRITICO");
        assertThat(salida.getAll()).doesNotContain("Paciente de Prueba").doesNotContain("Dolor tor");
    }

    @Test
    @DisplayName("un valor de la petición con saltos de línea no puede falsificar líneas del log")
    void shouldNotAllowLogInjection_WhenPathContainsLineBreaks(CapturedOutput salida) {
        webTestClient.mutate().defaultHeaders(cabeceras -> cabeceras.remove("X-API-Key")).build()
                .get().uri("/triage/%0AERROR%20falso%0D%0Aotra%20linea")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(salida.getAll().lines().filter(linea -> linea.startsWith("ERROR falso"))).isEmpty();
        assertThat(salida.getAll()).contains("Acceso rechazado sin API key válida");
    }
}
