package pt.ulisboa.tecnico.cnv.webserver;

import java.io.IOException;
import java.net.InetSocketAddress;

import Threads.AntWarThread;
import Threads.CompressionThread;
import Threads.FoxRabbitThread;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;

public class WebServer {
    private final static int SLEEP_DURATION = 15000;
    public final static String FOXRABBITTABLE = "FoxRabbitMetrics";
    public final static String COMPRESSIONTABLE = "CompressionMetrics";
    public final static String ANTWARTABLE = "AntWarMetrics";


    public void serve() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());

        server.createContext("/", new RootHandler());
        server.createContext("/simulate", new SimulationHandler());
        server.createContext("/compressimage", new CompressImageHandlerImpl());
        server.createContext("/insectwar", new WarSimulationHandler());


        launchDynamoThread();
        server.start();
    }


    private void launchDynamoThread()
    {
        new Thread(() -> {
            /* --------------- */
            /* RUNNING LOCALLY */
            /* --------------- */
            // ProfileCredentialsProvider credentials = new ProfileCredentialsProvider();
            // AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_2).withCredentials(credentials).build();

            /* -------------------- */
            /* RUNNING IN THE CLOUD */
            /* -------------------- */
            EnvironmentVariableCredentialsProvider credentials = new EnvironmentVariableCredentialsProvider();
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_2).withCredentials(credentials).build();
            DynamoDB dynamoDB = new DynamoDB(client);

            Table foxRabbitDynamoMetrics = dynamoDB.getTable(FOXRABBITTABLE);
            Table antWarDynamoMetrics = dynamoDB.getTable(ANTWARTABLE);
            Table compressionDynamoMetrics = dynamoDB.getTable(COMPRESSIONTABLE);

            while(true)
            {
                try {
                    Thread.sleep(SLEEP_DURATION);

                        new FoxRabbitThread(foxRabbitDynamoMetrics, client).run();
                        new AntWarThread(antWarDynamoMetrics,client).run();
                        new CompressionThread(compressionDynamoMetrics, client).run();

                } catch (InterruptedException e) {
                    System.err.println("[ERROR] Failure sleeping : " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("[ERROR] Unexpected error in metrics thread: " + e.getMessage());
                }
            }

        }).start();
    }


    public static void main(String[] args) throws Exception {
        WebServer webServer = new WebServer();
        try
        {
            webServer.serve();
        }
        catch (IOException e)
        {
            System.out.println("-------------------------- ERRO NO MAIN ----------------------------------");
            System.err.println(e);
        }
    }
}
