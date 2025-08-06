package effectivemobile.practice.springkafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ExecutionException;

@Service
@Profile("spring-kafka")
public class SpringNotificationService {

    @Value("${app.kafka.kafkaConfirmationCodeTopic}")
    private String topicName;

    private static final int CODE_LENGTH = 4;

    private final Random random = new Random();

    private final KafkaTemplate<String, ConfirmationCode> kafkaTemplate;

    @Autowired
    public SpringNotificationService(KafkaTemplate<String, ConfirmationCode> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processRegistrationRequest(String email) throws JsonProcessingException, ExecutionException, InterruptedException {
        String code = generateVerificationCode();
        ConfirmationCode confirmationCode = new ConfirmationCode(
                email,
                code
        );
        System.out.println("Verification code for " + email + ": " + code);

        kafkaTemplate.send(topicName, confirmationCode).get();
    }

    private String generateVerificationCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
