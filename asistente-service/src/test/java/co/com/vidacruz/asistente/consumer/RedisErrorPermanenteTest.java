package co.com.vidacruz.asistente.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisConnectionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.RedisConnectionFailureException;

@DisplayName("RedisErrorPermanente")
class RedisErrorPermanenteTest {

    @Test
    void shouldBePermanent_WhenCommandIsRejectedWithWrongType() {
        assertThat(RedisErrorPermanente.esPermanente(
                new RedisCommandExecutionException("WRONGTYPE Operation against a key"))).isTrue();
    }

    @Test
    void shouldBePermanent_WhenCommandFailsWithOutOfMemoryEvenIfWrappedBySpring() {
        Throwable envuelto = new InvalidDataAccessApiUsageException("OOM",
                new RedisCommandExecutionException("OOM command not allowed when used memory > 'maxmemory'"));

        assertThat(RedisErrorPermanente.esPermanente(envuelto)).isTrue();
    }

    @Test
    void shouldNotBePermanent_WhenServerIsLoadingOrBusy() {
        assertThat(RedisErrorPermanente.esPermanente(new RedisCommandExecutionException("LOADING dataset"))).isFalse();
        assertThat(RedisErrorPermanente.esPermanente(new RedisCommandExecutionException("BUSY script"))).isFalse();
    }

    @Test
    void shouldNotBePermanent_WhenConnectionOrTimeoutFails() {
        assertThat(RedisErrorPermanente.esPermanente(new RedisConnectionFailureException("caída"))).isFalse();
        assertThat(RedisErrorPermanente.esPermanente(new RedisConnectionException("sin conexión"))).isFalse();
        assertThat(RedisErrorPermanente.esPermanente(new TimeoutException("lento"))).isFalse();
    }
}
