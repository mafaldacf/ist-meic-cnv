package pt.ulisboa.tecnico.cnv.mss;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.*;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class DynamoUtils {

    private static final long RANDOM_WORKLOAD = 10000;

    private static JSONObject tablesJson;

    // ----------------------
    // DynamoDB Table Helpers
    // ----------------------
    private static final String FOXES_RABBITS_TABLE_NAME = "FoxRabbitMetrics";
    private static final String INSECT_WAR_TABLE_NAME = "AntWarMetrics";
    private static final String IMAGE_COMPRESSION_TABLE_NAME = "CompressionMetrics";
    private static final String FOXES_RABBITS_HASH_KEY = "WorldScenario";
    private static final String INSECT_WAR_HASH_KEY = "Ratio";
    private static final String IMAGE_COMPRESSION_HASH_KEY = "DestinationFactor";

    // ---------------------
    // DynamoDB AWS Entities
    // ---------------------

    private static DynamoDB dynamoClient;
    private static Table foxesRabbitsTable;
    private static Table insectWarTable;
    private static Table imageCompressionTable;

    private static void fillFoxesRabbitsTable(JSONArray json) {
        ArrayList<Long> worlds = new ArrayList<>(Arrays.asList(1L, 2L, 3L, 4L));
        ArrayList<Long> scenarios = new ArrayList<>(Arrays.asList(1L, 2L, 3L));
        for (long world : worlds) {
            for (long scenario : scenarios) {
                JSONObject values = (JSONObject) json.get((int) (world-1));
                long work = (long) values.get("work");
                long generations = (long) values.get("generations");
                long metric = work / generations;
                String hashKey = buildFoxesRabbitsHashKey(world, scenario);
                Item newItem = new Item()
                        .withPrimaryKey(FOXES_RABBITS_HASH_KEY, hashKey)
                        .withLong("Metric", metric)
                        .withLong("Gen_Min", generations)
                        .withLong("Gen_Max", generations)
                        .withLong("Inst_Min", work)
                        .withLong("Inst_Max", work);

                foxesRabbitsTable.putItem(newItem);
            }
        }
    }

    private static void fillInsectWarTable(JSONObject json) {
        long max = (long) json.get("max");
        long army1 = (long) json.get("army1");
        long army2 = (long) json.get("army2");
        long work = (long) json.get("work");
        long roundArmy = max*(army1+army2);
        long metric = work/roundArmy;
        ArrayList<Long> ratios = new ArrayList<>(Arrays.asList(2L, 4L, 6L, 8L, 10L));
        for (long ratio : ratios) {
            String hashKey = buildInsectWarHashKeyFromRatio(ratio);
            Item newItem = new Item()
                    .withPrimaryKey(INSECT_WAR_HASH_KEY, hashKey)
                    .withLong("Metric", metric)
                    .withLong("RoundArmy_Min", roundArmy)
                    .withLong("RoundArmy_Max", roundArmy)
                    .withLong("Inst_Min", work)
                    .withLong("Inst_Max", work);

            insectWarTable.putItem(newItem);
        }
    }

    private static void fillImageCompressionTable(JSONArray json) {
        ArrayList<Float> factors = new ArrayList<>(Arrays.asList(0.0F, 0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 1.0F));
        ArrayList<String> targets = new ArrayList<>(Arrays.asList("JPEG", "Deflate", "BI_RGB"));
        int numTargets = targets.size();
        for (int targetIndex = 0; targetIndex < numTargets; targetIndex++) {
            for (float factor : factors) {
                JSONObject values = (JSONObject) json.get(targetIndex);
                long pixels = (long) values.get("pixels");
                long work = (long) values.get("work");
                float metric = ((float) work)/((float) pixels);
                String hashKey = buildImageCompressionHashKey(targets.get(targetIndex), factor);
                Item newItem = new Item()
                        .withPrimaryKey(IMAGE_COMPRESSION_HASH_KEY, hashKey)
                        .withFloat("Metric", metric)
                        .withLong("Size_Min", pixels)
                        .withLong("Size_Max", pixels)
                        .withLong("Inst_Min", work)
                        .withLong("Inst_Max", work);
                imageCompressionTable.putItem(newItem);

                if (targets.get(targetIndex).equals("BI_RGB")) {
                    // BI_RGB/BMP has a global metric for all factors
                    break;
                }
            }
        }
    }

    private static void fillTable(Table table, String tableName) throws IOException, ParseException {
        if (tableName.equals(FOXES_RABBITS_TABLE_NAME)) {
            JSONArray jsonArray = (JSONArray) tablesJson.get(tableName);
            foxesRabbitsTable = table;
            fillFoxesRabbitsTable(jsonArray);
        }
        if (tableName.equals(INSECT_WAR_TABLE_NAME)) {
            JSONObject jsonObj = (JSONObject) tablesJson.get(tableName);
            insectWarTable = table;
            fillInsectWarTable(jsonObj);
        }
        else if (tableName.equals(IMAGE_COMPRESSION_TABLE_NAME)) {
            JSONArray jsonArray = (JSONArray) tablesJson.get(tableName);
            imageCompressionTable = table;
            fillImageCompressionTable(jsonArray);
        }
    }

    private static Table loadTable(String tableName, String hashKey) throws InterruptedException, IOException, ParseException {
        Table table = dynamoClient.getTable(tableName);
        try {
            table.describe();
            System.out.println("[DynamoDB] Loaded table '" + tableName + "'.");
        } catch (ResourceNotFoundException e) {
            System.out.println("[DynamoDB] Creating new '" + tableName + "' table...");
            CreateTableRequest request = new CreateTableRequest()
                    .withTableName(tableName)
                    .withKeySchema(new KeySchemaElement(hashKey, KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition(hashKey, ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L));
            table = dynamoClient.createTable(request);
            table.waitForActive();
            System.out.println("[DynamoDB] Filling '" + tableName + "' table...");
            fillTable(table, tableName);
        }
        return table;
    }

    public static void init() throws InterruptedException, IOException, ParseException {
        ProfileCredentialsProvider credentials = new ProfileCredentialsProvider();
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_2).withCredentials(credentials).build();
        dynamoClient = new DynamoDB(client);

        ClassLoader loader = DynamoUtils.class.getClassLoader();
        InputStream inputStream = loader.getResourceAsStream("tables.json");
        assert inputStream != null;
        Reader reader = new BufferedReader(new InputStreamReader(inputStream));
        JSONParser jsonParser = new JSONParser();
        tablesJson = (JSONObject) jsonParser.parse(reader);

        foxesRabbitsTable = loadTable(FOXES_RABBITS_TABLE_NAME, FOXES_RABBITS_HASH_KEY);
        insectWarTable = loadTable(INSECT_WAR_TABLE_NAME, INSECT_WAR_HASH_KEY);
        imageCompressionTable = loadTable(IMAGE_COMPRESSION_TABLE_NAME, IMAGE_COMPRESSION_HASH_KEY);
    }
    public static void initEnvironmentLambdaFoxesRabbits() {
        EnvironmentVariableCredentialsProvider credentials = new EnvironmentVariableCredentialsProvider();
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_2).withCredentials(credentials).build();
        DynamoDB dynamoClient = new DynamoDB(client);
        foxesRabbitsTable = dynamoClient.getTable(FOXES_RABBITS_TABLE_NAME);
    }

    public static void initEnvironmentLambdaInsectWar() {
        EnvironmentVariableCredentialsProvider credentials = new EnvironmentVariableCredentialsProvider();
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_2).withCredentials(credentials).build();
        insectWarTable = new DynamoDB(client).getTable(INSECT_WAR_TABLE_NAME);
    }

    private static String buildFoxesRabbitsHashKey(long world, long scenario) {
        return world + ":" + scenario;
    }

    private static String buildInsectWarHashKey(long army1, long army2) {
        long ratio = army1 > army2 ? (long)(army1/army2 + 0.5) : (long)(army2/army1 + 0.5);
        ratio = ratio % 2 == 0 ? ratio : ratio + 1;
        return ratio > 8 ? "<=infinity" : "<=" + ratio;
    }

    private static String buildInsectWarHashKeyFromRatio(long ratio) {
        ratio = ratio % 2 == 0 ? ratio : ratio + 1;
        return ratio > 8 ? "<=infinity" : "<=" + ratio;
    }

    private static String buildImageCompressionHashKey(String targetFormat, float compressionFactor) {
        if (targetFormat.equals("BI_RGB")) {
            return targetFormat;
        }
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        return targetFormat + ":" + decimalFormat.format(compressionFactor);
    }

    public static long getFoxesRabbitsMetric(int world, int scenario) {
        String hashKey = buildFoxesRabbitsHashKey(world, scenario);
        GetItemSpec getItemSpec = new GetItemSpec()
                .withPrimaryKey(FOXES_RABBITS_HASH_KEY, hashKey)
                .withAttributesToGet("Metric");

        Item item = foxesRabbitsTable.getItem(getItemSpec);
        System.out.println("[DynamoDB] Foxes Rabbits: item: " + item);

        if (item != null) {
            return item.getLong("Metric");
        }

        return RANDOM_WORKLOAD;
    }

    public static long getInsectWarMetric(int army1, int army2) {
        String hashKey = buildInsectWarHashKey(army1, army2);
        GetItemSpec getItemSpec = new GetItemSpec()
                .withPrimaryKey(INSECT_WAR_HASH_KEY, hashKey)
                .withAttributesToGet("Metric");

        Item item = insectWarTable.getItem(getItemSpec);
        System.out.println("[DynamoDB] Insect War item: " + item);

        if (item != null) {
            return item.getLong("Metric");
        }

        return RANDOM_WORKLOAD;
    }

    public static float getCompressImageMetric(String targetFormat, float compressionFactor) {
        String hashKey = buildImageCompressionHashKey(targetFormat, compressionFactor);

        GetItemSpec getItemSpec = new GetItemSpec()
                .withPrimaryKey(IMAGE_COMPRESSION_HASH_KEY, hashKey)
                .withAttributesToGet("Metric");

        Item item = imageCompressionTable.getItem(getItemSpec);
        System.out.println("[DynamoDB] Image Compression item: " + item);

        if (item != null) {
            return item.getFloat("Metric");
        }

        return (float)RANDOM_WORKLOAD/(RANDOM_WORKLOAD*20); // in compression, the metric value is between 0-1
    }

    public static void tryUpdateFoxesRabbitsMetric(int generations, int world, int scenario, long work) {
        // NOTE: this method is used by lambda functions

        String hashKey = buildFoxesRabbitsHashKey(world, scenario);
        GetItemSpec getItemSpec = new GetItemSpec().withPrimaryKey(FOXES_RABBITS_HASH_KEY, hashKey);

        Item item = foxesRabbitsTable.getItem(getItemSpec);
        if (item != null) {
            long genMin = item.getLong("Gen_Min");
            long genMax = item.getLong("Gen_Max");
            long instMin = item.getLong("Inst_Min");
            long instMax = item.getLong("Inst_Max");

            if (generations < genMin) {
                long newMetric = (instMax - work) / (genMax - generations);
                UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                        .withPrimaryKey(FOXES_RABBITS_HASH_KEY, hashKey)
                        .withUpdateExpression("SET Gen_Min = :val1, Inst_Min = :val2, Metric = :val3")
                        .withValueMap(new ValueMap()
                                .withLong(":val1", generations)
                                .withLong(":val2", work)
                                .withLong(":val3", newMetric));
                foxesRabbitsTable.updateItem(updateItemSpec);
            } else if (generations > genMax) {
                long newMetric = (work - instMin) / (generations - genMin);
                UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                        .withPrimaryKey(FOXES_RABBITS_HASH_KEY, hashKey)
                        .withUpdateExpression("SET Gen_Max = :val1, Inst_Max = :val2, Metric = :val3")
                        .withValueMap(new ValueMap()
                                .withLong(":val1", generations)
                                .withLong(":val2", work)
                                .withLong(":val3", newMetric));
                foxesRabbitsTable.updateItem(updateItemSpec);
            }
        }
    }

    public static void tryUpdateInsectWarMetric(int max, int army1, int army2, long work) {
        // NOTE: this method is used by lambda functions

        String hashKey = buildInsectWarHashKey(army1, army2);
        System.out.flush();
        int roundArmy = max*(army1+army2);

        GetItemSpec getItemSpec = new GetItemSpec().withPrimaryKey(INSECT_WAR_HASH_KEY, hashKey);
        Item item = insectWarTable.getItem(getItemSpec);
        if (item != null) {
            long RoundArmyMin = item.getLong("RoundArmy_Min");
            long RoundArmyMax = item.getLong("RoundArmy_Max");
            long instMin = item.getLong("Inst_Min");
            long instMax = item.getLong("Inst_Max");

            if (roundArmy < RoundArmyMin) {
                long newMetric = (instMax-work)/(RoundArmyMax-roundArmy);
                UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                        .withPrimaryKey(INSECT_WAR_HASH_KEY, hashKey)
                        .withUpdateExpression("SET RoundArmy_Min = :val1, Inst_Min = :val2, Metric = :val3")
                        .withValueMap(new ValueMap()
                                .withLong(":val1", roundArmy)
                                .withLong(":val2", work)
                                .withLong(":val3", newMetric));
                insectWarTable.updateItem(updateItemSpec);
            }
            else if (roundArmy > RoundArmyMax) {
                long newMetric = (work-instMin)/(roundArmy-RoundArmyMin);
                UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                        .withPrimaryKey(INSECT_WAR_HASH_KEY, hashKey)
                        .withUpdateExpression("SET RoundArmy_Max = :val1, Inst_Max = :val2, Metric = :val3")
                        .withValueMap(new ValueMap()
                                .withLong(":val1", roundArmy)
                                .withLong(":val2", work)
                                .withLong(":val3", newMetric));
                insectWarTable.updateItem(updateItemSpec);
            }
        }
    }
}
