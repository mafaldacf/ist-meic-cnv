package pt.ulisboa.tecnico.cnv.awsmanagement;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ServiceException;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class LambdaUtils {

    private static AWSLambda lambdaClient;
    private static final String FOXES_RABBITS_FUNCTION_NAME = "foxes-rabbits";
    private static final String INSECT_WAR_FUNCTION_NAME = "insect-war";

    private static final String COMPRESS_IMAGE_FUNCTION_NAME = "compress-image";

    public static String invokeFunction(LoadBalancer.RequestType requestType, Map<String, String> params) {
        String functionName = null;

        switch (requestType) {
            case FOXES_RABBIT:
                functionName = FOXES_RABBITS_FUNCTION_NAME;
                break;
            case INSECT_WAR:
                functionName = INSECT_WAR_FUNCTION_NAME;
                break;
            case COMPRESS_IMAGE:
                functionName = COMPRESS_IMAGE_FUNCTION_NAME;
                break;
        }

        if (functionName != null) {
            try {
                String jsonString = JSONObject.toJSONString(params);

                com.amazonaws.services.lambda.model.InvokeRequest request = new InvokeRequest()
                        .withFunctionName(functionName)
                        .withPayload(jsonString);

                InvokeResult result = lambdaClient.invoke(request);
                String output = new String(result.getPayload().array(), StandardCharsets.UTF_8);

                // lambda functions return string in json format
                // so we have to remove the quotation marks manually
                if (!output.contains("errorMessage")) {
                    if (requestType.equals(LoadBalancer.RequestType.COMPRESS_IMAGE)) {
                        return String.format("data:image/%s;base64,%s", params.get("targetFormat"),
                                output.substring(1, output.length() - 1));
                    }
                    else {
                        // workaround: lambda functions return string in json format with quoted chars
                        // hence, we have to remove the quotation marks manually
                        // 1. remove the first and last chars which are extra quotes
                        // 2. replace \" by "
                        return output.substring(1, output.length() - 1).replace("\\\"", "\"");
                    }
                }
                else {
                    System.out.println("[ERROR] Failure sending request to lambda function: " + output);
                }

            } catch(ServiceException e) {
                System.err.println(e.getMessage());
            }
        }
        return null;
    }

    public static void init() {
        ProfileCredentialsProvider credentials = new ProfileCredentialsProvider();
        lambdaClient = AWSLambdaClient.builder()
                .withCredentials(credentials)
                .withRegion(Regions.US_EAST_2)
                .build();
    }
}
