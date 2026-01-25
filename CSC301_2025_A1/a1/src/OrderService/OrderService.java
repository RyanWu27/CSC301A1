import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class OrderService {
    public static void main(String[] args) throws Exception {
        System.out.println("OrderService starting...");

        int port = 14003; // temporary
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.start();

        System.out.println("OrderService listening on port " + port);
    }
}
