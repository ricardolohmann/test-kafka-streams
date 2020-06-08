package com.test.kafka.streams.bank_transactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Instant;
import java.util.Properties;

public class BankTransactions {
    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "bank-transactions-app");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // not recommended in production
        config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        final JsonSerializer jsonSerializer = new JsonSerializer();
        final JsonDeserializer jsonDeserializer = new JsonDeserializer();
        final Serde<JsonNode> jsonSerde = Serdes.serdeFrom(jsonSerializer, jsonDeserializer);

        StreamsBuilder builder = new StreamsBuilder();

        // stream from kafka
        KStream<String, JsonNode> bankTransactions = builder.stream("bank-transactions",
                Consumed.with(Serdes.String(), jsonSerde));

        ObjectNode initialBalance = JsonNodeFactory.instance.objectNode();
        initialBalance.put("count", 0);
        initialBalance.put("balance", 0);
        initialBalance.put("time", Instant.ofEpochMilli(0L).toString());

        KTable<String, JsonNode> bankBalance = bankTransactions
                .groupBy(
                        // select the group key
                        (s, jsonNode) -> jsonNode.get("name").asText(),
                        // we have changed the default Serdes, so we need to set it to the new ones
                        Grouped.with(Serdes.String(), jsonSerde)
                )
                // aggregate
                .aggregate(
                        () -> initialBalance,
                        (aggKey, json, aggValue) -> createNewBalance(json, aggValue),
                        // set the state name and replace it default Serdes
                        Materialized.<String, JsonNode, KeyValueStore<Bytes, byte[]>>as("bank-aggregate-balances")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(jsonSerde)
                );

        // stream to a kafka topic
        bankBalance.toStream().to("bank-balance", Produced.with(Serdes.String(), jsonSerde));

        Topology topology = builder.build();
        System.out.println(topology.describe());
        KafkaStreams streams = new KafkaStreams(topology, config);
        streams.cleanUp();
        streams.start();

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    private static JsonNode createNewBalance(JsonNode transaction, JsonNode balanceValue) {
        // get the last transaction time which can be unordered
        Long lastTransactionTime = Math.max(
                Instant.parse(transaction.get("time").asText()).toEpochMilli(),
                Instant.parse(balanceValue.get("time").asText()).toEpochMilli()
        );

        ObjectNode balance = JsonNodeFactory.instance.objectNode();
        balance.put("count", balanceValue.get("count").asInt() + 1);
        balance.put("balance", balanceValue.get("balance").asDouble() + transaction.get("amount").asDouble());
        balance.put("time", Instant.ofEpochMilli(lastTransactionTime).toString());
        return balance;
    }
}
