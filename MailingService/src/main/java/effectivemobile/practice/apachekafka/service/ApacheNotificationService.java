package effectivemobile.practice.apachekafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import effectivemobile.practice.apachekafka.config.KafkaProducerConfig;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@Profile("apache-kafka")
public class ApacheNotificationService {

    @Value("${app.kafka.kafkaConfirmationCodeTopic}")
    private String topicName;

    private static final int CODE_LENGTH = 4;

    private final Random random = new Random();
    private final KafkaProducer<String, ConfirmationCode> kafkaProducer;

    @Autowired
    public ApacheNotificationService(KafkaProducerConfig producerConfig, ObjectMapper objectMapper) {
        this.kafkaProducer = producerConfig.createProducer(objectMapper);
    }

    public void processRegistrationRequest(String email) {
        String code = generateVerificationCode();
        ConfirmationCode confirmationCode = new ConfirmationCode(email, code);
        System.out.println("Verification code for " + email + ": " + code);

        ProducerRecord<String, ConfirmationCode> record = new ProducerRecord<>(topicName, email, confirmationCode);
        try {
            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Error sending message to Kafka: " + exception.getMessage());
                } else {
                    System.out.println("Message sent to topic " + metadata.topic() + " partition " + metadata.partition() +
                            " offset " + metadata.offset());
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    private String generateVerificationCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    @PreDestroy
    public void shutdown() {
        kafkaProducer.close();
    }
}
