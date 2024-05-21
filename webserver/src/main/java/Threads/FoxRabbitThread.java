package Threads;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import pt.ulisboa.tecnico.cnv.javassist.FoxRabbitsParameters;
import pt.ulisboa.tecnico.cnv.javassist.tools.ThreadICount;
import pt.ulisboa.tecnico.cnv.webserver.WebServer;

import java.util.*;

public class FoxRabbitThread {

    private static final String HASH_KEY = "WorldScenario";
    private static final String METRIC = "Metric";
    private static final String GEN_MIN = "Gen_Min";
    private static final String GEN_MAX = "Gen_Max";
    private static final String INSTS_MIN = "Inst_Min";
    private static final String INSTS_MAX = "Inst_Max";

    private static final String UPDATED_VALUES = "Updated";

    private static final long NA = -1L;
    private static final long INFINITY = 9223372036854775807L;
    private static final int WORLDS = 4;
    private static final int SCENARIOS = 3;

    private Table foxRabbitTable;
//    private Iterator<FoxRabbitsParameters> iterator;

    private List<FoxRabbitsParameters> foxRabbitMetricsKeySet;

    AmazonDynamoDB client;
    private ScanResult dynamoSnapshot;

    public FoxRabbitThread(Table foxRabbitTable, AmazonDynamoDB client)
    {
        this.foxRabbitTable = foxRabbitTable;
        this.client = client;
        foxRabbitMetricsKeySet = new ArrayList<>();
    }

    public void run() {
        System.out.println("Launching FoxRabbit thread...");

        FoxRabbitsParameters frp;
        scanDynamo();

        int numGenerations;
        int world;
        int scenario;

        long numInsts;

        Map<String, Long> currentValues;


        foxRabbitMetricsKeySet.addAll(ThreadICount.foxRabbitMetrics.keySet());
        for ( int i = 0 ; i < foxRabbitMetricsKeySet.size() ; i ++)
        {
            frp = foxRabbitMetricsKeySet.get(i);
            numInsts = ThreadICount.foxRabbitMetrics.get(frp).get(0);

            if ( numInsts != -1)
            {

                numGenerations = frp.getGenerations();
                world = frp.getWorld();
                scenario = frp.getScenario();

                numInsts += ThreadICount.foxRabbitMetrics.get(frp).get(1);


                currentValues = getFromDynamoSnapshot(buildHashKey(world, scenario));


                currentValues = updateCurrentMetrics(currentValues, numInsts, numGenerations);
                foxRabbitMetricsKeySet.remove(i);
                ThreadICount.foxRabbitMetrics.remove(frp);
                currentValues = tryToUpdateWithLocalValues(world, scenario, currentValues, i);

                if ( currentValues.get(UPDATED_VALUES) == 1L)
                {
                    long newMetric = calculateNewMetric(currentValues);
                    if (newMetric != -1) {
                        currentValues.put(METRIC, newMetric);
                        putDynamoValues(buildHashKey(world,scenario), currentValues);
                    }
                }
                i--;
            }
        }


        System.out.println("Finishing FoxRabbit thread...");
    }

    private Map<String, Long> tryToUpdateWithLocalValues(int world, int scenario, Map<String, Long> currentMetrics, int i)
    {
        FoxRabbitsParameters frp;

        long numInsts;
        for (  ; i < foxRabbitMetricsKeySet.size() ; i++)
        {
            frp = foxRabbitMetricsKeySet.get(i);
            numInsts = ThreadICount.foxRabbitMetrics.get(frp).get(0);
            if ( numInsts != -1)
            {
                if ( frp.getWorld() == world && frp.getScenario() == scenario )
                {
                    numInsts += ThreadICount.foxRabbitMetrics.get(frp).get(1);

                    currentMetrics = updateCurrentMetrics(currentMetrics, numInsts, frp.getGenerations());
                    foxRabbitMetricsKeySet.remove(i);
                    ThreadICount.foxRabbitMetrics.remove(frp);
                    i--;
                }
            }
        }
        return currentMetrics;
    }


    private Map<String,Long> getFromDynamoSnapshot(String hashKey)
    {
        Map<String, Long> dynamoValues = new HashMap<>();

        for (Map<String, AttributeValue> item: dynamoSnapshot.getItems() )
        {
            if ( item.get(HASH_KEY).getS().equals( hashKey ) )
            {
                AttributeValue instMaxAttr = item.get(INSTS_MAX);
                AttributeValue instMinAttr = item.get(INSTS_MIN);
                AttributeValue roundArmyMin = item.get(GEN_MIN);
                AttributeValue roundArmyMax = item.get(GEN_MAX);

                dynamoValues.put(GEN_MIN, Long.parseLong(roundArmyMin.getN()));
                dynamoValues.put(GEN_MAX, Long.parseLong(roundArmyMax.getN()));
                dynamoValues.put(INSTS_MIN, Long.parseLong(instMinAttr.getN()));
                dynamoValues.put(INSTS_MAX, Long.parseLong(instMaxAttr.getN()));

                dynamoValues.put(UPDATED_VALUES, 0L);
            }
        }
        return dynamoValues;
    }

    private Map<String, Long> updateCurrentMetrics(Map<String, Long> currentMetrics, long numInsts, long gens)
    {
        if ( gens < currentMetrics.get(GEN_MIN) )
        {
            currentMetrics.put(GEN_MIN, gens);
            currentMetrics.put(UPDATED_VALUES, 1L);
        }
        else
        {
            if ( gens > currentMetrics.get(GEN_MAX) )
            {
                currentMetrics.put(GEN_MAX, gens);
                currentMetrics.put(UPDATED_VALUES, 1L);
            }
        }

        if ( numInsts > currentMetrics.get(INSTS_MAX) )
        {
            currentMetrics.put(INSTS_MAX, numInsts);
            currentMetrics.put(UPDATED_VALUES, 1L);
        }
        else
        {
            if ( numInsts < currentMetrics.get(INSTS_MIN) )
            {
                currentMetrics.put(INSTS_MIN, numInsts);
                currentMetrics.put(UPDATED_VALUES, 1L);
            }
        }

        return currentMetrics;
    }


    private void putDynamoValues(String hashKey, Map<String, Long> newValues)
    {

        Item itemToAdd = new Item()
                .withPrimaryKey(HASH_KEY, hashKey)
                .withLong(METRIC, newValues.get(METRIC))
                .withLong(GEN_MIN, newValues.get(GEN_MIN))
                .withLong(GEN_MAX, newValues.get(GEN_MAX))
                .withLong(INSTS_MIN, newValues.get(INSTS_MIN))
                .withLong(INSTS_MAX, newValues.get(INSTS_MAX));

        foxRabbitTable.putItem(itemToAdd);
    }
    private void scanDynamo()
    {
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(WebServer.FOXRABBITTABLE);

        dynamoSnapshot = client.scan(scanRequest);
    }

    private String buildHashKey(int world, int scenario)
    {
        return world+":"+scenario;
    }

    private long calculateNewMetric(Map<String, Long> updatedValues)
    {
        if (Objects.equals(updatedValues.get(GEN_MAX), updatedValues.get(GEN_MIN))) {
            return -1;
        }
        return ( updatedValues.get(INSTS_MAX) - updatedValues.get(INSTS_MIN) ) / ( updatedValues.get(GEN_MAX) - updatedValues.get(GEN_MIN) );
    }

}
