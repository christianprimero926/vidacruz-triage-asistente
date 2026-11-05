package co.com.vidacruz.asistente.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class RedisSchedulerConfig {

    /**
     * Spring Data Redis abre la conexión compartida de forma síncrona en el primer uso: se aísla en un scheduler
     * propio para que nunca ocurra en el event loop, ni siquiera si Redis estaba caído al arrancar.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler redisScheduler() {
        return Schedulers.newBoundedElastic(2, 1000, "redis");
    }
}
