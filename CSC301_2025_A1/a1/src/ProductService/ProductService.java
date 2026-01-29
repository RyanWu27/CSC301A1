import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;


class Product {
    int id;
    String name;
    String description;
    float price;
    int quantity;

    Product(int id, String name, String description, float price, int quantity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
    }
}

public class ProductService {

    static Map<Integer, Product> products = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("ProductService starting...");

        if (args.length != 1) { // Not hardcode port
            System.err.println("Usage: java ProductService config.json");
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

        int port = extractPort(config, "ProductService");

        if (port <= 0) {
            System.err.println("Invalid port in config.json");
            return;
        }

        // Creation
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.createContext("/product", new ProductHandler());
        server.createContext("/product/", new ProductHandler());

        server.start();
        System.out.println("ProductService listening on port " + port);
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

        private void handlePost(HttpExchange exchange) throws IOException {

            Map<String, String> productData = getRequestData(exchange);
            String command = productData.get("command");
            String s_id = productData.get("id");

            if (command == null || s_id == null) {
                // Required fields missing, send appropriate output
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            int id;
            try { // If id is not a number, there is error
                id = Integer.parseInt(s_id);
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }


            switch (command) {
                case "create":
                    handleCreate(exchange, productData, id);
                    break;
                case "update":
                    handleUpdate(exchange, productData, id);
                    break;
                case "delete":
                    handleDelete(exchange, productData, id);
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
            String suffix = path.substring("/product/".length());

            int id;
            try {
                id = Integer.parseInt(suffix);
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            Product p = products.get(id);
            if (p == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            String json = "{\"id\":" + p.id
                    + ",\"name\":\"" + p.name + "\""
                    + ",\"description\":\"" + p.description + "\""
                    + ",\"price\":" + p.price
                    + ",\"quantity\":" + p.quantity
                    + "}";
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
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        }

        private static void handleCreate(HttpExchange exchange, Map<String, String> data, int id) throws IOException {

            String name = data.get("name");
            String description = data.get("description");
            String sPrice = data.get("price");
            String sQty = data.get("quantity");


            // If there is missing field, we need to send error and close
            if (name == null || name.trim().isEmpty() ||
                    description == null || description.trim().isEmpty() ||
                    sPrice == null || sPrice.trim().isEmpty() ||
                    sQty == null || sQty.trim().isEmpty()) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            float priceVal;
            int qtyVal;
            try {
                priceVal = Float.parseFloat(sPrice);
                qtyVal = Integer.parseInt(sQty);
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            if (products.get(id) != null) { // Duplicated id
                exchange.sendResponseHeaders(409, 0);
                exchange.close();
                return;
            }

            Product newProduct = new Product(id, name, description, priceVal, qtyVal);
            products.put(id, newProduct);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();


        }

        private static void handleUpdate(HttpExchange exchange, Map<String, String> data, int id) throws IOException {
            Product existingProduct = products.get(id);

            if (existingProduct == null) { // Return error user DNE (Not found)
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            if (data.containsKey("name")) {
                String v = data.get("name");
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0); exchange.close(); return; }
                existingProduct.name = v;
            }
            if (data.containsKey("description")) {
                String v = data.get("description");
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0); exchange.close(); return; }
                existingProduct.description = v;
            }
            if (data.containsKey("price")) {
                String v = data.get("price");
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0); exchange.close(); return; }
                try { existingProduct.price = Float.parseFloat(v); }
                catch (Exception e) { exchange.sendResponseHeaders(400,0); exchange.close(); return; }
            }
            if (data.containsKey("quantity")) {
                String v = data.get("quantity");
                if (v == null || v.trim().isEmpty()) { exchange.sendResponseHeaders(400, 0); exchange.close(); return; }
                try { existingProduct.quantity = Integer.parseInt(v); }
                catch (Exception e) { exchange.sendResponseHeaders(400,0); exchange.close(); return; }
            }

            // Updated all fields at this point, need appropriate output
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        }

        private static void handleDelete(HttpExchange exchange, Map<String, String> data, int id) throws IOException {
            Product existingProduct = products.get(id);

            if (existingProduct == null) { // Return error, user DNE (Not found)
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            String name = data.get("name");
            String description = data.get("description");
            String sPrice = data.get("price");
            String sQty = data.get("quantity");

            if (name == null || name.trim().isEmpty() ||
                    description == null || description.trim().isEmpty() ||
                    sPrice == null || sPrice.trim().isEmpty() ||
                    sQty == null || sQty.trim().isEmpty()) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            float priceVal;
            int qtyVal;
            try {
                priceVal = Float.parseFloat(sPrice);
                qtyVal = Integer.parseInt(sQty);
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }

            if (name.equals(existingProduct.name) &&
                    description.equals(existingProduct.description) &&
                    Float.compare(existingProduct.price, priceVal) == 0 &&
                    existingProduct.quantity == qtyVal) {

                products.remove(id);
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            // Mismatch
            exchange.sendResponseHeaders(404, 0);
            exchange.close();

        }

    }
}
