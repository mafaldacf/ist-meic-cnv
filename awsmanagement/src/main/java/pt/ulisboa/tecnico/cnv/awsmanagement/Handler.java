package pt.ulisboa.tecnico.cnv.awsmanagement;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Handler implements HttpHandler {

    public Handler() {
    }

    @Override
    public void handle(HttpExchange he) throws IOException, NumberFormatException {
        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }

        // Parse request
        URI requestedUri = he.getRequestURI();
        String path = requestedUri.getRawPath();
        String uri = requestedUri.toString();
        String query = requestedUri.getRawQuery();

        String response;
        System.out.println("Processing request: " + query);
        if (path.contains("simulate")) {
            Map<String, String> params = queryToMap(query);
            response = LoadBalancer.processRequest(LoadBalancer.RequestType.FOXES_RABBIT, uri, params, null);
            System.out.println("Returning simulate response...");
        }
        else if (path.contains("insectwar")) {
            Map<String, String> params = queryToMap(query);
            response = LoadBalancer.processRequest(LoadBalancer.RequestType.INSECT_WAR, uri, params, null);
            System.out.println("Returning insect war response...");
        }
        else if (path.contains("compressimage")) {
            InputStream requestBodyStream = he.getRequestBody();
            String requestBody = new BufferedReader(new InputStreamReader(requestBodyStream)).lines().collect(Collectors.joining("\n"));
            byte[] requestBodyBytes = requestBody.getBytes();
            HashMap<String, String> params = new HashMap<>();
            String[] resultSplits  = requestBody.split(",");
            params.put("targetFormat", resultSplits[0].split(":")[1].split(";")[0]);
            params.put("compressionFactor", resultSplits[0].split(":")[2].split(";")[0]);
            params.put("body", resultSplits[1]);

            response = LoadBalancer.processRequest(LoadBalancer.RequestType.COMPRESS_IMAGE, uri, params, requestBodyBytes);
            System.out.println("Returning compression response...");
        }
        else {
            response = "Internal Server Error";
            he.sendResponseHeaders(500, response.length());
        }

        he.sendResponseHeaders(200, response.length());
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
}