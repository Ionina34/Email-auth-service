package effectivemobile.practice.springkafka.service;

import effectivemobile.practice.AuthServiceApp;
import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.controller.dto.out.MessageResponse;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.repository.UserRepository;
import effectivemobile.practice.security.service.security.AuthenticationService;
import effectivemobile.practice.service.ConfirmationCodeService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AuthServiceApp.class,
        properties = {"spring.jpa.hibernate.ddl-auto=none",
                "spring.datasource.url="})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ActiveProfiles("spring-kafka")
@Testcontainers
@Import(TestKafkaConfig.class)
public class SpringKafkaAuthServiceTest {

    @Container
   public static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.3.3")
    ).withEmbeddedZookeeper();

    @Autowired
    private SpringKafkaAuthService springKafkaAuthService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaTemplate<String, ConfirmationCode> confirmationCodeKafkaTemplate;

    @MockBean
    private ConfirmationCodeService confirmationCodeService;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private UserRepository userRepository;

    @Value("${app.kafka.kafkaRegistrationTopic}")
    private String registrationTopic;

    @Value("${app.kafka.kafkaConfirmationCodeListener}")
    private String confirmationTopic;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("app.kafka.kafkaRegistrationTopic", () -> "test-registration-topic");
        registry.add("app.kafka.kafkaConfirmationCodeListener", () -> "test-confirmation-topic");
        registry.add("app.kafka.kafkaEventGroupId", () -> "test-group-id");
    }

    @Test
    @DisplayName("Регистрация неподтвержденного аккаунта отправляет сообщение в Kafka")
    void testRegisterUnconfirmedAccount_SendsKafkaMessage() {
        SignInUpRequest request = new SignInUpRequest("test@example.com", "password");
        when(authenticationService.registerUnconfirmedAccount(any(SignInUpRequest.class)))
                .thenReturn(null);

        MessageResponse response = springKafkaAuthService.registerUnconfirmedAccount(request);

        assertNotNull(response);
        assertEquals("A verification code has been sent to your email. Send it for verification to the address: '/verify'.", response.message());
        verify(authenticationService, times(1)).registerUnconfirmedAccount(request);

        // Проверяем, что сообщение отправлено в Kafka
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Consumer<String, String> consumer = createConsumer();
                    consumer.subscribe(Collections.singletonList(registrationTopic));
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    assertFalse(records.isEmpty(), "Сообщение должно быть отправлено в топик");
                    records.forEach(record -> {
                        assertEquals("test@example.com", record.value(), "Значение сообщения должно соответствовать email");
                    });
                    consumer.close();
                });
    }

    @Test
    @DisplayName("Регистрация неподтвержденного аккаунта с неподтвержденным статусом отправляет сообщение в Kafka")
    void testRegisterUnconfirmedAccount_AccountNotVerified_SendsKafkaMessage() {
        SignInUpRequest request = new SignInUpRequest("test@example.com", "password");
        when(authenticationService.registerUnconfirmedAccount(any(SignInUpRequest.class)))
                .thenThrow(new AccountNotVerified("Account not verified"));

        AccountNotVerified exception = assertThrows(AccountNotVerified.class, () -> {
            springKafkaAuthService.registerUnconfirmedAccount(request);
        });
        assertEquals("Account not verified", exception.getMessage());
        verify(authenticationService, times(1)).registerUnconfirmedAccount(request);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Consumer<String, String> consumer = createConsumer();
                    consumer.subscribe(Collections.singletonList(registrationTopic));
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    assertFalse(records.isEmpty(), "Сообщение должно быть отправлено в топик");
                    records.forEach(record -> {
                        assertEquals("test@example.com", record.value(), "Значение сообщения должно соответствовать email");
                    });
                    consumer.close();
                });
    }

    @Test
    @DisplayName("Успешный вход в систему отправляет сообщение в Kafka")
    void testSignIn_Success_SendsKafkaMessage() {
        SignInUpRequest request = new SignInUpRequest("test@example.com", "password");
        JwtAuthenticationResponse jwtResponse = new JwtAuthenticationResponse("jwt-token");
        when(authenticationService.signIn(any(SignInUpRequest.class))).thenReturn(jwtResponse);

        JwtAuthenticationResponse response = springKafkaAuthService.signIn(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        verify(authenticationService, times(1)).signIn(request);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Consumer<String, String> consumer = createConsumer();
                    consumer.subscribe(Collections.singletonList(registrationTopic));
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    assertFalse(records.isEmpty(), "Сообщение должно быть отправлено в топик");
                    records.forEach(record -> {
                        assertEquals("test@example.com", record.value(), "Значение сообщения должно соответствовать email");
                    });
                    consumer.close();
                });
    }

    @Test
    @DisplayName("Вход в систему с неподтвержденным аккаунтом отправляет сообщение в Kafka")
    void testSignIn_AccountNotVerified_SendsKafkaMessage() {
        SignInUpRequest request = new SignInUpRequest("test@example.com", "password");
        when(authenticationService.signIn(any(SignInUpRequest.class)))
                .thenThrow(new AccountNotVerified("Account not verified"));

        AccountNotVerified exception = assertThrows(AccountNotVerified.class, () -> {
            springKafkaAuthService.signIn(request);
        });
        assertEquals("Account not verified", exception.getMessage());
        verify(authenticationService, times(1)).signIn(request);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Consumer<String, String> consumer = createConsumer();
                    consumer.subscribe(Collections.singletonList(registrationTopic));
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    assertFalse(records.isEmpty(), "Сообщение должно быть отправлено в топик");
                    records.forEach(record -> {
                        assertEquals("test@example.com", record.value(), "Значение сообщения должно соответствовать email");
                    });
                    consumer.close();
                });
    }

    @Test
    @DisplayName("Подтверждение аккаунта обрабатывает сообщение Kafka и выдает токен")
    void testAccountConfirmation_ProcessesKafkaMessage() {
        String email = "test@example.com";
        String code = UUID.randomUUID().toString();
        ConfirmationCode confirmationCode = new ConfirmationCode(email, code);
        when(confirmationCodeService.verifyConfirmationCode(confirmationCode)).thenReturn(true);
        JwtAuthenticationResponse jwtResponse = new JwtAuthenticationResponse("jwt-token");
        when(authenticationService.tokenIssuance(email)).thenReturn(jwtResponse);

        JwtAuthenticationResponse response = springKafkaAuthService.accountConfirmation(confirmationCode);

        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        verify(confirmationCodeService, times(1)).verifyConfirmationCode(confirmationCode);
        verify(confirmationCodeService, times(1)).deleteConfirmationCode(email);
        verify(authenticationService, times(1)).tokenIssuance(email);
    }

    @Test
    @DisplayName("Получение и обработка кода подтверждения через Kafka")
    void testReceiveCode_ProcessAndSaveCode(){
        ConfirmationCode confirmationCode = new ConfirmationCode("test@maiil.ru","1234");
        String key = UUID.randomUUID().toString();

        doNothing().when(confirmationCodeService).saveConfirmationCode(confirmationCode);

        confirmationCodeKafkaTemplate.send(confirmationTopic, key,confirmationCode).join();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ConfirmationCode.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, ConfirmationCode> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(confirmationTopic));
            ConsumerRecords<String, ConfirmationCode> records = consumer.poll(Duration.ofSeconds(5));
            assertFalse(records.isEmpty(), "Message should be received from the topic");
            records.forEach(record -> {
                assertEquals(confirmationCode.email(), record.value().email(), "Email should match");
                assertEquals(confirmationCode.code(), record.value().code(), "Code should match");
            });
        }

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(()->{
                    verify(confirmationCodeService, times(1)).saveConfirmationCode(any(ConfirmationCode.class));
                });
    }

    private Consumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }
}