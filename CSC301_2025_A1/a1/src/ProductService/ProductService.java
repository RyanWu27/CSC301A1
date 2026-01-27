import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class ProductService {

    static Map<Integer, String> users = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("ProductService starting...");

        int port = 14002; // temporary, we will read config.json later

        // Creation
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.createContext("/product", new ProductHandler());
        server.createContext("/product/", new ProductHandler());

        server.start();
        System.out.println("ProductService listening on port " + port);
    }

    static class ProductHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (method.equals("POST") && path.equals("/product")) {
                handlePost(exchange);
                return;
            }

            if (method.equals("GET") && path.startsWith("/product/")) {
                handleGet(exchange);
                return;
            }

            // If something cannot be handled, there is error
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        }

        private void handlePost(HttpExchange exchange) throws IOException {}

        private void handleGet(HttpExchange exchange) throws IOException {}

    }
}
