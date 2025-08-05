package effectivemobile.practice.apachekafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import effectivemobile.practice.apachekafka.config.ApacheKafkaProducerConfig;
import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.controller.dto.out.MessageResponse;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.exception.TimeoutConfirmationCodeException;
import effectivemobile.practice.security.service.security.AuthenticationService;
import effectivemobile.practice.service.ConfirmationCodeService;
import effectivemobile.practice.service.KafkaAuthService;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityExistsException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@Profile("apache-kafka")
public class ApacheKafkaAuthService implements KafkaAuthService {

    private static final int CODE_LENGTH = 4;

    @Value("${app.kafka.kafkaRegistrationTopic}")
    private String topicName;

    private final AuthenticationService authenticationService;
    private final ConfirmationCodeService confirmationCodeService;
    private KafkaProducer<String, ConfirmationCode> kafkaProducer;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Autowired
    public ApacheKafkaAuthService(AuthenticationService authenticationService,
                                  ConfirmationCodeService confirmationCodeService,
                                  ApacheKafkaProducerConfig producerConfig,
                                  ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.confirmationCodeService = confirmationCodeService;
        this.kafkaProducer = producerConfig.createProducer(objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public MessageResponse registerUnconfirmedAccount(SignInUpRequest request) throws EntityExistsException, AccountNotVerified {
        String message;
        try {
            authenticationService.registerUnconfirmedAccount(request);
            message = "A verification code has been sent to your email. Send it for verification to the address: '/verify'.";
        } catch (AccountNotVerified e) {
            sendConfirmationCode(request.email());
            throw e;
        }
        sendConfirmationCode(request.email());
        return new MessageResponse(message);
    }

    @Override
    public JwtAuthenticationResponse accountConfirmation(ConfirmationCode request) {
        boolean isValid = confirmationCodeService.verifyConfirmationCode(request);
        if (isValid) {
            confirmationCodeService.deleteConfirmationCode(request.email());
            return authenticationService.tokenIssuance(request.email());
        } else {
            throw new TimeoutConfirmationCodeException("The code has expired");
        }
    }

    @Override
    public JwtAuthenticationResponse signIn(SignInUpRequest request) throws AccountNotVerified {
        JwtAuthenticationResponse response;
        try {
            response = authenticationService.signIn(request);
        } catch (AccountNotVerified e) {
            sendConfirmationCode(request.email());
            throw e;
        }
        sendConfirmationCode(request.email());
        return response;
    }

    private void sendConfirmationCode(String email) {
        String code = generateVerificationCode();
        ConfirmationCode confirmationCode = new ConfirmationCode(email, code);
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
