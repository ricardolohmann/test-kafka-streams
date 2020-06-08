
setup_word_count:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1 \
		--partitions 2 \
		--topic word-count-input
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1 \
		--partitions 2 \
		--topic word-count-output \
		--config cleanup.policy=compact

setup_favorite_color:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1\
		--partitions 2 \
		--topic favorite-color-input
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1 \
		--partitions 2 \
		--topic favorite-color-output
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1 \
		--partitions 2 \
		--topic favorite-color-keyed

setup_bank_balance:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1 \
		--partitions 2 \
		--topic bank-transactions
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
		--zookeeper zookeeper:2181 \
		--replication-factor 1 \
		--partitions 2 \
		--topic bank-balance \
		--config cleanup.policy=compact

run_kafka_consumer:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
		--bootstrap-server kafka:9092 \
        --topic $(topic) \
        --from-beginning \
        --formatter kafka.tools.DefaultMessageFormatter \
        --property print.key=true \
        --property print.value=true \
        --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer \
        --property value.deserializer=org.apache.kafka.common.serialization.StringDeserializer

run_console_producer:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-console-producer.sh \
		--broker-list kafka:9092 \
		--topic $(topic)

run_producer_perf_test:
	@docker-compose exec kafka /opt/kafka/bin/kafka-producer-perf-test.sh \
		--topic $(topic) \
		--num-records 10000 \
		--throughput -1 \
		--payload-delimiter "\n" \
		--payload-file /opt/kafka/bank_transactions \
		--producer-props \
			acks=1 \
			bootstrap.servers=kafka:9092 \
			key.serializer=org.apache.kafka.common.serialization.StringSerializer \
			value.serializer=org.apache.kafka.common.serialization.StringSerializer

list_topics:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --list \
		--zookeeper zookeeper:2181

list_consumer_groups:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
    	--bootstrap-server kafka:9092 --list

get_group_lag:
	@ docker-compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
		--bootstrap-server kafka:9092 \
		--describe --group $(group)
