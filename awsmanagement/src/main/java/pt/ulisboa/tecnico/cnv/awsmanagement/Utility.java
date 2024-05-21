package pt.ulisboa.tecnico.cnv.awsmanagement;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Utility {
    private final static String AWS_REGION = "us-east-2";
    protected static AmazonEC2 ec2;
    protected static AmazonCloudWatch cloudWatch;
    private static String iid;

    private static final int HEARTBEAT_TIMEOUT_SECONDS = 5000;

    protected static ConcurrentHashMap<String, InstanceProperties> instances; // <iid, ip>


    public static void init() {
        iid =  System.getenv("IID");
        System.out.println("Running LB and AS on EC2 instance id: " + iid);

        instances = new ConcurrentHashMap<>();

        loadEc2();
        loadCloudWatch();
    }

    private static void loadEc2() {
        // profile credentials provider loads credentials file from home/<user>/.aws/credentials
        // requires running command 'aws configure' using AWS CLI
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(credentialsProvider).build();
    }

    private static void loadCloudWatch() {
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(AWS_REGION).withCredentials(credentialsProvider).build();
    }

    public static Set<Instance> getInstances() {
        Set<Instance> instances = new HashSet<>();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            instances.addAll(reservation.getInstances());
        }

        // Remove instances that are not running and current instance where the LB and AS are running
        instances.removeIf(instance ->
                !instance.getState().getName().equals("running")
                        || instance.getInstanceId().equals(iid));

        return instances;
    }

    public static boolean sendHeartbeat(String ip) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + ip + ":8000/");
            System.out.println("Sending heartbeat to " + url);

            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HEARTBEAT_TIMEOUT_SECONDS);
            connection.setReadTimeout(HEARTBEAT_TIMEOUT_SECONDS);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return (responseCode == HttpURLConnection.HTTP_OK);
        } catch(MalformedURLException m) {
            System.out.println("[ERROR] [HEARTBEAT] Malformed URL: " + m.getMessage());
        } catch (IOException e) {
            System.out.println("[ERROR] [HEARTBEAT] Connection exception: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return false;
    }
}
