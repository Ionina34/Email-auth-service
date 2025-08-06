package effectivemobile.practice.springkafka.service;

import effectivemobile.practice.model.kafka.ConfirmationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = SpringNotificationService.class)
@ActiveProfiles("spring-kafka")
public class SpringNotificationServiceTest {

    @Autowired
    private SpringNotificationService notificationService;

    @MockBean
    private KafkaTemplate<String, ConfirmationCode> kafkaTemplate;

    @Test
    @DisplayName("Checking the sending of the code to the topic")
    void testProcessRegistrationRequest_sendConfirmationCode() throws JsonProcessingException, ExecutionException, InterruptedException {
        String email = "test@mail.ru";
        notificationService.processRegistrationRequest(email);

        verify(kafkaTemplate).send(eq("confirmation-code"), any(ConfirmationCode.class));
    }

    @Test
    @DisplayName("Checking the code output to the console")
    void testConsoleOutput_printConsole() throws JsonProcessingException, ExecutionException, InterruptedException {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        String email = "test@mail.ru";
        notificationService.processRegistrationRequest(email);

        System.setOut(originalOut);
        assertTrue(outContent.toString().contains("Verification code for " + email));
    }

    @Test
    @DisplayName("Checking the generation of a four-digit code")
    void testGenerateVerificationCode_access() throws Exception {
        Method method = SpringNotificationService.class
                .getDeclaredMethod("generateVerificationCode");
        method.setAccessible(true);

        String code = (String) method.invoke(notificationService);

        assertNotNull(code);
        assertEquals(4, code.length());
        assertTrue(code.matches("\\d{4}"));
    }
}
