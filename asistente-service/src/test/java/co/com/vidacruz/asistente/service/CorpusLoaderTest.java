package co.com.vidacruz.asistente.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.asistente.domain.Protocolo;
import co.com.vidacruz.asistente.domain.SeccionProtocolo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CorpusLoader")
class CorpusLoaderTest {

    private CorpusLoader corpusLoader;

    @BeforeEach
    void setUp() {
        corpusLoader = new CorpusLoader(new ObjectMapper());
    }

    @Nested
    @DisplayName("Contenido del corpus")
    class Contenido {

        @Test
        void shouldLoadExactlyTheThreeProtocols_WhenCorpusIsRead() {
            List<String> ids = corpusLoader.protocolos().stream().map(Protocolo::id).toList();

            assertThat(ids).containsExactly("PROTO-001", "PROTO-002", "PROTO-003");
        }

        @Test
        void shouldKeepSectionsInOrder_WhenCorpusIsRead() {
            assertThat(nombresDeSecciones("PROTO-001"))
                    .containsExactly("Definición", "Medidas iniciales", "Contraindicaciones");
            assertThat(nombresDeSecciones("PROTO-002"))
                    .containsExactly("Criterios", "Acciones iniciales");
            assertThat(nombresDeSecciones("PROTO-003"))
                    .containsExactly("Clasificación", "Rehidratación");
        }

        @Test
        void shouldKeepLiteralText_WhenSectionContainsFiguresAndSymbols() {
            assertThat(contenido("PROTO-001", "Definición"))
                    .isEqualTo("Presión arterial ≥ 180/120 mmHg. "
                            + "Si hay daño de órgano diana es emergencia; si no, urgencia.");
            assertThat(contenido("PROTO-002", "Acciones iniciales"))
                    .contains("30 ml/kg de cristaloides si hay hipotensión o lactato ≥ 4.");
            assertThat(contenido("PROTO-003", "Rehidratación"))
                    .isEqualTo("Leve o moderada sin vómito → solución oral 50 ml/kg en 4 horas. "
                            + "Severa → vía intravenosa, bolo de 20 ml/kg.");
        }

        private List<String> nombresDeSecciones(String protocolo) {
            return corpusLoader.protocolos().stream()
                    .filter(p -> p.id().equals(protocolo))
                    .flatMap(p -> p.secciones().stream())
                    .map(SeccionProtocolo::nombre)
                    .toList();
        }

        private String contenido(String protocolo, String seccion) {
            return corpusLoader.protocolos().stream()
                    .filter(p -> p.id().equals(protocolo))
                    .flatMap(p -> p.secciones().stream())
                    .filter(s -> s.nombre().equals(seccion))
                    .map(SeccionProtocolo::contenido)
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("existe(protocolo, seccion)")
    class Existe {

        @Test
        void shouldReturnTrue_WhenProtocolAndSectionAreInCorpus() {
            assertThat(corpusLoader.existe("PROTO-002", "Acciones iniciales")).isTrue();
        }

        @Test
        void shouldReturnFalse_WhenProtocolDoesNotExist() {
            assertThat(corpusLoader.existe("PROTO-999", "Definición")).isFalse();
        }

        @Test
        void shouldReturnFalse_WhenSectionBelongsToAnotherProtocol() {
            assertThat(corpusLoader.existe("PROTO-001", "Rehidratación")).isFalse();
        }

        @Test
        void shouldReturnFalse_WhenSectionDoesNotExist() {
            assertThat(corpusLoader.existe("PROTO-001", "Dosis")).isFalse();
        }
    }
}
