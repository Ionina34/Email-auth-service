package effectivemobile.practice.springkafka.listener;

import effectivemobile.practice.model.kafka.ConfirmationCode;
import effectivemobile.practice.service.ConfirmationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile("spring-kafka")
public class SpringKafkaEventListener {

    private final ConfirmationCodeService confirmationCodeService;

    @Autowired
    public SpringKafkaEventListener(ConfirmationCodeService confirmationCodeService) {
        this.confirmationCodeService = confirmationCodeService;
    }

    @KafkaListener(topics = "${app.kafka.kafkaConfirmationCodeListener}",
            groupId = "kafkaEventGroupId",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeConfirmationCode(@Payload ConfirmationCode confirmationCode,
                                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) UUID key,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) String partition,
                                        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp) {
        confirmationCodeService.saveConfirmationCode(confirmationCode);
    }
}
