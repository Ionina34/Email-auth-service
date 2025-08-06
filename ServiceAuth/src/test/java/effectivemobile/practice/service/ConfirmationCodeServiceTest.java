package effectivemobile.practice.service;

import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.service.ConfirmationCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class ConfirmationCodeServiceTest {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(60));

    @Autowired
    private ConfirmationCodeService confirmationCodeService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Configuration
    static class TestConfig {

        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(redisContainer.getHost());
            config.setPort(redisContainer.getFirstMappedPort());
            return new LettuceConnectionFactory(config);
        }

        @Bean
        public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
            RedisTemplate<String, String> template = new RedisTemplate<>();
            template.setConnectionFactory(redisConnectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            return template;
        }

        @Bean
        public ConfirmationCodeService confirmationCodeService(RedisTemplate<String, String> redisTemplate) {
            return new ConfirmationCodeService(redisTemplate);
        }
    }

    @BeforeEach
    void setUp(){
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("Сохранение кода подтверждения с установкой TTL")
    void testSaveConfirmationCode_SavesCodeWithTTL(){
        ConfirmationCode confirmationCode = new ConfirmationCode("test@mail.ru", "1234");
        String expectedKey = "confirmation:email:test@mail.ru";

        confirmationCodeService.saveConfirmationCode(confirmationCode);

        String storedCode = redisTemplate.opsForValue().get(expectedKey);
        assertEquals("1234", storedCode, "Stored code should match");
        Long ttl = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);
        assertNotNull(ttl, "TTL should be set");
        assertTrue(ttl <= 300 && ttl > 0, "TTL should be approximately 300 seconds");
    }

    @Test
    @DisplayName("Проверка валидного кода подтверждения возвращает true")
    void testVerifyConfirmationCode_ValidCode_ReturnsTrue() {
        ConfirmationCode confirmationCode = new ConfirmationCode("test@example.com", "1234");
        confirmationCodeService.saveConfirmationCode(confirmationCode);

        boolean result = confirmationCodeService.verifyConfirmationCode(confirmationCode);

        assertTrue(result, "Verification should return true for valid code");
    }

    @Test
    @DisplayName("Проверка невалидного кода подтверждения возвращает false")
    void testVerifyConfirmationCode_InvalidCode_ReturnsFalse() {
        ConfirmationCode validCode = new ConfirmationCode("test@example.com", "1234");
        ConfirmationCode invalidCode = new ConfirmationCode("test@example.com", "5678");
        confirmationCodeService.saveConfirmationCode(validCode);

        boolean result = confirmationCodeService.verifyConfirmationCode(invalidCode);

        assertFalse(result, "Verification should return false for invalid code");
    }

    @Test
    @DisplayName("Проверка несуществующего кода подтверждения возвращает false")
    void testVerifyConfirmationCode_NonExistentCode_ReturnsFalse() {
        ConfirmationCode confirmationCode = new ConfirmationCode("test@example.com", "1234");

        boolean result = confirmationCodeService.verifyConfirmationCode(confirmationCode);

        assertFalse(result, "Verification should return false for non-existent code");
    }

    @Test
    @DisplayName("Удаление кода подтверждения из Redis")
    void testDeleteConfirmationCode_RemovesCode() {
        ConfirmationCode confirmationCode = new ConfirmationCode("test@example.com", "1234");
        confirmationCodeService.saveConfirmationCode(confirmationCode);
        String key = "confirmation:email:test@example.com";
        assertEquals("1234", redisTemplate.opsForValue().get(key), "Code should be saved before deletion");

        confirmationCodeService.deleteConfirmationCode(confirmationCode.email());

        String storedCode = redisTemplate.opsForValue().get(key);
        assertNull(storedCode, "Code should be deleted");
    }

    @Test
    @DisplayName("Перезапись существующего кода подтверждения")
    void testSaveConfirmationCode_OverwritesExistingCode() {
        ConfirmationCode oldCode = new ConfirmationCode("test@example.com", "1234");
        ConfirmationCode newCode = new ConfirmationCode("test@example.com", "5678");
        String key = "confirmation:email:test@example.com";
        confirmationCodeService.saveConfirmationCode(oldCode);
        assertEquals("1234", redisTemplate.opsForValue().get(key), "Old code should be saved");

        confirmationCodeService.saveConfirmationCode(newCode);

        String storedCode = redisTemplate.opsForValue().get(key);
        assertEquals("5678", storedCode, "New code should overwrite old code");
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl, "TTL should be set");
        assertTrue(ttl <= 300 && ttl > 0, "TTL should be approximately 300 seconds");
    }

    @Test
    @DisplayName("Проверка истечения TTL кода подтверждения")
    void testSaveConfirmationCode_TTLExpires() throws InterruptedException {
        ConfirmationCode confirmationCode = new ConfirmationCode("test@example.com", "1234");
        String key = "confirmation:email:test@example.com";

        redisTemplate.opsForValue().set(key, confirmationCode.code(), 1, TimeUnit.SECONDS);

        Thread.sleep(1500); // Ждем, пока ключ истечет

        String storedCode = redisTemplate.opsForValue().get(key);
        assertNull(storedCode, "Code should be null after TTL expires");
    }
}
