package co.com.vidacruz.triage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.test.StepVerifier;

@DisplayName("Auditoría clínica inmutable en la base (RN-04)")
class AuditoriaInmutableIT extends AbstractIT {

    private void registrarPaciente() {
        webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PacienteFixtures.e1())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PacienteRegistradoResponseDTO.class);
    }

    @Test
    @DisplayName("un DELETE sobre la auditoría falla y la fila sigue ahí")
    void shouldRejectDelete_WhenAuditoriaRowExists() {
        registrarPaciente();

        StepVerifier.create(databaseClient.sql("DELETE FROM auditoria_clinica").fetch().rowsUpdated())
                .expectErrorMatches(error -> error.getMessage().contains("inmutable"))
                .verify();

        assertThat(contarAuditorias()).isEqualTo(1);
    }

    @Test
    @DisplayName("un UPDATE sobre la auditoría falla y el valor no cambia")
    void shouldRejectUpdate_WhenAuditoriaRowExists() {
        registrarPaciente();

        StepVerifier.create(databaseClient.sql("UPDATE auditoria_clinica SET estado_nuevo = 'ALTA'")
                        .fetch().rowsUpdated())
                .expectErrorMatches(error -> error.getMessage().contains("inmutable"))
                .verify();

        String estado = databaseClient.sql("SELECT estado_nuevo FROM auditoria_clinica")
                .map(fila -> fila.get(0, String.class)).one().block();
        assertThat(estado).isEqualTo("REGISTRADO");
    }

    private Long contarAuditorias() {
        return databaseClient.sql("SELECT COUNT(*) FROM auditoria_clinica")
                .map(fila -> fila.get(0, Long.class)).one().block();
    }
}
