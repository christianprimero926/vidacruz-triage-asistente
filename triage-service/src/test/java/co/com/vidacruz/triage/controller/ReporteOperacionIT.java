package co.com.vidacruz.triage.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.support.AbstractIT;
import co.com.vidacruz.triage.support.PacienteFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Datos sembrados por la API con un reloj controlado.
 * Hora 10:00 (esperas de los atendidos: 2, 10 y 30 min): total 5, atendidos 3,
 * promedio (2+10+30)/3 = 14.00 y p95 interpolado = 10 + 0.9 * (30 - 10) = 28.00.
 * Hora 11:00: dos pacientes sin consulta. Hora 12:00: una espera de 4.5 min, fuera del rango principal.
 */
@DisplayName("GET /triage/reportes/operacion")
class ReporteOperacionIT extends AbstractIT {

    private static final String DESDE = "2026-11-05T10:00:00Z";
    private static final String HASTA = "2026-11-05T12:00:00Z";

    @BeforeEach
    void sembrar() {
        UUID critico = registrarEn("2026-11-05T10:05:00Z", PacienteFixtures.e1());
        UUID urgente = registrarEn("2026-11-05T10:10:00Z", PacienteFixtures.e2());
        UUID urgente2 = registrarEn("2026-11-05T10:20:00Z", PacienteFixtures.e3());
        registrarEn("2026-11-05T10:30:00Z", PacienteFixtures.e4());
        UUID altaDirecta = registrarEn("2026-11-05T10:40:00Z", PacienteFixtures.e6());
        registrarEn("2026-11-05T11:15:00Z", PacienteFixtures.e5());
        registrarEn("2026-11-05T11:45:00Z", PacienteFixtures.e4());
        UUID tarde = registrarEn("2026-11-05T12:00:00Z", PacienteFixtures.e1());
        registrarEn("2026-11-05T09:59:59Z", PacienteFixtures.e1());

        cambiarEn("2026-11-05T10:07:00Z", critico, "EN_CONSULTA");
        cambiarEn("2026-11-05T10:20:00Z", urgente, "EN_CONSULTA");
        cambiarEn("2026-11-05T10:50:00Z", urgente2, "EN_CONSULTA");
        cambiarEn("2026-11-05T10:45:00Z", altaDirecta, "ALTA");
        cambiarEn("2026-11-05T11:30:00Z", critico, "ALTA");
        cambiarEn("2026-11-05T12:04:30Z", tarde, "EN_CONSULTA");
    }

    private UUID registrarEn(String instante, PacienteRequestDTO paciente) {
        clock.fijar(Instant.parse(instante));
        return crearPaciente(paciente);
    }

    private void cambiarEn(String instante, UUID id, String estado) {
        clock.fijar(Instant.parse(instante));
        moverPaciente(id, estado).expectStatus().isOk();
    }

    private WebTestClient.BodyContentSpec consultar(String desde, String hasta) {
        return webTestClient.get().uri(uri -> uri.path("/triage/reportes/operacion")
                        .queryParam("desde", desde).queryParam("hasta", hasta).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    @Nested
    @DisplayName("Cálculo")
    class Calculo {

        @Test
        @DisplayName("agrupa por hora con total, conteo por nivel, atendidos, promedio y p95 calculados a mano")
        void shouldAggregatePerHour_WhenRangeCoversSeededPatients() {
            consultar(DESDE, HASTA)
                    .jsonPath("$.desde").isEqualTo(DESDE)
                    .jsonPath("$.hasta").isEqualTo(HASTA)
                    .jsonPath("$.filas.length()").isEqualTo(2)
                    .jsonPath("$.filas[0].hora").isEqualTo("2026-11-05T10:00:00Z")
                    .jsonPath("$.filas[0].totalRegistrados").isEqualTo(5)
                    .jsonPath("$.filas[0].conteoPorNivel.CRITICO").isEqualTo(2)
                    .jsonPath("$.filas[0].conteoPorNivel.URGENTE").isEqualTo(1)
                    .jsonPath("$.filas[0].conteoPorNivel.URGENTE_2").isEqualTo(1)
                    .jsonPath("$.filas[0].conteoPorNivel.NO_URGENTE").isEqualTo(1)
                    .jsonPath("$.filas[0].conteoPorNivel.NO_URGENTE_2").isEqualTo(0)
                    .jsonPath("$.filas[0].pacientesAtendidos").isEqualTo(3)
                    .jsonPath("$.filas[0].esperaPromedioMinutos").isEqualTo(14.0)
                    .jsonPath("$.filas[0].esperaP95Minutos").isEqualTo(28.0);
        }

        @Test
        @DisplayName("una hora sin atendidos informa espera nula pero sí sus conteos")
        void shouldReturnNullWaits_WhenNobodyWasAttendedInTheHour() {
            consultar(DESDE, HASTA)
                    .jsonPath("$.filas[1].hora").isEqualTo("2026-11-05T11:00:00Z")
                    .jsonPath("$.filas[1].totalRegistrados").isEqualTo(2)
                    .jsonPath("$.filas[1].conteoPorNivel.NO_URGENTE").isEqualTo(1)
                    .jsonPath("$.filas[1].conteoPorNivel.NO_URGENTE_2").isEqualTo(1)
                    .jsonPath("$.filas[1].conteoPorNivel.CRITICO").isEqualTo(0)
                    .jsonPath("$.filas[1].pacientesAtendidos").isEqualTo(0)
                    .jsonPath("$.filas[1].esperaPromedioMinutos").isEmpty()
                    .jsonPath("$.filas[1].esperaP95Minutos").isEmpty();
        }

        @Test
        @DisplayName("el rango es semiabierto: registrar justo en hasta queda fuera y justo antes de desde también")
        void shouldExcludePatientsOutsideTheRange_WhenOnTheBoundaries() {
            consultar(DESDE, HASTA).jsonPath("$.filas[?(@.hora=='2026-11-05T12:00:00Z')]").isEmpty()
                    .jsonPath("$.filas[?(@.hora=='2026-11-05T09:00:00Z')]").isEmpty();

            consultar(DESDE, "2026-11-05T12:00:01Z")
                    .jsonPath("$.filas.length()").isEqualTo(3)
                    .jsonPath("$.filas[2].hora").isEqualTo("2026-11-05T12:00:00Z")
                    .jsonPath("$.filas[2].totalRegistrados").isEqualTo(1)
                    .jsonPath("$.filas[2].pacientesAtendidos").isEqualTo(1)
                    .jsonPath("$.filas[2].esperaPromedioMinutos").isEqualTo(4.5)
                    .jsonPath("$.filas[2].esperaP95Minutos").isEqualTo(4.5);
        }

        @Test
        @DisplayName("un offset en la fecha equivale al mismo instante en UTC")
        void shouldGiveSameResult_WhenRangeIsExpressedWithOffset() {
            consultar("2026-11-05T05:00:00-05:00", "2026-11-05T07:00:00-05:00")
                    .jsonPath("$.filas.length()").isEqualTo(2)
                    .jsonPath("$.filas[0].esperaP95Minutos").isEqualTo(28.0);
        }

        @Test
        @DisplayName("un rango sin pacientes devuelve filas vacías")
        void shouldReturnEmptyRows_WhenNoPatientsInRange() {
            consultar("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z")
                    .jsonPath("$.filas.length()").isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Validación de parámetros")
    class Validacion {

        private WebTestClient.BodyContentSpec rechazar(String uri) {
            return webTestClient.get().uri(uri).exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA");
        }

        @Test
        @DisplayName("faltan las dos fechas: 400 nombrando ambos campos")
        void shouldRespondBadRequest_WhenParametersAreMissing() {
            rechazar("/triage/reportes/operacion")
                    .jsonPath("$.errores[0].campo").isEqualTo("desde")
                    .jsonPath("$.errores[1].campo").isEqualTo("hasta");
        }

        @Test
        @DisplayName("una fecha no ISO 8601: 400 nombrando el campo")
        void shouldRespondBadRequest_WhenDateIsNotIso() {
            rechazar("/triage/reportes/operacion?desde=ayer&hasta=" + HASTA)
                    .jsonPath("$.errores[0].campo").isEqualTo("desde");
        }

        @Test
        @DisplayName("desde igual o posterior a hasta: 400")
        void shouldRespondBadRequest_WhenRangeIsInverted() {
            rechazar("/triage/reportes/operacion?desde=" + HASTA + "&hasta=" + DESDE)
                    .jsonPath("$.errores[0].campo").isEqualTo("hasta");
            rechazar("/triage/reportes/operacion?desde=" + DESDE + "&hasta=" + DESDE);
        }

        @Test
        @DisplayName("un rango de más de 31 días: 400 y de exactamente 31 días: 200")
        void shouldRespondBadRequest_WhenRangeExceedsThirtyOneDays() {
            rechazar("/triage/reportes/operacion?desde=2026-10-01T00:00:00Z&hasta=2026-11-01T00:00:01Z")
                    .jsonPath("$.mensaje").value(mensaje -> assertThat(mensaje.toString())
                            .contains("hasta"));
            consultar("2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z").jsonPath("$.filas.length()").isEqualTo(0);
        }
    }
}
