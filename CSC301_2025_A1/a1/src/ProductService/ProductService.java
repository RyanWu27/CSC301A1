import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class ProductService {
    public static void main(String[] args) throws Exception {
        System.out.println("ProductService starting...");

        int port = 14002; // temporary, we will read config.json later
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.start();

        System.out.println("ProductService listening on port " + port);
    }
}
