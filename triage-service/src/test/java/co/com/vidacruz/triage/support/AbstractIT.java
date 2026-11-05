package co.com.vidacruz.triage.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import co.com.vidacruz.triage.dto.request.PacienteRequestDTO;
import co.com.vidacruz.triage.dto.response.PacienteRegistradoResponseDTO;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Contexto completo contra PostgreSQL y Kafka reales, compartidos por todas las pruebas de integración. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureObservability
@Import({TestClockConfig.class, TestWebClientConfig.class})
public abstract class AbstractIT {

    protected static final String TOPIC = "triaje.eventos";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
        crearTopicConTresParticiones();
    }

    @DynamicPropertySource
    static void configurarInfraestructura(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                POSTGRES.getHost(), POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("triage.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected WebTestClient webTestClient;
    @Autowired
    protected DatabaseClient databaseClient;
    @Autowired
    protected MutableClock clock;

    /** TRUNCATE no dispara el trigger de inmutabilidad de la auditoría, a diferencia de DELETE. */
    @BeforeEach
    void limpiarEstado() {
        databaseClient.sql("TRUNCATE paciente, outbox_evento, alerta_medica CASCADE").then().block();
        clock.fijar(TestClockConfig.INICIO);
    }

    protected UUID crearPaciente(PacienteRequestDTO request) {
        return webTestClient.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PacienteRegistradoResponseDTO.class)
                .returnResult().getResponseBody().getId();
    }

    protected WebTestClient.ResponseSpec moverPaciente(UUID id, String estado) {
        return webTestClient.patch().uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"estado\":\"" + estado + "\"}")
                .exchange();
    }

    protected Long contar(String tabla) {
        return databaseClient.sql("SELECT COUNT(*) FROM " + tabla)
                .map(fila -> fila.get(0, Long.class)).one().block();
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    /** Pausar el contenedor conserva su puerto, a diferencia de pararlo, y simula un Kafka que no responde. */
    protected static void pausarKafka() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    protected static void reanudarKafka() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void crearTopicConTresParticiones() {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo crear el topic de pruebas", error);
        }
    }
}
