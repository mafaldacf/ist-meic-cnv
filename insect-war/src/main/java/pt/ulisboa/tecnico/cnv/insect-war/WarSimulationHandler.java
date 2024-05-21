package pt.ulisboa.tecnico.cnv.insectwar;

import java.io.IOException;
import java.io.OutputStream;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.util.*;
import java.net.URI;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import pt.ulisboa.tecnico.cnv.javassist.AntWarParameters;
import pt.ulisboa.tecnico.cnv.javassist.tools.ThreadICount;
import pt.ulisboa.tecnico.cnv.mss.DynamoUtils;

public class WarSimulationHandler implements HttpHandler, RequestHandler<Map<String, String>, String> {

    @Override
    public void handle(HttpExchange he) throws IOException {
        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }

        // parse request
        URI requestedUri = he.getRequestURI();
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);

        int max = Integer.parseInt(parameters.get("max"));
        int army1 = Integer.parseInt(parameters.get("army1"));
        int army2 = Integer.parseInt(parameters.get("army2"));

        Long tid = Thread.currentThread().getId();
        ThreadICount.antWarMetrics.put(new AntWarParameters(max, army1, army2, tid),List.of(-1L, -1L));

        pt.ulisboa.tecnico.cnv.insectwar.InsectWars insect_wars = new InsectWars();
        String response = insect_wars.war(max, army1, army2);

        he.sendResponseHeaders(200, response.toString().length());
        OutputStream os = he.getResponseBody();
        os.write(response.getBytes());

        os.close();
    }

    public Map<String, String> queryToMap(String query) {
        if(query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for(String param : query.split("&")) {
            String[] entry = param.split("=");
            if(entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

    private String handleRequest(int max, int army1, int army2) {
        InsectWars insect_wars = new InsectWars();
        String response = insect_wars.war(max, army1, army2);

        return response;
    }

    @Override
    public String handleRequest(Map<String,String> event, Context context) {
        int max = Integer.parseInt(event.get("max"));
        int army1 = Integer.parseInt(event.get("army1"));
        int army2 = Integer.parseInt(event.get("army2"));

        Long tid = Thread.currentThread().getId();
        if (ThreadICount.ninsts.containsKey(tid)) { // sanity check
            long ninsts = ThreadICount.ninsts.get(tid);
            long nmemoryinsts = ThreadICount.nmemoryinsts.get(tid);
            DynamoUtils.initEnvironmentLambdaInsectWar();
            DynamoUtils.tryUpdateInsectWarMetric(max, army1, army2, ninsts+nmemoryinsts);
        }

        return handleRequest(max, army1, army2);
    }
}