package co.com.vidacruz.triage.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final Instant INICIO = Instant.parse("2026-11-05T15:00:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(INICIO);
    }
}
