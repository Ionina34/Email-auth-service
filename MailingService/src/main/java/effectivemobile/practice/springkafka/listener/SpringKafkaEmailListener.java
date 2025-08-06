package effectivemobile.practice.springkafka.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import effectivemobile.practice.springkafka.service.SpringNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
@Profile("spring-kafka")
public class SpringKafkaEmailListener {

    private final SpringNotificationService notificationService;

    @Autowired
    public SpringKafkaEmailListener(SpringNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.kafka.kafkaRegistrationRequestListener}",
            groupId = "kafkaEventGroupId",
            containerFactory = "kafkaListenerContainerFactory")
    public void listen(@Payload String email,
                       @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) UUID key,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                       @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp) throws JsonProcessingException, ExecutionException, InterruptedException {
        notificationService.processRegistrationRequest(email);
    }
}
