package com.test.kafka.streams.enrich_events;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;

public class EnrichEvents {
    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "bank-transactions-app");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // not recommended in production
        config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        StreamsBuilder builder = new StreamsBuilder();

        // stream from user purchases
        KStream<String, String> purchases = builder.stream("user-purchases");
        KStream<String, String> keyedPurchases = purchases
                .selectKey((key, value) -> value.split(":")[0])
                .mapValues((key, value) -> value.split(":")[1]);

        // stream from users
        KStream<String, String> usersTable = builder.stream("user-table");
        // extract key and write it back to an intermediate topic
        usersTable.selectKey((key, value) -> value.split(":")[0])
                .mapValues((key, value) -> value.split(":")[1])
                .to("user-keyed-table");
        // now read as GlobalKTable
        GlobalKTable<String, String> keyedUsersTable = builder.globalTable("user-keyed-table");

        // inner join users and purchases
        KStream<String, String> userPurchasesInnerJoin = keyedPurchases
                .join(
                        keyedUsersTable,
                        (key, value) -> key,
                        (purchase, userInfo) -> "Purchases=" + purchase + ",UserInfo=[" + userInfo + "]"
                );

        // write to a Kafka topic
        userPurchasesInnerJoin.to("user-purchases-enriched-inner-join");

        // left join users and purchases
        KStream<String, String> userPurchasesLeftJoin = keyedPurchases.leftJoin(
                keyedUsersTable,
                (key, value) -> key,
                (purchase, userInfo) -> {
                    // user info can be null because this is a left join
                    if (userInfo == null) {
                        return "Purchases=" + purchase + ",UserInfo=null";
                    }
                    return "Purchases=" + purchase + ",UserInfo=[" + userInfo + "]";
                }
        );

        // write to a Kafka topic
        userPurchasesLeftJoin.to("user-purchases-enriched-left-join");

        Topology topology = builder.build();
        KafkaStreams streams = new KafkaStreams(topology, config);

        streams.cleanUp();
        streams.start();

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

}
