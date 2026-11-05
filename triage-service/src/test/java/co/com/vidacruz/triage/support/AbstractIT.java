package co.com.vidacruz.triage.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/** Contexto completo contra un PostgreSQL real, compartido por todas las pruebas de integración. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurarBaseDeDatos(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                POSTGRES.getHost(), POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
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
}
