package Threads;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import pt.ulisboa.tecnico.cnv.javassist.CompressionParameters;
import pt.ulisboa.tecnico.cnv.javassist.FoxRabbitsParameters;
import pt.ulisboa.tecnico.cnv.javassist.tools.ThreadICount;
import pt.ulisboa.tecnico.cnv.webserver.WebServer;

import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.*;

public class CompressionThread {


    private static final String[] POSSIBLE_FORMATS = {"BI_RGB", "Deflate", "JPEG"};

    private static final float[] POSSIBLE_QUALITIES = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};

    private static final String HASHKEY = "DestinationFactor";
    private static final String METRIC = "Metric";
    private static final String SIZE_MIN = "Size_Min";
    private static final String SIZE_MAX = "Size_Max";
    private static final String INSTS_MIN = "Inst_Min";
    private static final String INSTS_MAX = "Inst_Max";

    private static final String UPDATED_VALUES = "Updated";

    private Table compressionTable;

    AmazonDynamoDB client;
    private ScanResult dynamoSnapshot;
    private List<CompressionParameters> compressionMetricsKeySet;

    public CompressionThread(Table compressionTable, AmazonDynamoDB client) {
        this.compressionTable = compressionTable;
        this.client = client;
        compressionMetricsKeySet = new ArrayList<>();
    }

    public void run() {
        System.out.println("Launching Compression thread...");
        CompressionParameters cp;
        scanDynamo();

        String format;
        float quality;
        long numInsts;

        Map<String, Long> currentValues;
        compressionMetricsKeySet.addAll(ThreadICount.compressionMetrics.keySet());


        for ( int i = 0 ; i < compressionMetricsKeySet.size() ; i ++)
        {
            cp = compressionMetricsKeySet.get(i);
            numInsts = ThreadICount.compressionMetrics.get(cp).get(0);

            if (numInsts != -1) {
                numInsts += ThreadICount.compressionMetrics.get(cp).get(1);
                format = cp.getCompressionType();
                quality = cp.getCompressionQuality();

                currentValues = getFromDynamoSnapshot(buildHashKey(format, quality));

                currentValues = updateCurrentMetrics(currentValues, numInsts, cp.getBuferedImage());
                compressionMetricsKeySet.remove(i);
                ThreadICount.compressionMetrics.remove(cp);

                currentValues = tryToUpdateWithLocalValues(format, quality, currentValues, i);


                if (currentValues.get(UPDATED_VALUES) == 1L) {
                    float newMetric = calculateNewMetric(currentValues);
                    if (newMetric != -1) {
                        putDynamoValues(buildHashKey(format, quality), currentValues ,newMetric);
                    }
                }
                i--;
            }
        }
        System.out.println("Finishing Compression thread...");
    }


    private Map<String, Long> tryToUpdateWithLocalValues(String format, float quality, Map<String, Long> currentMetrics, int i) {
        CompressionParameters cp;

        for (  ; i < compressionMetricsKeySet.size() ; i++)
        {
            cp = compressionMetricsKeySet.get(i);
            long numInsts = ThreadICount.compressionMetrics.get(cp).get(0);
            if (numInsts != -1) {
                if (cp.getCompressionType().equals(format) && cp.getCompressionQuality() == quality) {
                    numInsts += ThreadICount.compressionMetrics.get(cp).get(1);

                    currentMetrics = updateCurrentMetrics(currentMetrics, numInsts, cp.getBuferedImage());
                    compressionMetricsKeySet.remove(i);
                    ThreadICount.compressionMetrics.remove(cp);
                    i--;
                }
            }
        }
        return currentMetrics;
    }


    private void putDynamoValues(String hashKey, Map<String, Long> newValues, float metric) {
        Item itemToAdd = new Item()
                .withPrimaryKey(HASHKEY, hashKey)
                .withFloat(METRIC, metric)
                .withLong(SIZE_MIN, newValues.get(SIZE_MIN))
                .withLong(SIZE_MAX, newValues.get(SIZE_MAX))
                .withLong(INSTS_MIN, newValues.get(INSTS_MIN))
                .withLong(INSTS_MAX, newValues.get(INSTS_MAX));

        compressionTable.putItem(itemToAdd);
    }


    private Map<String, Long> getFromDynamoSnapshot(String hashKey) {

        Map<String, Long> dynamoValues = new HashMap<>();

        for (Map<String, AttributeValue> item : dynamoSnapshot.getItems()) {
            if (item.get(HASHKEY).getS().equals(hashKey)) {
                AttributeValue instMaxAttr = item.get(INSTS_MAX);
                AttributeValue instMinAttr = item.get(INSTS_MIN);
                AttributeValue sizeMaxAttr = item.get(SIZE_MAX);
                AttributeValue sizeMinAttr = item.get(SIZE_MIN);

                dynamoValues.put(SIZE_MIN, Long.parseLong(sizeMinAttr.getN()));
                dynamoValues.put(SIZE_MAX, Long.parseLong(sizeMaxAttr.getN()));
                dynamoValues.put(INSTS_MIN, Long.parseLong(instMinAttr.getN()));
                dynamoValues.put(INSTS_MAX, Long.parseLong(instMaxAttr.getN()));


                dynamoValues.put(UPDATED_VALUES, 0L);
            }
        }
        return dynamoValues;
    }


    private Map<String, Long> updateCurrentMetrics(Map<String, Long> currentMetrics, long numInsts, BufferedImage bufferedImage) {
        int size = bufferedImage.getHeight() * bufferedImage.getWidth();


        if (size > currentMetrics.get(SIZE_MAX)) {
            currentMetrics.put(SIZE_MAX, (long) size);
            currentMetrics.put(UPDATED_VALUES, 1L);
        } else {
            if (size < currentMetrics.get(SIZE_MIN)) {
                currentMetrics.put(SIZE_MIN, (long) size);
                currentMetrics.put(UPDATED_VALUES, 1L);
            }
        }

        if (numInsts > currentMetrics.get(INSTS_MAX)) {
            currentMetrics.put(INSTS_MAX, numInsts);
            currentMetrics.put(UPDATED_VALUES, 1L);
        } else {
            if (numInsts < currentMetrics.get(INSTS_MIN)) {
                currentMetrics.put(INSTS_MIN, numInsts);
                currentMetrics.put(UPDATED_VALUES, 1L);
            }
        }

        return currentMetrics;
    }

    private float calculateNewMetric(Map<String, Long> currentValues) {
        if (Objects.equals(currentValues.get(SIZE_MAX), currentValues.get(SIZE_MIN))) {
            return -1;
        }
        float metric = ((float)(currentValues.get(INSTS_MAX) - currentValues.get(INSTS_MIN))) / ((float)(currentValues.get(SIZE_MAX) - currentValues.get(SIZE_MIN)));
        return metric;

    }


    private void scanDynamo() {
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(WebServer.COMPRESSIONTABLE);

        dynamoSnapshot = client.scan(scanRequest);
    }

    private String buildHashKey(String format, float compressionQuality) {
        if ( format.equals(POSSIBLE_FORMATS[0]) ) { return format; }
        return format + ":" + roundToNearestTenth(compressionQuality);
    }

    public String roundToNearestTenth(float number) {
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        return decimalFormat.format(number);
    }

}