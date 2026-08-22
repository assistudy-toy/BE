package com.assistudy.commonservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * room 삭제 이벤트(RoomDeletedEvent) 발행용 Kafka 프로듀서 설정.
 * 직렬화는 log-send-service와 동일하게 ObjectMapper로 수동 JSON 문자열 변환 후 String으로 전송.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ObservationRegistry observationRegistry) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        // Zipkin에서 방 삭제 HTTP 요청 -> Kafka 발행 -> homework-service 소비까지 하나의
        // 트레이스로 이어서 보기 위해 명시적으로 활성화 (수동 빈이라 Boot 자동설정을 안 탐)
        template.setObservationEnabled(true);
        template.setObservationRegistry(observationRegistry);
        return template;
    }

    @Bean
    public NewTopic roomDeletedTopic() {
        return TopicBuilder.name("room-deleted").partitions(3).replicas(1).build();
    }
}
