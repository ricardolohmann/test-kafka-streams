#!/bin/bash

# create input topic with two partitions
docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --zookeeper zookeeper:2181 --replication-factor 1
 --partitions 2 --topic word-count-input

# create output topic
docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --zookeeper zookeeper:2181 --replication-factor 1
 --partitions 2 --topic word-count-output --config cleanup.policy=compact
 

# launch a Kafka consumer
docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
    --topic favorite-color-output \
    --from-beginning \
    --formatter kafka.tools.DefaultMessageFormatter \
    --property print.key=true \
    --property print.value=true \
    --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer \
    --property value.deserializer=org.apache.kafka.common.serialization.StringDeserializer

# then produce data to it
docker-compose exec kafka /opt/kafka/bin/kafka-console-producer.sh --broker-list kafka:9092 --topic favorite-color-output

# package your application as a fat jar
mvn clean package

# run your fat jar
java -jar <your jar here>.jar

# list all topics that we have in Kafka (so we can observe the internal topics)
docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --zookeeper zookeeper:2181
