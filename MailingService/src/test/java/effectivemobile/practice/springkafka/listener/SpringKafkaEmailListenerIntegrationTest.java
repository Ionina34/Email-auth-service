package effectivemobile.practice.springkafka.listener;

import effectivemobile.practice.model.kafka.ConfirmationCode;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("spring-kafka")
@Testcontainers
public class SpringKafkaEmailListenerIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.3.3"))
                    .withStartupTimeout(Duration.ofSeconds(60));

    @DynamicPropertySource
    static void registryKafkaProperties(DynamicPropertyRegistry registry){
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(factory);
    }

    @Autowired
    private KafkaTemplate<String, ConfirmationCode> confirmationCodeKafkaTemplate;

    @Value("${app.kafka.kafkaConfirmationCodeTopic}")
    private String outputTopic;

    @Value("${app.kafka.kafkaRegistrationRequestListener}")
    private String inputTopic;

    @Test
    void whenReceiveEmail_thanProcessAndSendConfirmationCode() throws InterruptedException {
        String email = "test@mail.ru";
        String key = UUID.randomUUID().toString();

        //Send test message in topic
        stringKafkaTemplate().send(inputTopic, key, email);

        //waiting precessing anf checking result
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Consumer<String, ConfirmationCode> consumer = createConsumer();
                    consumer.subscribe(Collections.singletonList(outputTopic));

                    ConsumerRecords<String, ConfirmationCode> records = consumer.poll(Duration.ofSeconds(5));

                    assertFalse(records.isEmpty());

                    records.forEach(record->{
                        ConfirmationCode confirmationCode = record.value();
                        assertEquals(email, confirmationCode.email());
                        assertTrue(confirmationCode.code().matches("\\d{4}"));
                    });

                    consumer.close();
                });
    }

    private Consumer<String, ConfirmationCode> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ConfirmationCode.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }
}
