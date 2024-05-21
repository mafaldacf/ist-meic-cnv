package Threads;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import pt.ulisboa.tecnico.cnv.javassist.AntWarParameters;
import pt.ulisboa.tecnico.cnv.javassist.tools.ThreadICount;
import pt.ulisboa.tecnico.cnv.webserver.WebServer;

import java.util.*;

public class AntWarThread {

    private static final String HASHKEY = "Ratio";
    private static final String METRIC = "Metric";
    private static final String ROUNDARMY_MIN = "RoundArmy_Min";
    private static final String ROUNDARMY_MAX = "RoundArmy_Max";
    private static final String INSTS_MIN = "Inst_Min";
    private static final String INSTS_MAX = "Inst_Max";
    private static final String UPDATED_VALUES = "Updated";

    private Table antWarTable;
    private List<AntWarParameters> antWarMetricsKeySet;
    AmazonDynamoDB client;
    private ScanResult dynamoSnapshot;

    public AntWarThread(Table antWarTable, AmazonDynamoDB client)
    {
        this.antWarTable = antWarTable;

        System.out.println("Table " + antWarTable.getTableName());
        this.client = client;
        antWarMetricsKeySet = new ArrayList<>();
    }

    public void run() {
        System.out.println("Launching AntWar thread...");

        AntWarParameters awp;
        scanDynamo();

        long numRounds;
        long army1;
        long army2;

        long numInsts;
        long ratio;



        Map<String, Long> currentValues;

        antWarMetricsKeySet.addAll(ThreadICount.antWarMetrics.keySet());
        for ( int i = 0 ; i < antWarMetricsKeySet.size() ; i ++)
        {
            awp = antWarMetricsKeySet.get(i);
            numInsts = ThreadICount.antWarMetrics.get(awp).get(0);
            if ( numInsts != -1)
            {
                numRounds = awp.getMax();
                army1 = awp.getArmy1();
                army2 = awp.getArmy2();

                numInsts += ThreadICount.antWarMetrics.get(awp).get(1);
                ratio = computeRatio(army1, army2);

                currentValues = getFromDynamoSnapshot(buildHashKey(ratio));
                currentValues = updateCurrentMetrics(currentValues, numInsts, computeRoundArmy(numRounds, army1, army2));
                antWarMetricsKeySet.remove(i);
                ThreadICount.antWarMetrics.remove(awp);

                currentValues = tryToUpdateWithLocalValues(ratio, currentValues, i);

                if ( currentValues.get(UPDATED_VALUES) == 1L)
                {
                    Map<String, Long> newMetric = calculateNewMetric(currentValues);
                    if (newMetric != null) {
                        putDynamoValues(buildHashKey(ratio), newMetric);
                    }
                }
                i--;
            }
        }

        System.out.println("Finishing AntWar thread...");
    }

    private Map<String, Long> tryToUpdateWithLocalValues(long ratio, Map<String, Long> currentMetrics, int i)
    {
        AntWarParameters awp;

        long numInsts;

        for (; i < antWarMetricsKeySet.size() ; i ++)
        {
            awp = antWarMetricsKeySet.get(i);
            numInsts = ThreadICount.antWarMetrics.get(awp).get(0);
            if ( numInsts != -1)
            {
                if ( ratio == computeRatio(awp.getArmy1(), awp.getArmy2()) )
                {
                    numInsts += ThreadICount.antWarMetrics.get(awp).get(1);

                    currentMetrics = updateCurrentMetrics(currentMetrics, numInsts, computeRoundArmy(awp.getMax(), awp.getArmy1(), awp.getArmy2()));
                    antWarMetricsKeySet.remove(1);
                    ThreadICount.antWarMetrics.remove(awp);
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
            if ( item.get(HASHKEY).getS().equals( hashKey ) )
            {
                AttributeValue instMaxAttr = item.get(INSTS_MAX);
                AttributeValue instMinAttr = item.get(INSTS_MIN);
                AttributeValue roundArmyMin = item.get(ROUNDARMY_MIN);
                AttributeValue roundArmyMax = item.get(ROUNDARMY_MAX);

                dynamoValues.put(ROUNDARMY_MIN, Long.parseLong(roundArmyMin.getN()));
                dynamoValues.put(ROUNDARMY_MAX, Long.parseLong(roundArmyMax.getN()));
                dynamoValues.put(INSTS_MIN, Long.parseLong(instMinAttr.getN()));
                dynamoValues.put(INSTS_MAX, Long.parseLong(instMaxAttr.getN()));

                dynamoValues.put(UPDATED_VALUES, 0L);
            }
        }
        return dynamoValues;
    }



    private Map<String, Long> updateCurrentMetrics(Map<String, Long> currentMetrics, long numInsts, long roundArmy)
    {

        if ( roundArmy < currentMetrics.get(ROUNDARMY_MIN) )
        {
            currentMetrics.put(ROUNDARMY_MIN, roundArmy);
            currentMetrics.put(UPDATED_VALUES, 1L);
        }
        else
        {
            if ( roundArmy > currentMetrics.get(ROUNDARMY_MAX) )
            {
                currentMetrics.put(ROUNDARMY_MAX, roundArmy);
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
                .withPrimaryKey(HASHKEY, hashKey)
                .withLong(METRIC, newValues.get(METRIC))
                .withLong(ROUNDARMY_MIN, newValues.get(ROUNDARMY_MIN))
                .withLong(ROUNDARMY_MAX, newValues.get(ROUNDARMY_MAX))
                .withLong(INSTS_MIN, newValues.get(INSTS_MIN))
                .withLong(INSTS_MAX, newValues.get(INSTS_MAX));

        antWarTable.putItem(itemToAdd);
    }

    private void scanDynamo()
    {
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(WebServer.ANTWARTABLE);

        dynamoSnapshot = client.scan(scanRequest);
    }

    private Map<String,Long> calculateNewMetric(Map<String,Long> currentValues)
    {

        if (Objects.equals(currentValues.get(ROUNDARMY_MAX), currentValues.get(ROUNDARMY_MIN))) {
            return null;
        }

        currentValues.put( METRIC, (currentValues.get(INSTS_MAX) - currentValues.get(INSTS_MIN)) / ( currentValues.get(ROUNDARMY_MAX) - currentValues.get(ROUNDARMY_MIN)) );
        return  currentValues;
    }


    private long computeRoundArmy (long numRounds, long army1, long army2)
    {
        return numRounds * (army1+army2);
    }

    private long computeRatio(long army1, long army2)
    {
        long ratio = army1 > army2 ? (long)(army1/army2 + 0.5) : (long)(army2/army1 + 0.5);
        if ( ratio % 2 == 0) { return ratio; }
        return ratio + 1;
    }

    private String buildHashKey(long ratio)
    {
        if ( ratio > 8 ) return "<=infinity";
        return "<=" + ratio;
    }
}

