package co.com.vidacruz.triage.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Webhook de mentira: guarda lo que recibe y responde con el estado y la demora que el test configure. */
public class FakeWebhookServer {

    private final HttpServer server;
    private final ConcurrentLinkedQueue<String> cuerpos = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> contentTypes = new ConcurrentLinkedQueue<>();
    private final AtomicInteger estado = new AtomicInteger(200);
    private final AtomicLong demoraMs = new AtomicLong();

    public FakeWebhookServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo levantar el webhook de prueba", error);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/hook", intercambio -> {
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentTypes.add(String.valueOf(intercambio.getRequestHeaders().getFirst("Content-Type")));
            dormir(demoraMs.get());
            intercambio.sendResponseHeaders(estado.get(), -1);
            intercambio.close();
        });
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/hook";
    }

    public List<String> cuerpos() {
        return List.copyOf(cuerpos);
    }

    public List<String> contentTypes() {
        return List.copyOf(contentTypes);
    }

    public void responder(int codigo, long demora) {
        estado.set(codigo);
        demoraMs.set(demora);
    }

    public void reiniciar() {
        cuerpos.clear();
        contentTypes.clear();
        responder(200, 0);
    }

    public void detener() {
        server.stop(0);
    }

    private void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
