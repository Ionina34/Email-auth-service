package effectivemobile.practice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.controller.dto.out.MessageResponse;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.exception.TimeoutConfirmationCodeException;
import effectivemobile.practice.service.KafkaAuthService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {AuthController.class, GlobalExceptionHandler.class})
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaAuthService kafkaAuthService;

    private SignInUpRequest signInUpRequest;
    private ConfirmationCode confirmationCode;

    @BeforeEach
    void setUp() {
        signInUpRequest = new SignInUpRequest("test@mail.ru", "12345");
        confirmationCode = new ConfirmationCode("test@mail.ru", "0000");
    }

    @Test
    @DisplayName("Регистрация выбрасывает EntityExistsException и возвращает 409 Conflict")
    void signUp_ThrowsEntityExistsException_ReturnsConflict() throws Exception {
        when(kafkaAuthService.registerUnconfirmedAccount(any(SignInUpRequest.class)))
                .thenThrow(new EntityExistsException("Email already exists"));

        mockMvc.perform(post("/api/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInUpRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.advice").value("Try another email."))
                .andExpect(jsonPath("$.error").value("Email already exists"));
    }

    @Test
    @DisplayName("Регистрация выбрасывает AccountNotVerified и возвращает 401 Unauthorized")
    void signUp_ThrowsAccountNotVerified_ReturnsUnauthorized() throws Exception {
        when(kafkaAuthService.registerUnconfirmedAccount(any(SignInUpRequest.class))).thenThrow(new AccountNotVerified("Account not verified"));

        mockMvc.perform(post("/api/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInUpRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.advice").value("Check your email and send the verification code."))
                .andExpect(jsonPath("$.error").value("Account not verified"));
    }

    @Test
    @DisplayName("Успешная регистрация возвращает 202 Accepted")
    void signUp_Access() throws Exception {
        when(kafkaAuthService.registerUnconfirmedAccount(any(SignInUpRequest.class))).thenReturn(new MessageResponse("Access"));

        mockMvc.perform(post("/api/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInUpRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Access"));
    }

    @Test
    @DisplayName("Проверка кода выбрасывает TimeoutConfirmationCodeException и возвращает 409 Conflict")
    void verify_ThrowsTimeoutConfirmationCodeException_ReturnsRequestTimeout() throws Exception {
        when(kafkaAuthService.accountConfirmation(any(ConfirmationCode.class))).thenThrow(new TimeoutConfirmationCodeException("Code expired"));

        mockMvc.perform(post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.advice").value("The code is incorrect or incorrect. Send a repeat registration request or check your email."))
                .andExpect(jsonPath("$.error").value("Code expired"));
    }

    @Test
    @DisplayName("Успешная проверка кода возвращает 200 OK")
    void verify_Access() throws Exception {
        when(kafkaAuthService.accountConfirmation(any(ConfirmationCode.class))).thenReturn(new JwtAuthenticationResponse("token-test"));

        mockMvc.perform(post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-test"));
    }

    @Test
    @DisplayName("Вход выбрасывает EntityNotFoundException и возвращает 404 Not Found")
    void signIn_ThrowsEntityNotFoundException_ReturnsNotFound() throws Exception {
        when(kafkaAuthService.signIn(any(SignInUpRequest.class))).thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(post("/api/v1/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signInUpRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.advice").value("There may be an error in writing the mail."))
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    @DisplayName("Вход выбрасывает AccountNotVerified и возвращает 401 Unauthorized")
    void signIn_ThrowsAccountNotVerified_ReturnsUnauthorized()throws Exception{
        when(kafkaAuthService.signIn(any(SignInUpRequest.class))).thenThrow(new AccountNotVerified("Account not verified"));

        mockMvc.perform(post("/api/v1/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signInUpRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.advice").value("Check your email and send the verification code."))
                .andExpect(jsonPath("$.error").value("Account not verified"));
    }

    @Test
    @DisplayName("Успешный вход возвращает 202 Accepted")
    void signIn_Access()throws Exception{
        when(kafkaAuthService.signIn(any(SignInUpRequest.class))).thenReturn(new JwtAuthenticationResponse("token-test"));

        mockMvc.perform(post("/api/v1/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signInUpRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.token").value("token-test"));
    }
}
