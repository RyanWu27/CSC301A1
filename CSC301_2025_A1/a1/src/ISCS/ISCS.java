import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class ISCS {
    public static void main(String[] args) throws Exception {
        System.out.println("ISCS starting...");

        int port = 14000; // temporary
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.start();

        System.out.println("ISCS listening on port " + port);
    }
}
