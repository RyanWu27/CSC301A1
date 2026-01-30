import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class OrderService {

    public static void main(String[] args) throws Exception {
        System.out.println("OrderService starting...");

        if (args.length != 1) {
            System.err.println("Usage: java OrderService config.json");
            return;
        }

        String config = readFile(args[0]);
        int port = extractPort(config, "OrderService");
        if (port <= 0) {
            System.err.println("Invalid port in config.json");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.createContext("/order", new OrderHandler(config));

        server.createContext("/user", new UserProxyHandler(config));
        server.createContext("/product", new ProductProxyHandler(config));

        server.start();
        System.out.println("OrderService listening on port " + port);
    }

    static class UserProxyHandler implements HttpHandler {
        private final int userPort;

        UserProxyHandler(String config) {
            this.userPort = extractPort(config, "UserService");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            proxyRequest(exchange, userPort);
        }
    }

    static class ProductProxyHandler implements HttpHandler {
        private final int productPort;

        ProductProxyHandler(String config) {
            this.productPort = extractPort(config, "ProductService");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            proxyRequest(exchange, productPort);
        }
    }

    /**
     * Shared logic to forward requests from OrderService to User/Product services
     */
    private static void proxyRequest(HttpExchange exchange, int targetPort) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().toString();
        String targetUrl = "http://localhost:" + targetPort + path;

        HttpResult result;
        if ("GET".equals(method)) {
            result = httpGet(targetUrl);
        } else {
            String body = readRequestBody(exchange);
            result = httpPostJson(targetUrl, body);
        }

        sendJson(exchange, result.code, result.body);
    }

    static class OrderHandler implements HttpHandler {
        private final int userPort;
        private final int productPort;

        OrderHandler(String config) {
            this.userPort = extractPort(config, "UserService");
            this.productPort = extractPort(config, "ProductService");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("POST".equals(method) && (path.equals("/order") || path.equals("/order/"))) {
                handlePlaceOrder(exchange);
                return;
            }

            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }

        private void handlePlaceOrder(HttpExchange exchange) throws IOException {
            Map<String, String> data = getRequestData(exchange);
            String command = data.get("command");

            if (command == null || !command.equals("place order")) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            String sUserId = data.get("user_id");
            String sProductId = data.get("product_id");
            String sQty = data.get("quantity");

            if (sUserId == null || sProductId == null || sQty == null) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                return;
            }

            try {
                int userId = Integer.parseInt(sUserId);
                int productId = Integer.parseInt(sProductId);
                int qty = Integer.parseInt(sQty);

                if (qty <= 0) {
                    sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                    return;
                }

                HttpResult userRes = httpGet("http://localhost:" + userPort + "/user/" + userId);
                if (userRes.code != 200) {
                    sendJson(exchange, userRes.code == 404 ? 404 : 400, "{\"status\":\"Invalid Request\"}");
                    return;
                }

                HttpResult prodRes = httpGet("http://localhost:" + productPort + "/product/" + productId);
                if (prodRes.code != 200) {
                    sendJson(exchange, prodRes.code == 404 ? 404 : 400, "{\"status\":\"Invalid Request\"}");
                    return;
                }

                Map<String, String> prodJson = parseFlatJsonObject(prodRes.body);
                int stock = Integer.parseInt(prodJson.getOrDefault("quantity", "0"));

                if (qty > stock) {
                    sendJson(exchange, 409, "{\"status\":\"Exceeded quantity limit\"}");
                    return;
                }

                String updateBody = String.format("{\"command\":\"update\",\"id\":%d,\"quantity\":%d}", productId, (stock - qty));
                HttpResult updateRes = httpPostJson("http://localhost:" + productPort + "/product", updateBody);

                if (updateRes.code == 200) {
                    sendJson(exchange, 200, String.format("{\"product_id\":%d,\"user_id\":%d,\"quantity\":%d,\"status\":\"Success\"}", productId, userId, qty));
                } else {
                    sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
                }

            } catch (Exception e) {
                sendJson(exchange, 400, "{\"status\":\"Invalid Request\"}");
            }
        }
    }

    // --- Helper Functions ---

    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
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
        return (start == j) ? -1 : Integer.parseInt(json.substring(start, j));
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static Map<String, String> getRequestData(HttpExchange exchange) throws IOException {
        return parseFlatJsonObject(readRequestBody(exchange));
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private static Map<String, String> parseFlatJsonObject(String body) {
        Map<String, String> out = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return out;
        String content = body.trim().replaceAll("[{}]", "");
        for (String pair : content.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                out.put(parts[0].trim().replace("\"", ""), parts[1].trim().replace("\"", ""));
            }
        }
        return out;
    }

    private static HttpResult httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        return new HttpResult(code, readConnBody(conn, code));
    }

    private static HttpResult httpPostJson(String urlStr, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        return new HttpResult(code, readConnBody(conn, code));
    }

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

    static class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }
}