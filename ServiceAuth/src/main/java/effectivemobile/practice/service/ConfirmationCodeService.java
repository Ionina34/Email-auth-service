package effectivemobile.practice.service;

import effectivemobile.practice.model.kafka.ConfirmationCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class ConfirmationCodeService {

    private static final String CONFIRMATION_KEY_PREFIX = "confirmation:email:";
    private static final long CODE_TTL_SECONDS = 300; //5мин

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public ConfirmationCodeService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveConfirmationCode(ConfirmationCode confirmationCode) {
        String key = CONFIRMATION_KEY_PREFIX + confirmationCode.email();
        redisTemplate.opsForValue().set(key, confirmationCode.code(), CODE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public boolean verifyConfirmationCode(ConfirmationCode confirmationCode) {
        String key = CONFIRMATION_KEY_PREFIX + confirmationCode.email();
        String storeCode = redisTemplate.opsForValue().get(key);
        if (storeCode == null) {
            return false;
        }
        return storeCode.equals(confirmationCode.code());
    }

    public void deleteConfirmationCode(String email) {
        String key = CONFIRMATION_KEY_PREFIX + email;
        redisTemplate.delete(key);
    }
}
