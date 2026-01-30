
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.util.HashMap;
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
        this.password = password; // Already Hashed
    }
}

public class UserService { // All programs for UserService

    static Map<Integer, User> users = new HashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("UserService starting...");

        if (args.length != 1) { // Not hardcode port
            System.err.println("Usage: java UserService config.json");
            return;
        }

        String configPath = args[0];

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(configPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        String config = sb.toString();

        int port = extractPort(config, "UserService");

        if (port <= 0) {
            System.err.println("Invalid port in config.json");
            return;
        }

        // Literally Server creation
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        // Routers that help us handle different paths
        server.createContext("/user", new UserHandler());
        server.createContext("/user/", new UserHandler());

        server.start();
        System.out.println("Server started on port " + port);
    }

    private static int extractPort(String json, String serviceName) {
        int i = json.indexOf(serviceName);
        if (i < 0) return -1;

        int p = json.indexOf("port", i);
        if (p < 0) return -1;

        int colon = json.indexOf(":", p);
        int j = colon + 1;

        while (j < json.length() && !Character.isDigit(json.charAt(j))) j++;

        int start = j;
        while (j < json.length() && Character.isDigit(json.charAt(j))) j++;

        if (start == j) return -1;

        return Integer.parseInt(json.substring(start, j));
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
            String command = stripQuotes(userData.get("command"));
            String s_id = stripQuotes(userData.get("id"));

            if (command == null || s_id == null) {
                // Required fields missing, send appropriate output
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            int id;
            try {
                id = Integer.parseInt(s_id);
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

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
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {

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

            User u = users.get(id); // The user we already have
            if (u == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            String json = "{\"id\":" + u.id
                    + ",\"username\":\"" + u.username
                    + "\",\"email\":\"" + u.email
                    + "\",\"password\":\"" + u.password + "\"}";
            sendResponse(exchange, json);
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
            if (body.length() < 2 || !body.startsWith("{") || !body.endsWith("}")) {
                return data;
            }

            // Manual parsing: strip braces and split into pairs
            String content = body.substring(1, body.length() - 1);
            String[] pairs = content.split(",");

            if (content.trim().isEmpty()) return data;

            for (String pair : pairs) {
                String[] parts = pair.split(":", 2); // Split only on the first colon
                if (parts.length == 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim();
                    data.put(key, value);
                }
            }
            return data;
        }

        private static void sendResponse(HttpExchange exchange, String response) throws IOException {

            // Turns response string into bytes, and then send header show it works, next make a stream
            // Use the stream we write back the bytes.
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        }

        private static void handleCreate(HttpExchange exchange, Map<String, String> data, int id) throws IOException {

            String rawUsername = data.get("username");
            String rawEmail = data.get("email");
            String rawPassword = data.get("password");

            // If there is missing field, we need to send error and close
            if (rawUsername == null || rawEmail == null || rawPassword == null) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            // Check if email is valid email format (Not integers)
            boolean emailWasQuoted = rawEmail.trim().startsWith("\"") && rawEmail.trim().endsWith("\"");
            if (!emailWasQuoted) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            String username = stripQuotes(rawUsername);
            String email = stripQuotes(rawEmail);
            String password = stripQuotes(rawPassword);


            // Validate emptiness after stripping
            if (username.trim().isEmpty() || email.trim().isEmpty() || password.trim().isEmpty()) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            // If user is already there, we can't create
            if (users.get(id) != null) {
                exchange.sendResponseHeaders(409, 0);
                exchange.close();
                return;
            }

            // Successfully Created new user
            String hashed = sha256LowerHex(password);
            User newUser = new User(id, username, email, hashed);
            users.put(id, newUser);

            String json = "{\"id\":" + newUser.id
                    + ",\"username\":\"" + newUser.username
                    + "\",\"email\":\"" + newUser.email
                    + "\",\"password\":\"" + newUser.password + "\"}";
            sendResponse(exchange, json);
        }

        private static void handleUpdate(HttpExchange exchange, Map<String, String> data, int id) throws  IOException {
            User existingUser = users.get(id);

            if (existingUser == null) { // Return error user DNE (Not found)
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            if (data.containsKey("username")) {
                String v = stripQuotes(data.get("username"));
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                    return;
                }
                existingUser.username = v;
            }
            if (data.containsKey("email")) {
                String v = data.get("email");
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                    return;
                }
                if (!(v.startsWith("\"") && v.endsWith("\"")) || v.length() <= 2) { // Check valid email
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                    return;
                }
                existingUser.email = stripQuotes(v);
            }
            if (data.containsKey("password")) {
                String v = stripQuotes(data.get("password"));
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                    return;
                }
                existingUser.password = sha256LowerHex(v);
            }

            // Updated all fields at this point, need appropriate output
            String json = "{\"id\":" + existingUser.id
                    + ",\"username\":\"" + existingUser.username
                    + "\",\"email\":\"" + existingUser.email
                    + "\",\"password\":\"" + existingUser.password + "\"}";
            sendResponse(exchange, json);
        }

        private static void handleDelete(HttpExchange exchange, Map<String, String> data, int id) throws IOException {
            User existingUser = users.get(id); // These are what we already have for a User

            if (existingUser == null) { // Return error, user DNE (Not found)
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }


            // These are from the client JSON
            String username = stripQuotes(data.get("username"));
            String email = stripQuotes(data.get("email"));
            String password = stripQuotes(data.get("password"));

            if (username == null || username.trim().isEmpty() ||
                    email == null || email.trim().isEmpty() ||
                    password == null || password.trim().isEmpty()) {

                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            if (username.equals(existingUser.username) && email.equals(existingUser.email) &&
                    sha256LowerHex(password).equals(existingUser.password)) {
                users.remove(id); // Removed the user, send appropriate response
                sendResponse(exchange, "{}"); // success
                return;
            }

            // Mismatch
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        }

    }

    // SHA256 password formatting
    private static String sha256LowerHex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Stripping quotes for the correct format to store as User
    private static String stripQuotes(String s) { // For fixing integer email issue
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

}
