package co.com.vidacruz.asistente.consumer;

/**
 * Avance de la relectura de una partición. El offset objetivo llega de forma asíncrona (se consulta al consumer),
 * por eso puede conocerse antes o después de que se procesen los primeros registros.
 */
class ProgresoParticion {

    private long ultimoProcesado = -1;
    private Long objetivo;

    /** @return true si con este offset la relectura quedó completa. */
    synchronized boolean registrar(long offset) {
        ultimoProcesado = Math.max(ultimoProcesado, offset);
        return completa();
    }

    /** @return true si la partición ya está completa: estaba vacía o ya se procesó hasta el objetivo. */
    synchronized boolean fijarObjetivo(long inicio, long fin) {
        objetivo = fin;
        return inicio >= fin || completa();
    }

    private boolean completa() {
        return objetivo != null && ultimoProcesado + 1 >= objetivo;
    }
}
