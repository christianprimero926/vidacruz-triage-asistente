package co.com.vidacruz.asistente.consumer;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverPartition;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconstruccionEstado")
class ReconstruccionEstadoTest {

    @Mock
    private UrgenciasRepository repository;
    @Mock
    private ReceiverPartition particion;

    private ReconstruccionEstado reconstruccion;

    @BeforeEach
    void setUp() {
        reconstruccion = new ReconstruccionEstado(repository);
    }

    @Test
    void shouldCreateKeyAndSeekToBeginning_WhenInitializationKeyIsMissing() {
        when(repository.inicializado()).thenReturn(Mono.just(false));
        when(repository.marcarInicializado()).thenReturn(Mono.empty());

        StepVerifier.create(reconstruccion.prepararArranque()).verifyComplete();
        reconstruccion.alAsignar(List.of(particion));

        verify(repository).marcarInicializado();
        verify(particion).seekToBeginning();
    }

    @Test
    void shouldKeepCommittedOffsets_WhenInitializationKeyExists() {
        when(repository.inicializado()).thenReturn(Mono.just(true));

        StepVerifier.create(reconstruccion.prepararArranque()).verifyComplete();
        reconstruccion.alAsignar(List.of(particion));

        verify(repository, never()).marcarInicializado();
        verify(particion, never()).seekToBeginning();
    }

    @Test
    void shouldSeekOnlyOnce_WhenPartitionsAreAssignedAgain() {
        when(repository.inicializado()).thenReturn(Mono.just(false));
        when(repository.marcarInicializado()).thenReturn(Mono.empty());

        StepVerifier.create(reconstruccion.prepararArranque()).verifyComplete();
        reconstruccion.alAsignar(List.of(particion));
        reconstruccion.alAsignar(List.of(particion));

        verify(particion).seekToBeginning();
    }
}
