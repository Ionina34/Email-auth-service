package effectivemobile.practice.apachekafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import effectivemobile.practice.model.kafka.ConfirmationCode;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Profile("apache-kafka")
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private  String bootstrapServers;

    public KafkaProducer<String, ConfirmationCode> createProducer(ObjectMapper objectMapper) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());

        return new KafkaProducer<>(props, new StringSerializer(), new JsonSerializer(objectMapper));
    }

    public static class JsonSerializer implements Serializer<ConfirmationCode> {
        private final ObjectMapper objectMapper;

        public JsonSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(String s, ConfirmationCode confirmationCode) {
            try {
                return objectMapper.writeValueAsBytes(confirmationCode);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing ConfirmationCode: " + e.getMessage(), e);
            }
        }
    }
}
