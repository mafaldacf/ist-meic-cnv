package pt.ulisboa.tecnico.cnv.awsmanagement;

import com.amazonaws.util.IOUtils;
import pt.ulisboa.tecnico.cnv.mss.DynamoUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class LoadBalancer {

    public enum RequestType {
        FOXES_RABBIT,
        INSECT_WAR,
        COMPRESS_IMAGE
    }
    private static final long MAX_WORKLOAD_EC2 = 10000000000L; // 10.000.000.000 // 18.640.028.000
    private static final double MAX_WORKLOAD_LAMBDA = 0.20 * MAX_WORKLOAD_EC2;
    private static final int  MAX_CONCURRENT_REQUESTS = 150;
    private static final int MAX_RETRIES_FORWARD_REQUEST = 3;

    synchronized private static void finishRequest(String instanceId, long workload) {
        InstanceProperties instance = Utility.instances.get(instanceId);
        instance.removeWorkload(workload);
        instance.removeRequest();
        Utility.instances.put(instance.getId(), instance);
    }

    synchronized private static void registerRequest(String instanceId, long workload) {
        InstanceProperties instance = Utility.instances.get(instanceId);
        instance.addWorkload(workload);
        instance.addRequest();
        Utility.instances.put(instance.getId(), instance);
    }

    synchronized static InstanceProperties getEC2Instance(long workload) {
        while (true) {
            if (!Utility.instances.isEmpty()) {
                List<InstanceProperties> availableInstances = new ArrayList<>();
                for (ConcurrentHashMap.Entry<String, InstanceProperties> entry : Utility.instances.entrySet()) {
                    if (entry.getValue().isRunning()) {
                        availableInstances.add(entry.getValue());
                    }
                }
                availableInstances.sort(Comparator.comparingLong(InstanceProperties::getWorkload));

                if (!availableInstances.isEmpty()) {
                    if (AutoScaler.isScalingUp()) {
                        for (InstanceProperties instance : availableInstances) {
                            if (instance.getRequests() <= MAX_CONCURRENT_REQUESTS
                                    && workload + instance.getWorkload() <= MAX_WORKLOAD_EC2 ) {

                                registerRequest(instance.getId(), workload);
                                return instance;
                            }
                        }
                        // if no instance can process the request, we resort to lambdas
                        return null;
                    }
                    else {
                        InstanceProperties instance = availableInstances.get(0);
                        registerRequest(instance.getId(), workload);
                        return instance;
                    }
                }
            }
            else {
                System.out.println("[INFO] No EC2 instances available to forward the request");
                return null;
            }
        }
    }


    public static String processRequest(RequestType requestType, String uri, Map<String,String> params, byte[] requestBodyBytes) throws IOException {

        long workload = estimateWorkload(requestType, params);
        while (true) {
            try {
                InstanceProperties instance = getEC2Instance(workload);
                if (instance != null) {
                    System.out.println("[EC2] Forwarding request to EC2 instance...");
                    String response = forwardRequestEC2Instance(instance, uri, requestBodyBytes, workload);
                    finishRequest(instance.getId(), workload);

                    if (response != null) {
                        return response;
                    }
                }
                if (workload <= MAX_WORKLOAD_LAMBDA) {
                    System.out.println("[LAMBDA] Forwarding request to lambda function...");
                    String response = LambdaUtils.invokeFunction(requestType, params);
                    if (response != null) {
                        return response;
                    }
                }
            } catch (IOException e) {
                System.out.println("[ERROR] Unexpected exception caught: " + e.getMessage());
            }
        }
    }

    private static int computeDynamicReadTimeout(long workload) {
        if (workload > MAX_WORKLOAD_EC2) {
            return 240000; // 4 minutes
        }
        if (workload > MAX_WORKLOAD_EC2/2) {
            return 120000; // 2 minutes
        }
        return 60000; // 1 minute
    }

    private static String forwardRequestEC2Instance(InstanceProperties instance, String uri, byte[] requestBody, long workload) throws IOException {
        String response = "";

        URL url = new URL("http://" + instance.getIp() + ":8000" + uri);
        System.out.println("Forwarding request to " + url);

        HttpURLConnection connection = null;
        OutputStream os = null;
        int retries = 0;
        while (retries < MAX_RETRIES_FORWARD_REQUEST) {
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(computeDynamicReadTimeout(workload));
                connection.setConnectTimeout(30000);

                // compress image
                if (requestBody != null) {
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    os = connection.getOutputStream();
                    os.write(requestBody);
                    os.close();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = connection.getInputStream();
                    response = IOUtils.toString(is);
                } else {
                    System.out.println("NOK: " + connection.getResponseMessage());
                    response = connection.getResponseMessage();
                }
                connection.disconnect();
                return response;
            } catch (IOException e) {
                System.out.println("[ERROR] [FORWARDING] Connection exception: " + e.getMessage());
                instance = Utility.instances.get(instance.getId());
                if (!Utility.sendHeartbeat(instance.getIp())) {
                    instance = Utility.instances.get(instance.getId());
                    instance.setDead();
                    Utility.instances.put(instance.getId(), instance);
                    System.out.println("[LOAD BALANCER] setting instance " + instance.getId() + " as dead");
                    return null;
                }
            } finally {
                if (connection != null) connection.disconnect();
                if (os != null) os.close();
            }
            retries++;
        }
        return response;
    }

    private static long estimateWorkload(RequestType requestType, Map<String,String> params) throws IOException {
        if (requestType.equals(RequestType.FOXES_RABBIT)) {
            return estimateWorkloadFoxesRabbits(params);
        }
        if (requestType.equals(RequestType.INSECT_WAR)) {
            return estimateWorkloadInsectWar(params);
        }
        if (requestType.equals(RequestType.COMPRESS_IMAGE)) {
            return estimateWorkloadCompressImage(params);
        }
        return -1;
    }

    private static long estimateWorkloadFoxesRabbits(Map<String,String> params) {
        int generations = Integer.parseInt(params.get("generations"));
        int world = Integer.parseInt(params.get("world"));
        int scenario = Integer.parseInt(params.get("scenario"));
        long metric = DynamoUtils.getFoxesRabbitsMetric(world, scenario);
        return metric*generations;
    }

    private static long estimateWorkloadInsectWar(Map<String,String> params) {
        int max = Integer.parseInt(params.get("max"));
        int army1 = Integer.parseInt(params.get("army1"));
        int army2 = Integer.parseInt(params.get("army2"));
        long metric = DynamoUtils.getInsectWarMetric(army1, army2);
        return metric*max*(army1+army2);
    }

    private static long estimateWorkloadCompressImage(Map<String,String> params) throws IOException {
        String targetFormat = params.get("targetFormat");
        float compressionFactor = Float.parseFloat(params.get("compressionFactor"));
        String body = params.get("body");

        byte[] decoded = Base64.getDecoder().decode(body);
        ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
        BufferedImage bi = ImageIO.read(bais);
        int pixels = bi.getHeight() * bi.getWidth();

        float metric = DynamoUtils.getCompressImageMetric(targetFormat, compressionFactor);
        return (long) (metric*pixels);
    }
}