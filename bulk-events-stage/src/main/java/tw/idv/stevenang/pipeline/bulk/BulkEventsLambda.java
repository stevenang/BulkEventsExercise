package tw.idv.stevenang.pipeline.bulk;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import tw.idv.stevenang.pipeline.common.WeatherEvent;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BulkEventsLambda {

    // This will instanciate an object mapper which can map the given object to the specific class object
    // FAIL_ON_UNKNOWN_PROPERTIES - Feature that determine whether encountering of unknown object or not
    // false - Turn off the state of DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
    private final ObjectMapper objectMapper = new ObjectMapper().configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
    );
    // This will instanciate an Amazon SNS client
    private final AmazonSNS sns = AmazonSNSClientBuilder.defaultClient();
    // This will instanciate an Amazon S3 Client
    private final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    // This will read a System Environmental Variable - FAN_OUT_TOPIC
    private final String snsTopic = System.getenv("FAN_OUT_TOPIC");

    public void handleRequest(S3Event event) {
        event.getRecords().forEach(this::processS3EventRecord);
    }

    private void processS3EventRecord(S3EventNotification.S3EventNotificationRecord record) {

        System.out.println(record.getS3().getBucket().getName());
        System.out.println(record.getS3().getObject().getKey());

        final List<WeatherEvent> weatherEvents = readWeatherEventsFromS3(
                record.getS3().getBucket().getName(),
                record.getS3().getObject().getKey());

        weatherEvents.stream().map(this::weatherToSnsMessage)
                .forEach(message -> sns.publish(snsTopic, message));

        System.out.println("Published " + weatherEvents.size() + " weather events to SNS");
    }

    private List<WeatherEvent> readWeatherEventsFromS3(String bucket, String key) {

        try {
            final S3ObjectInputStream inputStream = s3.getObject(bucket, key).getObjectContent();
            final WeatherEvent [] weatherEvents = objectMapper.readValue(inputStream, WeatherEvent[].class);
            inputStream.close();

            s3.deleteObject(bucket, key);

            return Arrays.asList(weatherEvents);

        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            throw new RuntimeException(exception);
        }
    }

    private String weatherToSnsMessage(WeatherEvent weatherEvent) {

        try {
            return objectMapper.writeValueAsString(weatherEvent);
        } catch (JsonProcessingException exception) {
            exception.printStackTrace();
            throw new RuntimeException(exception);
        }
    }
}
