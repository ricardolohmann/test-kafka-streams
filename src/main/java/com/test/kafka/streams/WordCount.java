package com.test.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Named;

import java.util.Arrays;
import java.util.Properties;

public class WordCount {
    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "word-count");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        StreamsBuilder builder = new StreamsBuilder();

        // stream from kafka
        KStream<String, String> wordCountInput = builder.stream("word-count-input");

        KTable<String, Long> wordCounts = wordCountInput
                // convert values to lower case
                .mapValues(value -> value.toLowerCase())
                // split by space
                .flatMapValues(lowerCasedTextLine -> Arrays.asList(lowerCasedTextLine.split(" ")))
                // filter words > 1
                .filter((key, word) -> word.length() > 1)
                // select the key
                .selectKey((key, word) -> word)
                // group
                .groupByKey()
                // aggregate
                .count(Named.as("Counts"));

        // stream to a kafka topic
        wordCounts.toStream().to("word-count-output");

        Topology topology = builder.build();
        System.out.println(topology.describe());
        KafkaStreams streams = new KafkaStreams(topology, config);
        streams.start();

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
