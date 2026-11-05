package co.com.vidacruz.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.domain.SegmentoRecuperado;
import co.com.vidacruz.asistente.service.RagRetriever;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Arranque: el índice del corpus queda listo al crear el contexto")
class IndiceArranqueIT {

    @Autowired
    private RagRetriever ragRetriever;

    @Test
    void shouldRetrieveProto002_WhenContextIsUp() {
        StepVerifier.create(ragRetriever.recuperar("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .assertNext(this::assertPrimeroEsAccionesIniciales)
                .verifyComplete();
    }

    private void assertPrimeroEsAccionesIniciales(List<SegmentoRecuperado> segmentos) {
        assertThat(segmentos).hasSize(3);
        assertThat(segmentos.get(0).protocolo()).isEqualTo("PROTO-002");
        assertThat(segmentos.get(0).seccion()).isEqualTo("Acciones iniciales");
    }
}
