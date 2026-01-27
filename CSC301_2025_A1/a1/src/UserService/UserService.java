
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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
            JsonNode body = getRequestBodyAsNode(exchange);

            String command = body.get("command").asText();
            String command = body.get("id").asInt();

            if (!(command)) { exchange.sendResponseHeaders(400, 0); }

            switch (command) {
                case "create":
                    handleCreate(exchange, body, id);
                    break;
                case "update":
                    handleUpdate(exchange, body, id);
                    break;
                case "delete":
                    handleDelete(exchange, body, id);
                    break;
                default:
                    sendResponse(exchange, 400, "{}");
            }


            // == Incomeplete ==

            // If it is not create, then it is error and we close request
            if (!body.contains("\"command\":\"create\"")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            Integer id = jsonGetInt(body, "id");
            String username = jsonGetString(body, "username");
            String email = jsonGetString(body, "email");
            String password = jsonGetString(body, "password");

            // Trying to get the id, so we can put the body into this id location
            if (id == null || username == null || email == null || password == null) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            users.put(id, new User(id, username, email, password));
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
            User u = users.get(id);
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

        private static JsonNode getRequestBodyAsNode(HttpExchange exchange) throws IOException {
            ObjectMapper mapper = new ObjectMapper();
            // Directly parses the InputStream into a tree structure (JsonNode)
            return mapper.readTree(exchange.getRequestBody());
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

        private static void handleCreate(HttpExchange exchange, JsonNode body, int id) {
            String username = body.get("username").asString();
            String email = body.get("email").asString();
            String password = body.get("password").asString();

            User newUser = new User(id, username, email, String password);
            users.put(id, newUser);

        }

        private static void handleUpdate(HttpExchange exchange, JsonNode body, int id) {
            User existingUser = users.get(id);

            if (existingUser == null) { // Return error user DNE
            }

            if body.has("username") {
                existingUser.setUsername(body.get("username").asString());
            }
            if body.has("email") {
                existingUser.setEmail(body.get("email").asString());
            }
            if body.has("password") {
                existingUser.setPassword(body.get("password").asString());
            }

        }

        private static void handleDelete(HttpExchange exchange, JsonNode body, int id) {
            User existingUser = users.get(id);

            if (existingUser == null) { // Return error user DNE
            }

            users.remove(id); // Removed
        }

    }
}
