package com.test.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Arrays;
import java.util.Properties;

public class FavoriteColor {
    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "favorite-color-count");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // not recommended in production
        config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        StreamsBuilder builder = new StreamsBuilder();

        // stream from kafka
        KStream<String, String> colorCountInput = builder.stream("favorite-color-input");

        KStream<String, String> filteredColors = colorCountInput
                // filter invalid values
                .filter((key, value) -> value.contains(","))
                // convert values to lower case
                .mapValues(value -> value.toLowerCase())
                // select the key
                .selectKey((key, value) -> value.split(",")[0])
                // extract only the value
                .mapValues((key, value) -> value.split(",")[1])
                // filter colors
                .filter((key, value) -> Arrays.asList("red", "blue", "green").contains(value));

        // writes to and intermediate keyed topic
        filteredColors.to("favorite-color-keyed");

        // read from intermediate topic
        KTable<String, String> keyedColors = builder.table("favorite-color-keyed");

        KTable<String, Long> colorsCount = keyedColors
                // group by color
                .groupBy((color, value) -> KeyValue.pair(color, color))
                // aggregate
                .count(Named.as("Colors"));

        // stream to a kafka topic
        colorsCount.toStream().to("favorite-color-output", Produced.with(Serdes.String(), Serdes.Long()));

        Topology topology = builder.build();
        KafkaStreams streams = new KafkaStreams(topology, config);

        // use cleanUp only in dev
        streams.cleanUp();
        streams.start();

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
