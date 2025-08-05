package effectivemobile.practice.service;

import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.controller.dto.out.MessageResponse;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.security.exception.AccountNotVerified;
import jakarta.persistence.EntityExistsException;

public interface KafkaAuthService {

    MessageResponse registerUnconfirmedAccount(SignInUpRequest request) throws EntityExistsException, AccountNotVerified;

    JwtAuthenticationResponse accountConfirmation(ConfirmationCode request);

    JwtAuthenticationResponse signIn(SignInUpRequest request) throws AccountNotVerified;
}
