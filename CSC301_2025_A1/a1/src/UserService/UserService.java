
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

        // Literally Server creation
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        // Routers that help us handle different paths
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

            // Handles differently based on the method
            if ("POST".equals(method) && path.equals("/user")) {
                handlePost(exchange);
                return;
            }

            if ("GET".equals(method) && path.startsWith("/user/")) {
                handleGet(exchange);
                return;
            }

            // If something cannot be handled, there is error
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            String body = getRequestBody(exchange);

            // == Incomeplete ==

            // If it is not create, then it is error and we close request
            if (!body.contains("\"command\":\"create\"")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            // Trying to get the id, so we can put the body into this id location
            int id = Integer.parseInt(body.replaceAll("\\D+", ""));
            users.put(id, body);

            sendResponse(exchange, "User created");
        }

        private void handleGet(HttpExchange exchange) throws IOException {

            // == Incomeplete ==

            String path = exchange.getRequestURI().getPath();
            int id = Integer.parseInt(path.substring("/user/".length()));

            // Check if id is in our hashmaps, if not it is not found error
            if (!users.containsKey(id)) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            // return the user if it exists
            sendResponse(exchange, users.get(id));
        }

        private static String getRequestBody(HttpExchange exchange) throws IOException {
            // Remember there is polymorphism, might want to change function name
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }

        private static void sendResponse(HttpExchange exchange, String response) throws IOException {

            // Turns response string into bytes, and then send header show it works, next make a stream
            // Use the stream we write back the bytes.
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();

        }
    }
}
