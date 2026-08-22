package com.assistudy.homeworkservice.event;

import com.assistudy.homeworkservice.repository.FeedbackRepository;
import com.assistudy.homeworkservice.repository.HomeworkRepository;
import com.assistudy.shared.event.RoomDeletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * common-service가 발행한 room-deleted 이벤트를 구독해서 관련 Homework/Feedback을
 * soft-delete한다(choreography SAGA, #40).
 *
 * 처리는 조건부 UPDATE(WHERE isDeleted = false)라 같은 이벤트가 재전송되어도(Kafka
 * at-least-once) 결과가 동일함(멱등). 성공 시에만 ack하고, 실패하면 예외를 그대로
 * 던져서 KafkaConfig의 DefaultErrorHandler가 재시도 -> 최종 실패 시 DLQ로 넘기게 한다
 * (로그만 남기고 조용히 ack하지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomDeletedEventConsumer {

    private final HomeworkRepository homeworkRepository;
    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "room-deleted", groupId = "homework-service")
    public void handle(String message, Acknowledgment acknowledgment) throws Exception {
        RoomDeletedEvent event = objectMapper.readValue(message, RoomDeletedEvent.class);
        log.info("[RoomDeletedEvent] received - roomId={}", event.roomId());

        feedbackRepository.softDeleteAllByHomeworkRoomId(event.roomId());
        homeworkRepository.softDeleteAllByRoomId(event.roomId());

        acknowledgment.acknowledge();
        log.info("[RoomDeletedEvent] processed - roomId={}", event.roomId());
    }
}
