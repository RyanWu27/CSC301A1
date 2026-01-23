import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class UserService {
    public static void main(String[] args) throws Exception {
        System.out.println("UserService starting...");

        int port = 14001; // temporary hardcoded port

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.start();

        System.out.println("UserService listening on port " + port);
    }
}
