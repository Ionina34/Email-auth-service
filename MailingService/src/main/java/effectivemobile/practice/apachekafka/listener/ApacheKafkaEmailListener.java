package effectivemobile.practice.apachekafka.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import effectivemobile.practice.apachekafka.service.ApacheNotificationService;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

@Slf4j
@Component
@Profile("apache-kafka")
public class ApacheKafkaEmailListener {

    private String bootstrapServers;
    private String topicName;
    private String groupId;

    private final ApacheNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final KafkaConsumer<String, String> consumer;
    private volatile boolean running = true;

    @Autowired
    public ApacheKafkaEmailListener(ApacheNotificationService notificationService,
                                    ObjectMapper objectMapper,
                                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                                    @Value("${app.kafka.kafkaConfirmationCodeListener}") String topicName,
                                    @Value("${app.kafka.kafkaEventGroupId}") String groupId) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.bootstrapServers = bootstrapServers;
        this.topicName = topicName;
        this.groupId = groupId;
        this.consumer = createConsumer();
        startConsumer();
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topicName));
        return consumer;
    }

    private void startConsumer() {
        new Thread(() -> {
            try {
                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            // Десериализация строки JSON в ConfirmationCode
                            ConfirmationCode confirmationCode = objectMapper.readValue(record.value(), ConfirmationCode.class);
                            String email = confirmationCode.email();
                            notificationService.processRegistrationRequest(email);
                        } catch (Exception e) {
                            log.error("Error processing message: {}, error: {}", record.value(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Consumer error: {}", e.getMessage());
            } finally {
                consumer.close();
            }
        }).start();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        consumer.wakeup();
    }
}
