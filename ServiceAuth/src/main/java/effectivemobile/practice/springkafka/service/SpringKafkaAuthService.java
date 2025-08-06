package effectivemobile.practice.springkafka.service;

import effectivemobile.practice.controller.dto.out.MessageResponse;
import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.exception.TimeoutConfirmationCodeException;
import effectivemobile.practice.security.service.security.AuthenticationService;
import effectivemobile.practice.service.ConfirmationCodeService;
import effectivemobile.practice.service.KafkaAuthService;
import jakarta.persistence.EntityExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("spring-kafka")
public class SpringKafkaAuthService implements KafkaAuthService {

    private final AuthenticationService authenticationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ConfirmationCodeService confirmationCodeService;

    @Value("${app.kafka.kafkaRegistrationTopic}")
    private String topicName;

    @Autowired
    public SpringKafkaAuthService(AuthenticationService authenticationService, KafkaTemplate<String, String> kafkaTemplate, ConfirmationCodeService confirmationCodeService) {
        this.authenticationService = authenticationService;
        this.kafkaTemplate = kafkaTemplate;
        this.confirmationCodeService = confirmationCodeService;
    }

    @Override
    public MessageResponse registerUnconfirmedAccount(SignInUpRequest request) throws EntityExistsException, AccountNotVerified {
        String message;
        try {
            authenticationService.registerUnconfirmedAccount(request);
            message = "A verification code has been sent to your email. Send it for verification to the address: '/verify'.";
            kafkaTemplate.send(topicName, request.email());
        } catch (AccountNotVerified e) {
            kafkaTemplate.send(topicName, request.email());
            throw e;
        }

        return new MessageResponse(message);
    }

    @Override
    public JwtAuthenticationResponse accountConfirmation(ConfirmationCode request) throws TimeoutConfirmationCodeException{
        boolean isValid = confirmationCodeService.verifyConfirmationCode(request);
        if (isValid) {
            confirmationCodeService.deleteConfirmationCode(request.email());
            return authenticationService.tokenIssuance(request.email());
        } else {
            throw new TimeoutConfirmationCodeException("The code has expired or the code is incorrect");
        }
    }

    @Override
    public JwtAuthenticationResponse signIn(SignInUpRequest request) throws AccountNotVerified {
        JwtAuthenticationResponse response;
        try {
            response = authenticationService.signIn(request);
        } catch (AccountNotVerified e) {
            kafkaTemplate.send(topicName, request.email());
            throw e;
        }
        kafkaTemplate.send(topicName, request.email());
        return response;
    }
}
