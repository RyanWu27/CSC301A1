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


class Product {
    int id;
    String productname;
    String description;
    float price;
    int quantity;

    Product(int id, String productname, String description, float price, int quantity) {
        this.id = id;
        this.productname = productname;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
    }
}

public class ProductService {

    static Map<Integer, Product> products = new HashMap<>();

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
                    + ",\"productname\":\"" + p.productname + "\""
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

            String productname = data.get("productname");
            String description = data.get("description");
            String sPrice = data.get("price");
            String sQty = data.get("quantity");


            // If there is missing field, we need to send error and close
            if (productname == null || description == null || sPrice == null || sQty == null) {
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

            Product newProduct = new Product(id, productname, description, priceVal, qtyVal);
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

            if (data.containsKey("productname")) existingProduct.productname = data.get("productname");
            if (data.containsKey("description")) existingProduct.description = data.get("description");
            if (data.containsKey("price")) {
                try { existingProduct.price = Float.parseFloat(data.get("price")); }
                catch (Exception e) { exchange.sendResponseHeaders(400,0); exchange.close(); return; }
            }
            if (data.containsKey("quantity")) {
                try { existingProduct.quantity = Integer.parseInt(data.get("quantity")); }
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

            String productname = data.get("productname");
            String description = data.get("description");
            String sPrice = data.get("price");
            String sQty = data.get("quantity");

            if (productname == null || description == null || sPrice == null || sQty == null) {
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

            if (productname.equals(existingProduct.productname) &&
                    description.equals(existingProduct.description) &&
                    existingProduct.price == priceVal &&
                    existingProduct.quantity == qtyVal) {

                products.remove(id);
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(400, 0);
            exchange.close();

        }

    }
}
