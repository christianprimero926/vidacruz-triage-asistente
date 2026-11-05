package co.com.vidacruz.asistente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("GET /asistente/estado-urgencias con Redis inaccesible")
class EstadoUrgenciasSinRedisIT {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldRespond503WithClearMessage_WhenRedisIsDown() {
        webTestClient.get().uri("/asistente/estado-urgencias").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("ESTADO_URGENCIAS_NO_DISPONIBLE")
                .jsonPath("$.path").isEqualTo("/asistente/estado-urgencias");
    }
}
