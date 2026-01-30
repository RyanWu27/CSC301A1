import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class OrderService {

    public static void main(String[] args) throws Exception {

        // Difference Order vs User/Product: OrderService reads config.json once and reuses the information.
        //UserService and ProductService read it once and then forget it.

        System.out.println("OrderService starting...");

        if (args.length != 1) {
            System.err.println("Usage: java OrderService config.json");
            return;
        }

        String config = readFile(args[0]); // Basically the same thing as UserService line 42 to 51.
        int port = extractPort(config, "OrderService");
        if (port <= 0) {
            System.err.println("Invalid port in config.json");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.createContext("/order", new OrderHandler(config));
        server.createContext("/order/", new OrderHandler(config));

        server.start();
        System.out.println("OrderService listening on port " + port);
    }

    // Main logic, handle happens
    static class OrderHandler implements HttpHandler {
        private final int userPort;
        private final int productPort;

        OrderHandler(String config) { // Extract ports from config
            this.userPort = extractPort(config, "UserService");
            this.productPort = extractPort(config, "ProductService");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException { // See if it is a valid request
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("POST".equals(method) && "/order".equals(path)) {
                handlePlaceOrder(exchange);
                return;
            }

            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        }

        private void handlePlaceOrder(HttpExchange exchange) throws IOException { // Place Order logic
            Map<String, String> data = getRequestData(exchange); // parse json body

            // Check if command is "place order"
            String command = data.get("command");
            if (command == null || !command.equals("place order")) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            String sUserId = data.get("user_id");
            String sProductId = data.get("product_id");
            String sQty = data.get("quantity");

            // Check if all these fields exists
            if (sUserId == null || sProductId == null || sQty == null) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Converting strings into integers
            int userId, productId, qty;
            try {
                userId = Integer.parseInt(sUserId);
                productId = Integer.parseInt(sProductId);
                qty = Integer.parseInt(sQty);
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Quantity must be non-negative
            if (qty <= 0) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Check UserService if user exists
            HttpResult userRes = httpGet("http://localhost:" + userPort + "/user/" + userId);
            if (userRes.code == 404) {
                sendJson(exchange, 404, "{\"status\":\"Invalid Request\"}");
                return;
            }
            if (userRes.code != 200) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Check if product exists and get their current quantity
            HttpResult prodRes = httpGet("http://localhost:" + productPort + "/product/" + productId);
            if (prodRes.code == 404) {
                sendJson(exchange, 404, "{\"status\":\"Invalid Request\"}");
                return;
            }
            if (prodRes.code != 200) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Getting the quantity of the product
            Map<String, String> prodJson = parseFlatJsonObject(prodRes.body); // Reads stock from the JSON body
            String sStock = prodJson.get("quantity");
            if (sStock == null) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Convert the string into integer
            int stock;
            try {
                stock = Integer.parseInt(sStock);
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Ordered quantity cannot be more than the available stocks of a product
            if (qty > stock) {
                sendJson(exchange, 409, "{\"status\":\"Exceeded quantity limit\"}");
                return;
            }

            // Updating ProductService quantity
            int newStock = stock - qty; // New Stock after the order

            // Update product quantity by POST /product with command "update"
            // This is information about Product
            String updateBody =
                    "{"
                            + "\"command\":\"update\","
                            + "\"id\":" + productId + ","
                            + "\"quantity\":" + newStock
                            + "}";

            // Send JSON info to ProductService
            HttpResult updateRes = httpPostJson("http://localhost:" + productPort + "/product", updateBody);
            if (updateRes.code != 200) {
                // If update failed, treat as invalid request
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            // Return successful response
            // This is information about Order
            String ok =
                    "{"
                            + "\"product_id\":" + productId + ","
                            + "\"user_id\":" + userId + ","
                            + "\"quantity\":" + qty + ","
                            + "\"status\":\"Success\""
                            + "}";
            sendJson(exchange, 200, ok);
        }
    }

    // Helper Functions

    // It reads config.json once making a string that OrderService can reuse
    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        // FileReader opens the file at path and put it in a BufferedReader
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {

            // Store line by line what is in the BufferedReader into StringBuilder
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString(); // Return the string of what we read
    }

    // extracting port numbers from JSON file
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

    // Converts the HTTP request body which is the JSON text into a usable hashmap
    private static Map<String, String> getRequestData(HttpExchange exchange) throws IOException {
        Map<String, String> data = new HashMap<>();

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

        String content = body.substring(1, body.length() - 1).trim();
        if (content.isEmpty()) return data;

        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim().replace("\"", "");
                String value = parts[1].trim().replace("\"", "");
                data.put(key, value);
            }
        }
        return data;
    }

    // Sends an HTTP response as JSON to client
    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    // Very simple “flat JSON object” parser for responses like:
    // {"id":2000,"name":"x","description":"y","price":1.2,"quantity":90}
    // Parse JSON that came back from another service over HTTP
    private static Map<String, String> parseFlatJsonObject(String body) {
        Map<String, String> out = new HashMap<>();
        if (body == null) return out;
        body = body.trim();
        if (body.length() < 2 || !body.startsWith("{") || !body.endsWith("}")) return out;

        String content = body.substring(1, body.length() - 1).trim();
        if (content.isEmpty()) return out;

        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) continue;
            String key = parts[0].trim().replace("\"", "");
            String value = parts[1].trim().replace("\"", "");
            out.put(key, value);
        }
        return out;
    }

    static class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    // Sending a Get Request
    private static HttpResult httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        String body = readConnBody(conn, code);
        conn.disconnect();
        return new HttpResult(code, body);
    }

    // Send a Post Request with JSON
    private static HttpResult httpPostJson(String urlStr, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        String body = readConnBody(conn, code);
        conn.disconnect();
        return new HttpResult(code, body);
    }

    // Reads the HTTP response body from another service
    private static String readConnBody(HttpURLConnection conn, int code) throws IOException {
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
