
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

public class UserService { // All programs for UserService

    static Map<Integer, String> users = new HashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("UserService starting...");

        int port = 14001; // temporary hardcoded port

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.createContext("/user", new UserHandler());
        server.createContext("/user/", new UserHandler());

        server.start();
        System.out.println("Server started on port " + port);
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("POST".equals(method) && path.equals("/user")) {
                handlePost(exchange);
                return;
            }

            if ("GET".equals(method) && path.startsWith("/user/")) {
                handleGet(exchange);
                return;
            }

            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            String body = getRequestBody(exchange);

            if (!body.contains("\"command\":\"create\"")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            int id = Integer.parseInt(body.replaceAll("\\D+", ""));
            users.put(id, body);

            sendResponse(exchange, "User created");
        }

        private void handleGet(HttpExchange exchange) throws IOException {

            String path = exchange.getRequestURI().getPath();
            int id = Integer.parseInt(path.substring("/user/".length()));

            if (!users.containsKey(id)) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            sendResponse(exchange, users.get(id));
        }

        private static String getRequestBody(HttpExchange exchange) throws IOException {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }

        private static void sendResponse(HttpExchange exchange, String response) throws IOException {

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();

        }
    }
}
