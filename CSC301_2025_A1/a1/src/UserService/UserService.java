
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

class User {
    int id;
    String username;
    String email;
    String password;

    User(int id, String username, String email, String password) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
    }
}

public class UserService { // All programs for UserService

    static Map<Integer, User> users = new HashMap<>();

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
            Map<String, String> userData = getRequestData(exchange);
            String command = userData.get("command");
            String s_id = userData.get("id");

            if (command == null || s_id == null) {
                // Required fields missing, send appropriate output
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            int id = Integer.parseInt(s_id);

            switch (command) {
                case "create":
                    handleCreate(exchange, userData, id);
                    break;
                case "update":
                    handleUpdate(exchange, userData, id);
                    break;
                case "delete":
                    handleDelete(exchange, userData, id);
                    break;
                default:
                    // not sure what should go here
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                    break;
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            // == Incomeplete ==

            String path = exchange.getRequestURI().getPath();
            String suffix = path.substring("/user/".length());

            int id;
            try {
                id = Integer.parseInt(suffix);
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            User u = users.get(id);
            if (u == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            String json = "{\"id\":" + u.id + ",\"username\":\"" + u.username + "\",\"email\":\"" + u.email + "\"}";
            sendResponse(exchange, json);
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

        private static Map<String, String> getRequestData(HttpExchange exchange) throws IOException {
            Map<String, String> data = new HashMap<>();

            // Read the stream into a String first
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String body = sb.toString().trim();
            if (body.isEmpty() || !body.startsWith("{")) return data;

            // Manual parsing: strip braces and split into pairs
            String content = body.substring(1, body.length() - 1);
            String[] pairs = content.split(",");

            if (content.trim().isEmpty()) return data;

            for (String pair : pairs) {
                String[] parts = pair.split(":", 2); // Split only on the first colon
                if (parts.length == 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim().replace("\"", "");
                    data.put(key, value);
                }
            }
            return data;
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

        private static void handleCreate(HttpExchange exchange, Map<String, String> data, int id) throws IOException {
            String username = data.get("username");
            String email = data.get("email");
            String password = data.get("password");

            // If there is missing field, we need to send error and close
            if (username == null || email == null || password == null) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            User newUser = new User(id, username, email, password);
            users.put(id, newUser);

        }

        private static void handleUpdate(HttpExchange exchange, Map<String, String> data, int id) throws  IOException {
            User existingUser = users.get(id);

            if (existingUser == null) { // Return error user DNE (Not found)
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            if (data.containsKey("username")) existingUser.username = data.get("username");
            if (data.containsKey("email")) existingUser.email = data.get("email");
            if (data.containsKey("password")) existingUser.password = data.get("password");

            // Updated all fields at this point, need appropriate output

        }

        private static void handleDelete(HttpExchange exchange, Map<String, String> data, int id) throws IOException {
            User existingUser = users.get(id);

            if (existingUser == null) { // Return error, user DNE (Not found)
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }


            String username = data.get("username");
            String email = data.get("email");
            String password = data.get("password");

            if (username == null || email == null || password == null) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            if (username.equals(existingUser.username) && email.equals(existingUser.email) &&
                    password.equals(existingUser.password)) {
                users.remove(id); // Removed, send appropriate response
                exchange.sendResponseHeaders(200, 0); // success
                exchange.close();
                return;
            }

            // Mismatch
            exchange.sendResponseHeaders(400, 0);
            exchange.close();
            return;
        }

    }
}
