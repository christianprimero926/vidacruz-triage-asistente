package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.domain.AuditoriaClinica;
import co.com.vidacruz.triage.domain.NivelTriage;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import co.com.vidacruz.triage.repository.AuditoriaRepository;
import co.com.vidacruz.triage.repository.PacienteRepository;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import co.com.vidacruz.triage.support.TestClockConfig;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("POST /triage/pacientes")
class PacienteRegistroIT extends AbstractIT {

    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    static Stream<Arguments> ejemplosDelEnunciado() {
        return Stream.of(
                Arguments.of("E1", PacienteFixtures.e1(), NivelTriage.CRITICO, 5),
                Arguments.of("E2", PacienteFixtures.e2(), NivelTriage.URGENTE, 10),
                Arguments.of("E3", PacienteFixtures.e3(), NivelTriage.URGENTE_2, 30),
                Arguments.of("E4", PacienteFixtures.e4(), NivelTriage.NO_URGENTE, 60),
                Arguments.of("E5", PacienteFixtures.e5(), NivelTriage.NO_URGENTE_2, 120),
                Arguments.of("E6", PacienteFixtures.e6(), NivelTriage.CRITICO, 5));
    }

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
    @DisplayName("Registro correcto")
    class Registro {

        @ParameterizedTest(name = "{0} se clasifica {2} con SLA de {3} minutos")
        @MethodSource("co.com.vidacruz.triage.controller.PacienteRegistroIT#ejemplosDelEnunciado")
        void shouldClassifyExactly_WhenRegisteringStatementExamples(String ejemplo, PacienteRequestDTO request,
                                                                    NivelTriage nivel, int sla) {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().valueMatches("Location", "/triage/pacientes/[0-9a-f-]{36}")
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.nivel").isEqualTo(nivel.name())
                    .jsonPath("$.slaMinutos").isEqualTo(sla)
                    .jsonPath("$.estado").isEqualTo("REGISTRADO")
                    .jsonPath("$.fechaRegistro").isEqualTo(TestClockConfig.INICIO.toString());
        }

        @Test
        @DisplayName("el Location apunta al paciente creado y el paciente queda guardado con su SLA")
        void shouldPersistPaciente_WhenRegistrationSucceeds() {
            var registrado = registrar(PacienteFixtures.e1());

            var guardado = pacienteRepository.findById(registrado.getId()).block();
            assertThat(guardado).isNotNull();
            assertThat(guardado.getNivel()).isEqualTo(NivelTriage.CRITICO);
            assertThat(guardado.getSlaMinutos()).isEqualTo(5);
            assertThat(guardado.getSlaVenceEn()).isEqualTo(TestClockConfig.INICIO.plusSeconds(300));
            assertThat(guardado.getTemperatura()).isEqualByComparingTo("36.8");
            assertThat(guardado.isEscalado()).isFalse();
        }

        @Test
        @DisplayName("deja una auditoría de registro sin estado anterior")
        void shouldWriteAuditoria_WhenPacienteIsRegistered() {
            var registrado = registrar(PacienteFixtures.e2());

            List<AuditoriaClinica> auditorias = auditoriaRepository.findAll().collectList().block();
            assertThat(auditorias).singleElement().satisfies(auditoria -> {
                assertThat(auditoria.getPacienteId()).isEqualTo(registrado.getId());
                assertThat(auditoria.getTipoEvento()).isEqualTo("paciente_registrado");
                assertThat(auditoria.getEstadoAnterior()).isNull();
                assertThat(auditoria.getEstadoNuevo()).isEqualTo("REGISTRADO");
                assertThat(auditoria.getFechaEvento()).isEqualTo(TestClockConfig.INICIO);
            });
        }

        @Test
        @DisplayName("guarda el nombre y la queja sin espacios sobrantes")
        void shouldTrimTexts_WhenRegistering() {
            var registrado = registrar(PacienteFixtures.base().nombreCompleto("  Luis Pérez  ")
                    .quejaPrincipal("  Cefalea ").build());

            assertThat(pacienteRepository.findById(registrado.getId()).block()).satisfies(paciente -> {
                assertThat(paciente.getNombreCompleto()).isEqualTo("Luis Pérez");
                assertThat(paciente.getQuejaPrincipal()).isEqualTo("Cefalea");
            });
        }
    }

    @Nested
    @DisplayName("Validación de la solicitud")
    class Validacion {

        @Test
        @DisplayName("responde 400 nombrando el campo fuera de rango")
        void shouldRespondBadRequestNamingField_WhenValueIsOutOfRange() {
            var request = PacienteFixtures.base().frecuenciaCardiaca(300).build();

            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(400)
                    .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                    .jsonPath("$.mensaje").value(mensaje -> assertThat(mensaje.toString()).contains("frecuenciaCardiaca"))
                    .jsonPath("$.path").isEqualTo("/triage/pacientes")
                    .jsonPath("$.timestamp").isEqualTo(TestClockConfig.INICIO.toString())
                    .jsonPath("$.errores.length()").isEqualTo(1)
                    .jsonPath("$.errores[0].campo").isEqualTo("frecuenciaCardiaca")
                    .jsonPath("$.errores[0].mensaje")
                    .isEqualTo("El campo 'frecuenciaCardiaca' debe estar entre 20 y 250");
        }

        @Test
        @DisplayName("informa todos los campos inválidos en una sola respuesta")
        void shouldListEveryInvalidField_WhenSeveralAreWrong() {
            var request = PacienteFixtures.base().spo2(101).gcs(2).presionSistolica(100).presionDiastolica(100).build();

            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errores[*].campo").value(campos ->
                            assertThat(((List<?>) campos).stream().map(Object::toString))
                                    .containsExactlyInAnyOrder("presionDiastolica", "spo2", "gcs"));
        }

        @Test
        @DisplayName("un cuerpo vacío reporta los campos obligatorios")
        void shouldRequireFields_WhenBodyIsEmptyObject() {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errores.length()").isEqualTo(10)
                    .jsonPath("$.errores[?(@.campo=='temperatura')].mensaje")
                    .isEqualTo("El campo 'temperatura' es obligatorio");
        }

        @Test
        @DisplayName("una temperatura con más de dos decimales se rechaza")
        void shouldRejectTemperatura_WhenItHasThreeDecimals() {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonConCampo("temperatura", "36.123"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errores[0].campo").isEqualTo("temperatura");
        }

        @ParameterizedTest(name = "edad = {0}")
        @MethodSource("co.com.vidacruz.triage.controller.PacienteRegistroIT#valoresDeTipoIncorrecto")
        void shouldRespondBadRequestNamingField_WhenTypeIsWrong(String valor) {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonConCampo("edad", valor))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                    .jsonPath("$.errores[0].campo").isEqualTo("edad");
        }

        @Test
        @DisplayName("un JSON malformado responde 400 sin detalles internos")
        void shouldRespondBadRequest_WhenJsonIsMalformed() {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"edad\": ")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("SOLICITUD_INVALIDA")
                    .jsonPath("$.errores").doesNotExist();
        }

        @Test
        @DisplayName("sin cuerpo responde 400")
        void shouldRespondBadRequest_WhenBodyIsMissing() {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.codigo").isEqualTo("SOLICITUD_INVALIDA");
        }

        @Test
        @DisplayName("una solicitud inválida no guarda nada")
        void shouldNotPersistAnything_WhenValidationFails() {
            webTestClient.post().uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(PacienteFixtures.base().dolor(11).build())
                    .exchange()
                    .expectStatus().isBadRequest();

            assertThat(pacienteRepository.count().block()).isZero();
            assertThat(auditoriaRepository.count().block()).isZero();
        }
    }

    static Stream<String> valoresDeTipoIncorrecto() {
        return Stream.of("30.5", "\"abc\"", "true");
    }

    private static String jsonConCampo(String campo, String valorJson) {
        String base = """
                {"nombreCompleto":"Paciente de Prueba","edad":40,"frecuenciaCardiaca":72,"presionSistolica":120,
                "presionDiastolica":80,"spo2":99,"temperatura":36.5,"dolor":2,"gcs":15,"quejaPrincipal":"Herida"}
                """;
        return base.replaceAll("\"" + campo + "\":[^,}]+", "\"" + campo + "\":" + valorJson);
    }
}
