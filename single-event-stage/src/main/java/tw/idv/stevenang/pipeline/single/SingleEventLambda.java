package tw.idv.stevenang.pipeline.single;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import tw.idv.stevenang.pipeline.common.WeatherEvent;

import java.io.IOException;

public class SingleEventLambda {

    private final ObjectMapper objectMapper = new ObjectMapper().configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
    );

    private final DynamoDB dynamoDB = new DynamoDB(
            AmazonDynamoDBAsyncClientBuilder.defaultClient()
    );
    private final String tableName = System.getenv("LOCATIONS_TABLE");

    public void handleRequest(SNSEvent event) {
        event.getRecords().forEach(this::processSNSRecord);
    }

    private void processSNSRecord(SNSEvent.SNSRecord snsRecord) {
        try {

            final WeatherEvent weatherEvent = this.objectMapper.readValue(snsRecord.getSNS().getMessage(),
                    WeatherEvent.class);

            System.out.println("Received weather event:");

            final Table table = dynamoDB.getTable(tableName);
            final Item item = new Item().withPrimaryKey("locationName", weatherEvent.locationName)
                    .withDouble("temperature", weatherEvent.temperature)
                    .withLong("timestamp", weatherEvent.timestamp)
                    .withDouble("longitute", weatherEvent.longitute)
                    .withDouble("latitute", weatherEvent.latitute);
            table.putItem(item);

        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            throw new RuntimeException(exception);
        }
    }
}

