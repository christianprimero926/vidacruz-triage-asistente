package co.com.vidacruz.asistente.consumer;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.vidacruz.asistente.repository.UrgenciasRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverPartition;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconstruccionEstado por partición")
class ReconstruccionEstadoTest {

    private static final TopicPartition P0 = new TopicPartition("triaje.eventos", 0);
    private static final TopicPartition P1 = new TopicPartition("triaje.eventos", 1);

    @Mock
    private UrgenciasRepository repository;
    @Mock
    private KafkaReceiver<String, String> receiver;
    @Mock
    private ObjectProvider<KafkaReceiver<String, String>> proveedor;
    @Mock
    private Consumer<String, String> consumer;

    private ReconstruccionEstado reconstruccion;

    @BeforeEach
    void setUp() {
        reconstruccion = new ReconstruccionEstado(repository, proveedor);
    }

    private ReceiverPartition particion(TopicPartition tp) {
        ReceiverPartition particion = mock(ReceiverPartition.class);
        when(particion.topicPartition()).thenReturn(tp);
        return particion;
    }

    private void marcasExistentes(Integer... particiones) {
        when(repository.particionesInicializadas()).thenReturn(Mono.just(Set.of(particiones)));
        StepVerifier.create(reconstruccion.prepararArranque()).verifyComplete();
    }

    /** El receiver real ejecuta la función en el hilo del consumer; aquí se aplica sobre un consumer simulado. */
    @SuppressWarnings("unchecked")
    private void offsets(Map<TopicPartition, Long> inicio, Map<TopicPartition, Long> fin) {
        when(proveedor.getObject()).thenReturn(receiver);
        when(consumer.beginningOffsets(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(inicio);
        when(consumer.endOffsets(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(fin);
        when(receiver.doOnConsumer(org.mockito.ArgumentMatchers.any(Function.class))).thenAnswer(invocacion ->
                Mono.just(((Function<Consumer<String, String>, Object>) invocacion.getArgument(0)).apply(consumer)));
    }

    @Test
    void shouldSeekToBeginningOnlyPartitionsWithoutMark_WhenSomeAreAlreadyInitialized() {
        marcasExistentes(0);
        ReceiverPartition conMarca = particion(P0);
        ReceiverPartition sinMarca = particion(P1);
        offsets(Map.of(P1, 0L), Map.of(P1, 10L));

        reconstruccion.alAsignar(List.of(conMarca, sinMarca));

        verify(conMarca, never()).seekToBeginning();
        verify(sinMarca).seekToBeginning();
    }

    @Test
    void shouldNotSeekNorMark_WhenEveryPartitionHasMark() {
        marcasExistentes(0, 1);
        ReceiverPartition p0 = particion(P0);
        ReceiverPartition p1 = particion(P1);

        reconstruccion.alAsignar(List.of(p0, p1));

        verify(p0, never()).seekToBeginning();
        verify(p1, never()).seekToBeginning();
        verify(repository, never()).marcarParticion(anyInt());
    }

    @Test
    void shouldCreateMarkOnlyWhenConsumptionReachesRecordedEndOffset() {
        marcasExistentes();
        offsets(Map.of(P0, 0L), Map.of(P0, 3L));
        when(repository.marcarParticion(0)).thenReturn(Mono.empty());
        reconstruccion.alAsignar(List.of(particion(P0)));

        StepVerifier.create(reconstruccion.alProcesar(0, 0)).verifyComplete();
        StepVerifier.create(reconstruccion.alProcesar(0, 1)).verifyComplete();
        verify(repository, never()).marcarParticion(0);

        StepVerifier.create(reconstruccion.alProcesar(0, 2)).verifyComplete();
        verify(repository).marcarParticion(0);
    }

    @Test
    void shouldNotMarkAgain_WhenMoreRecordsArriveAfterReachingTheTarget() {
        marcasExistentes();
        offsets(Map.of(P0, 0L), Map.of(P0, 1L));
        when(repository.marcarParticion(0)).thenReturn(Mono.empty());
        reconstruccion.alAsignar(List.of(particion(P0)));

        StepVerifier.create(reconstruccion.alProcesar(0, 0)).verifyComplete();
        StepVerifier.create(reconstruccion.alProcesar(0, 1)).verifyComplete();
        StepVerifier.create(reconstruccion.alProcesar(0, 2)).verifyComplete();

        verify(repository, times(1)).marcarParticion(0);
    }

    @Test
    void shouldMarkImmediately_WhenPartitionIsEmpty() {
        marcasExistentes();
        offsets(Map.of(P0, 5L), Map.of(P0, 5L));
        when(repository.marcarParticion(0)).thenReturn(Mono.empty());

        reconstruccion.alAsignar(List.of(particion(P0)));

        verify(repository).marcarParticion(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkWhenTargetArrivesAfterRecordsWereProcessed_WhenOffsetLookupIsSlow() {
        marcasExistentes();
        Sinks.Empty<Void> liberar = Sinks.empty();
        when(proveedor.getObject()).thenReturn(receiver);
        when(consumer.beginningOffsets(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Map.of(P0, 0L));
        when(consumer.endOffsets(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Map.of(P0, 2L));
        when(receiver.doOnConsumer(org.mockito.ArgumentMatchers.any(Function.class))).thenAnswer(invocacion ->
                Mono.fromSupplier(() -> ((Function<Consumer<String, String>, Object>) invocacion.getArgument(0))
                        .apply(consumer)).delaySubscription(liberar.asMono()));
        when(repository.marcarParticion(0)).thenReturn(Mono.empty());
        reconstruccion.alAsignar(List.of(particion(P0)));

        StepVerifier.create(reconstruccion.alProcesar(0, 0)).verifyComplete();
        StepVerifier.create(reconstruccion.alProcesar(0, 1)).verifyComplete();
        verify(repository, never()).marcarParticion(0);
        liberar.tryEmitEmpty();

        verify(repository).marcarParticion(0);
    }

    @Test
    void shouldForgetMarksAndPendingState_WhenConsumerIsRestarted() {
        marcasExistentes(0);
        when(repository.particionesInicializadas()).thenReturn(Mono.just(Set.of()));
        StepVerifier.create(reconstruccion.prepararArranque()).verifyComplete();
        ReceiverPartition p0 = particion(P0);
        offsets(Map.of(P0, 0L), Map.of(P0, 4L));

        reconstruccion.alAsignar(List.of(p0));

        verify(p0).seekToBeginning();
    }
}
