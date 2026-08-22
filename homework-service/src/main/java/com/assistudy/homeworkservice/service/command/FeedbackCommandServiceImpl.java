package com.assistudy.homeworkservice.service.command;

import com.assistudy.homeworkservice.converter.FeedbackConverter;
import com.assistudy.homeworkservice.dto.request.CreateFeedbackRequest;
import com.assistudy.homeworkservice.dto.request.UpdateFeedbackByHostRequest;
import com.assistudy.homeworkservice.dto.response.CreateFeedbackResponse;
import com.assistudy.homeworkservice.entity.Feedback;
import com.assistudy.homeworkservice.entity.Homework;
import com.assistudy.homeworkservice.exception.FeedbackErrorCode;
import com.assistudy.homeworkservice.exception.FeedbackException;
import com.assistudy.homeworkservice.global.client.RoomServiceClient;
import com.assistudy.homeworkservice.global.dto.response.RoomSummaryResponse;
import com.assistudy.homeworkservice.repository.FeedbackRepository;
import com.assistudy.homeworkservice.repository.HomeworkRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FeedbackCommandServiceImpl implements FeedbackCommandService {

    private final FeedbackRepository feedbackRepository;
    private final HomeworkRepository homeworkRepository;
    private final RoomServiceClient roomServiceClient;

    @Override
    public CreateFeedbackResponse createFeedback(CreateFeedbackRequest request, Long userId) {
        // homeworkId로 과제 조회
        Homework homework = getHomeworkById(request.getHomeworkId());

        // 사용자가 해당 과제의 방에 참여 중인지 권한 확인
        validateUserParticipationInRoom(homework.getRoomId(), request.getUserId());

        // 중복 피드백 검증 (해당 과제에 해당 사용자가 이미 피드백을 작성했는지 확인)
        validateDuplicateFeedback(homework.getId(), request.getUserId());

        Feedback feedback = Feedback.builder()
                .homework(homework)
                .userId(request.getUserId())
                .date(LocalDate.now())
                .feedback(request.getFeedback())
                .build();

        Feedback savedFeedback = feedbackRepository.save(feedback);

        return FeedbackConverter.toCreateFeedbackResponse(savedFeedback);
    }

    @Override
    public void deleteFeedback(Long feedbackId, Long userId) {
        Feedback feedback = getFeedbackById(feedbackId);
        validateFeedbackDeletePermission(feedback, userId);
        feedbackRepository.delete(feedback);
    }

    @Override
    public void updateFeedbackByHost(UpdateFeedbackByHostRequest request, Long userId) {
        // 피드백 조회
        Feedback feedback = getFeedbackById(request.getFeedbackId());

        // 호스트 권한 확인 (피드백이 속한 과제의 방 호스트인지 확인)
        validateFeedbackUpdatePermission(feedback, userId);

        // 피드백 수정
        Feedback updatedFeedback = Feedback.builder()
                .id(feedback.getId())
                .homework(feedback.getHomework())
                .userId(feedback.getUserId())
                .date(feedback.getDate())
                .feedback(request.getFeedback())
                .isDeleted(feedback.getIsDeleted())  // builder로 재생성하면서 soft-delete 상태가 초기화되지 않도록 유지
                .build();

        feedbackRepository.save(updatedFeedback);
    }

    // ================= 내부 유틸 메서드 =================

    private Homework getHomeworkById(Long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.HOMEWORK_NOT_FOUND));
    }

    private Feedback getFeedbackById(Long feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new FeedbackException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));
    }

    private void validateDuplicateFeedback(Long homeworkId, Long userId) {
        if (feedbackRepository.findByHomeworkIdAndUserIdAndIsDeletedFalse(homeworkId, userId).isPresent()) {
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_ALREADY_EXISTS);
        }
    }

    private void validateFeedbackUpdatePermission(Feedback feedback, Long userId) {
        RoomSummaryResponse room = getRoomById(feedback.getHomework().getRoomId());
        if (!room.hostUserId().equals(userId)) {
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_UPDATE_PERMISSION_DENIED);
        }
    }

    private void validateFeedbackDeletePermission(Feedback feedback, Long userId) {
        if (!feedback.getUserId().equals(userId)) {
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_DELETE_PERMISSION_DENIED);
        }
    }

    private void validateUserParticipationInRoom(Long roomId, Long userId) {
        boolean isParticipant = Boolean.TRUE.equals(roomServiceClient.checkParticipant(roomId, userId).getResult());
        if (!isParticipant) {
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_CREATE_PERMISSION_DENIED);
        }
    }

    private RoomSummaryResponse getRoomById(Long roomId) {
        RoomSummaryResponse room = roomServiceClient.getRoom(roomId).getResult();
        if (room == null) {
            throw new FeedbackException(FeedbackErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }
}
