package effectivemobile.practice.controller;

import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.controller.dto.out.MessageResponse;
import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.exception.TimeoutConfirmationCodeException;
import effectivemobile.practice.service.KafkaAuthService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final KafkaAuthService kafkaAuthService;

    @Autowired
    public AuthController(KafkaAuthService authService) {
        this.kafkaAuthService = authService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<MessageResponse> signUp(@RequestBody SignInUpRequest request) throws EntityExistsException, AccountNotVerified {
        MessageResponse response = kafkaAuthService.registerUnconfirmedAccount(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<JwtAuthenticationResponse> verify(@RequestBody ConfirmationCode request) throws TimeoutConfirmationCodeException {
        JwtAuthenticationResponse response = kafkaAuthService.accountConfirmation(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/sign-in")
    public ResponseEntity<JwtAuthenticationResponse> signIn(@RequestBody SignInUpRequest request) throws EntityNotFoundException, AccountNotVerified {
        JwtAuthenticationResponse response = kafkaAuthService.signIn(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }
}
