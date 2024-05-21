package pt.ulisboa.tecnico.cnv.awsmanagement;

import java.net.InetSocketAddress;
import java.util.Objects;

import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.mss.DynamoUtils;


public class Main {
    private static int port = 8000;
    public static void main(String[] args) throws Exception {
        System.out.println("\n--------------- Initializing Load Balancer & Auto Scaler ---------------\n");

        Utility.init();
        DynamoUtils.init();
        LambdaUtils.init();
        AutoScaler.init();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());

        Handler handler = new Handler();
        server.createContext("/simulate", handler);
        server.createContext("/compressimage", handler);
        server.createContext("/insectwar", handler);
        server.start();
        System.out.println("LB listening on localhost:" + port + "...\n");



    }
}
