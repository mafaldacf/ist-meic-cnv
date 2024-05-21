package pt.ulisboa.tecnico.cnv.awsmanagement;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.services.connect.model.DescribeInstanceResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import pt.ulisboa.tecnico.cnv.mss.DynamoUtils;

public class AutoScaler {
    private static String INSTANCE_AMI_ID;
    private static String KEY_PAIR_NAME;
    private static String SECURITY_GROUP_ID;


    private static final double MIN_CPU_UTILIZATION = 15.0;
    private static final double MAX_CPU_UTILIZATION = 85.0;
    private static final double SCALE_FACTOR = 0.1;
    private static final int MAX_INSTANCES = 100;
    private static final int MIN_INSTANCES = 1;

    // Total observation time in milliseconds.
    private static final long OBS_TIME = 1000 * 60 * 5; // 5 minutes

    private static final int PERIOD_TIME = 60; // 60 seconds

    // Auto Scaler
    private static final long SLEEP_TIME = 1000 * 60;

    private static final int HEARTBEAT_SLEEP_INTERVAL = 1000; // 1 second

    private static HashMap<String,Double> instancesCpuUtilization;

    private static boolean isScalingUp = false;


    public AutoScaler() {
    }

    public static boolean isScalingUp() {
        return isScalingUp;
    }

    public static void init() throws IOException, ParseException {
        loadConfigValues();
        // Using lambda expression to create a Runnable object
        Runnable task = () -> {
            while (true) {
                try {
                    System.out.println("\n-----------------------------------");
                    System.out.println("-------- Going to autoscale --------");
                    System.out.println("------------------------------------\n");
                    autoscale();
                    System.out.println("Sleeping for " + SLEEP_TIME/1000 + " seconds...");
                    Thread.sleep(SLEEP_TIME);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        // Creating and starting a new thread using the Runnable object
        Thread thread = new Thread(task);
        thread.start();
    }

    private static void loadConfigValues() throws IOException, ParseException {
        ClassLoader loader = DynamoUtils.class.getClassLoader();
        InputStream inputStream = loader.getResourceAsStream("config.json");
        assert inputStream != null;
        Reader reader = new BufferedReader(new InputStreamReader(inputStream));
        JSONParser jsonParser = new JSONParser();
        JSONObject configJson = (JSONObject) jsonParser.parse(reader);

        INSTANCE_AMI_ID = (String) configJson.get("INSTANCE_AMI_ID");
        KEY_PAIR_NAME = (String) configJson.get("KEY_PAIR_NAME");
        SECURITY_GROUP_ID = (String) configJson.get("SECURITY_GROUP_ID");
    }

    public static void initRunningInstances() {
        Set<Instance> instances = Utility.getInstances();

        if (instances.isEmpty()) {
            System.out.println("There are currently 0 instances running. Going to create instances...");
            createInstances(1);
        } else {
            System.out.println("There are currently " + instances.size() + " instances running. Going to load their information...");
            for (Instance instance : instances) {
                String id = instance.getInstanceId();
                String publicIp = instance.getNetworkInterfaces().get(0).getAssociation().getPublicIp();
                Utility.instances.put(id, new InstanceProperties(publicIp, id));
            }
        }
    }

    public static void autoscale() {

        // Only get instances that are not going to terminate (i.e., running)
        Set<String> instanceIds = Utility.instances.entrySet().stream()
                .filter(entry -> entry.getValue().isRunning())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // first initialization and unexpectedly having no instances running
        if (instanceIds.isEmpty()) {
            initRunningInstances();
            instanceIds = Utility.instances.entrySet().stream()
                    .filter(entry -> entry.getValue().isRunning())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }

        instancesCpuUtilization = new HashMap<>();

        double cpuUtilization = 0.0;

        List<String> deadInstancesIds = getDeadInstances();
        if(!deadInstancesIds.isEmpty())
            terminateInstancesById(deadInstancesIds);

        for (String instanceId : instanceIds) {
            double instanceCpuUtilization = getCpuUtilization(Utility.cloudWatch, instanceId);
            instancesCpuUtilization.put(instanceId, instanceCpuUtilization);
            cpuUtilization += instanceCpuUtilization;

            System.out.println("Instance ID: " + instanceId + " | CPU Utilization: " + instanceCpuUtilization + "%");
        }

        int size = Utility.instances.size();
        double averageCpuUtilization = cpuUtilization/size;

        int numOfInstances = (int)(Math.ceil(size*SCALE_FACTOR));

        // Create or delete instances based on average CPU utilization
        if (averageCpuUtilization > MAX_CPU_UTILIZATION && size < MAX_INSTANCES) {
            createInstances(numOfInstances);
        } else if (averageCpuUtilization < MIN_CPU_UTILIZATION && size > MIN_INSTANCES) {
            terminateInstances(numOfInstances);
        }
    }

    private static double getCpuUtilization(AmazonCloudWatch cloudWatch, String instanceId) {
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest().withStartTime(new Date(new Date().getTime() - OBS_TIME))
                .withNamespace("AWS/EC2")
                .withPeriod(PERIOD_TIME)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
                .withEndTime(new Date());

        GetMetricStatisticsResult result = cloudWatch.getMetricStatistics(request);
        List<Datapoint> datapoints = result.getDatapoints();
        System.out.println("Datapoints for instance " + instanceId + ": " + datapoints);
        if (!datapoints.isEmpty()) {

            // CloudWatch datapoints are not chronologically ordered so we need to manually compute the latest one
            Datapoint latestDp = datapoints.get(0);
            for (Datapoint dp : datapoints) {
                if (dp.getTimestamp().after(latestDp.getTimestamp())) {
                    latestDp = dp;
                }
            }
            return latestDp.getAverage();
        }
        return 0.0;
    }

    public static void createInstances(int numOfInstances) {

        System.out.println("Creating instance(s)...");

        isScalingUp = true;
        System.out.println("[AUTOSCALER] scaling up...");

        // Create instance(s)
        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(INSTANCE_AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(numOfInstances)
                .withMaxCount(numOfInstances)
                .withKeyName(KEY_PAIR_NAME)
                .withSecurityGroupIds(SECURITY_GROUP_ID)
                .withMonitoring(true);

        // Launch instance(s)
        RunInstancesResult result = Utility.ec2.runInstances(request);

        List<Instance> instances = result.getReservation().getInstances();

        for(Instance instance : instances) {
            String instanceId = instance.getInstanceId();

            boolean initializing = true;
            while(initializing) {
                DescribeInstancesRequest describeRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
                DescribeInstancesResult instanceResult = Utility.ec2.describeInstances(describeRequest);
                instance = instanceResult.getReservations().get(0).getInstances().get(0);
                String state = instance.getState().getName();
                if (state.equals("running"))
                    initializing = false;
            }

            // Sanity check
            while (instance.getNetworkInterfaces().size() == 0) {
                DescribeInstancesRequest describeRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
                DescribeInstancesResult instanceResult = Utility.ec2.describeInstances(describeRequest);
                instance = instanceResult.getReservations().get(0).getInstances().get(0);
            }

            String ip = instance.getNetworkInterfaces().get(0).getAssociation().getPublicIp();

            while (!Utility.sendHeartbeat(ip)) {
                try {
                    Thread.sleep(HEARTBEAT_SLEEP_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Instance " + instance.getInstanceId() + " running!");

            Utility.instances.put(instance.getInstanceId(), new InstanceProperties(ip, instance.getInstanceId()));
        }
        isScalingUp = false;
        System.out.println("[AUTOSCALER] scaling up done!");
    }

    public static void terminateInstances(int numberOfInstances) {
        List<String> instanceIds = getInstancesToTerminate(numberOfInstances);

        // Sets status of instances to be terminating
        for(String instanceId : instanceIds) {
            InstanceProperties instance = Utility.instances.get(instanceId);
            instance.setOnHoldToTerminate();
            Utility.instances.put(instanceId, instance);
        }

        while(true) {
            int count = 0;
            for (String instanceId : Utility.instances.keySet()) {
                InstanceProperties instance = Utility.instances.get(instanceId);
                if (instance.isHoldingToTerminate() && instance.getWorkload() == 0) {
                    // to be set by the LB
                    count++;
                }
            }
            if (count == numberOfInstances) break;
            try {
                Thread.sleep(HEARTBEAT_SLEEP_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        terminateInstancesById(instanceIds);
    }

    private static List<String> getInstancesToTerminate(int numberOfInstances) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(instancesCpuUtilization.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> entry1, Map.Entry<String, Double> entry2) {
                // Compare the values of the entries
                return Double.compare(entry1.getValue(), entry2.getValue());
            }
        });

        List<String> instanceIds = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries) {
            instanceIds.add(entry.getKey());
            if(instanceIds.size() == numberOfInstances) break;
        }
        return instanceIds;
    }

    private static List<String> getDeadInstances() {
        List<String> instanceIds = new ArrayList<>();
        for (String instanceId : Utility.instances.keySet()) {
            if (Utility.instances.get(instanceId).isDead()) // to be set by the LB
                instanceIds.add(Utility.instances.get(instanceId).getId());
        }
        return instanceIds;
    }

    public static void terminateInstancesById(List<String> instancesIds) {
        // Create a TerminateInstancesRequest with the instance IDs
        TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(instancesIds);

        for(String instanceId : instancesIds) {
            Utility.instances.remove(instanceId);
            System.out.println("Terminating instance(s) " + instanceId + "...");
        }

        Utility.ec2.terminateInstances(request);

        for(String instanceId : instancesIds)
            System.out.println("Instance " + instanceId + " terminated!");
    }
}
