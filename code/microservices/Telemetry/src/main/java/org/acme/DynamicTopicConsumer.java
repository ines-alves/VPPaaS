package org.acme;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.inject.Inject;


public class DynamicTopicConsumer extends Thread  {
    private String kafka_servers;
    private String topic;

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;
    

    public DynamicTopicConsumer(String topic_received , String kafka_servers_received , io.vertx.mutiny.mysqlclient.MySQLPool client_received)  
    {
        topic = topic_received;
        kafka_servers = kafka_servers_received;
        client = client_received;
    }

    public void run() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        try {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", kafka_servers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "your-group-id");

            try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Collections.singletonList(topic));

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        System.out.printf("topic = %s, partition = %s, offset = %d,key = %s, value = %s\\n",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());

                        try {
                            String jsonString = record.value();
                            Telemetry telemetry = mapper.readValue(jsonString, Telemetry.class);
                            telemetry.save(client).await().indefinitely();
                        } catch (Exception ex) {
                            System.err.println("Failed to deserialize or save telemetry: " + ex.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception is caught:" + e);
        }
    }
}
