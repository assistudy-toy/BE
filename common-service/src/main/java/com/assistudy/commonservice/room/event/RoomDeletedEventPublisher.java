package com.assistudy.commonservice.room.event;

import com.assistudy.shared.event.RoomDeletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * room 삭제 트랜잭션이 실제로 커밋된 이후에만 Kafka로 RoomDeletedEvent를 발행한다
 * (choreography SAGA - homework-service가 구독해서 관련 Homework/Feedback을 정리함).
 *
 * AFTER_COMMIT 시점에 발행하므로 "방은 안 지워졌는데 이벤트만 나가는" 상황은 없다.
 * 다만 이 시점 이후 Kafka 발행 자체가 실패하면(예: 브로커 장애) 이벤트가 유실될 수 있음 -
 * 완전한 outbox 패턴(DB에 이벤트를 커밋과 같은 트랜잭션으로 남기고 별도 폴러가 발행)은
 * 이번 스코프에서는 적용하지 않음(알려진 한계로 남겨둠, #40).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomDeletedEventPublisher {

    private static final String TOPIC = "room-deleted";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RoomDeletedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            String key = String.valueOf(event.roomId());

            kafkaTemplate.send(TOPIC, key, message).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("[RoomDeletedEvent] published - roomId={}, offset={}",
                            event.roomId(), result.getRecordMetadata().offset());
                } else {
                    log.error("[RoomDeletedEvent] failed to publish - roomId={}: {}",
                            event.roomId(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("[RoomDeletedEvent] failed to serialize - roomId={}: {}", event.roomId(), e.getMessage(), e);
        }
    }
}
