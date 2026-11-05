package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.structured.format.console=ecs")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("Logs ECS con correlationId en el MDC")
class LogsEstructuradosIT {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldWriteJsonLogsWithCorrelationId_WhenRequestIsHandledReactively(CapturedOutput salida) {
        webTestClient.post().uri("/asistente/consulta").header("X-Correlation-Id", "correlacion-logs-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", "receta de arroz con pollo"))
                .exchange().expectStatus().isOk();

        String linea = salida.getOut().lines()
                .filter(l -> l.contains("Consulta respondida sin protocolo"))
                .findFirst().orElseThrow();
        assertThat(linea).startsWith("{").contains("\"ecs\"")
                .contains("\"correlationId\":\"correlacion-logs-01\"");
    }
}
