package com.assistudy.homeworkservice.service.query;

import com.assistudy.homeworkservice.converter.HomeworkConverter;
import com.assistudy.homeworkservice.dto.response.GetHomeworksByRoomAndDateResponse;
import com.assistudy.homeworkservice.dto.response.UserParticipatedRoomsWithHomeworkResponse;
import com.assistudy.homeworkservice.entity.Feedback;
import com.assistudy.homeworkservice.entity.Homework;
import com.assistudy.homeworkservice.exception.HomeworkErrorCode;
import com.assistudy.homeworkservice.exception.HomeworkException;
import com.assistudy.homeworkservice.global.client.RoomServiceClient;
import com.assistudy.homeworkservice.global.dto.response.ParticipatedRoomResponse;
import com.assistudy.homeworkservice.global.dto.response.RoomSummaryResponse;
import com.assistudy.homeworkservice.repository.FeedbackRepository;
import com.assistudy.homeworkservice.repository.HomeworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HomeworkQueryServiceImpl implements HomeworkQueryService {

	private final HomeworkRepository homeworkRepository;
	private final FeedbackRepository feedbackRepository;
	private final RoomServiceClient roomServiceClient;

	@Override
	public GetHomeworksByRoomAndDateResponse getHomeworksByRoomAndDate(Long roomId, LocalDate date, Long userId) {
		RoomSummaryResponse room = getRoomById(roomId);
		boolean isHost = room.hostUserId().equals(userId);
		List<Homework> homeworks = homeworkRepository.findAllByRoomIdAndDate(roomId, date);

		// 해당 날짜에 과제가 없으면 빈 리스트 반환
		if (homeworks.isEmpty()) {
			return GetHomeworksByRoomAndDateResponse.builder()
					.homeworks(List.of())
					.isHost(isHost)
					.build();
		}

		return HomeworkConverter.toGetHomeworksByRoomAndDateResponse(homeworks, isHost);
	}

	@Override
	public UserParticipatedRoomsWithHomeworkResponse getUserParticipatedRoomsWithHomework(Long userId) {
		// 사용자가 참여했던 CLASS 타입의 모든 방 조회 (현재 참여 중인 방 + 나간 방)
		List<ParticipatedRoomResponse> participations = roomServiceClient.getParticipatedClassRooms(userId).getResult();

		if (participations == null || participations.isEmpty()) {
			return HomeworkConverter.toUserParticipatedRoomsWithHomeworkResponse(List.of());
		}

		List<Long> roomIds = participations.stream()
				.map(participation -> participation.room().id())
				.distinct()
				.toList();

		// 방마다 반복 조회하던 과제 목록을 한 번에 조회해서 room_id로 묶어둠
		Map<Long, List<Homework>> homeworksByRoomId = homeworkRepository.findByRoomIdInAndIsDeletedFalseOrderByDateDesc(roomIds).stream()
				.collect(Collectors.groupingBy(Homework::getRoomId));

		// 피드백은 "호스트가 아닌 방"의 과제에서만 실제로 쓰이니(아래 !isHost 분기),
		// 그 범위로만 미리 좁혀서 조회
		List<Long> nonHostRoomIds = participations.stream()
				.filter(participation -> !participation.room().hostUserId().equals(userId))
				.map(participation -> participation.room().id())
				.distinct()
				.toList();

		List<Long> homeworkIdsNeedingFeedback = nonHostRoomIds.stream()
				.flatMap(roomId -> homeworksByRoomId.getOrDefault(roomId, List.of()).stream())
				.map(Homework::getId)
				.toList();

		// 과제마다 반복 조회하던 피드백도 한 번에 조회해서 homework_id로 묶어둠
		Map<Long, List<Feedback>> feedbacksByHomeworkId = homeworkIdsNeedingFeedback.isEmpty()
				? Map.of()
				: feedbackRepository.findByHomeworkIdInAndIsDeletedFalseOrderByDateDesc(homeworkIdsNeedingFeedback).stream()
						.collect(Collectors.groupingBy(feedback -> feedback.getHomework().getId()));

		List<UserParticipatedRoomsWithHomeworkResponse.RoomWithHomeworkInfo> roomInfos = participations.stream()
				.map(participation -> {
					RoomSummaryResponse room = participation.room();
					boolean isHost = room.hostUserId().equals(userId);

					List<Homework> homeworks = homeworksByRoomId.getOrDefault(room.id(), List.of());

					// 과제 정보 변환
					List<UserParticipatedRoomsWithHomeworkResponse.HomeworkInfo> homeworkInfos = homeworks.stream()
							.map(homework -> {
								String feedbackText = null;
								// 호스트가 아닌 경우에만 피드백 정보 포함 (있다면 첫 번째 피드백만) - 기존 동작 그대로 유지
								if (!isHost) {
									List<Feedback> feedbacks = feedbacksByHomeworkId.getOrDefault(homework.getId(), List.of());
									if (!feedbacks.isEmpty()) {
										feedbackText = feedbacks.get(0).getFeedback();
									}
								}
								return HomeworkConverter.toUserHomeworkInfo(homework, feedbackText);
							})
							.collect(Collectors.toList());

					// 방 정보 생성
					return HomeworkConverter.toRoomWithHomeworkInfo(
							room,
							isHost,
							!participation.participationDeleted(),
							homeworkInfos);
				})
				.collect(Collectors.toList());

		return HomeworkConverter.toUserParticipatedRoomsWithHomeworkResponse(roomInfos);
	}

	// ================= 내부 유틸 메서드 =================

	private RoomSummaryResponse getRoomById(Long roomId) {
		RoomSummaryResponse room = roomServiceClient.getRoom(roomId).getResult();
		if (room == null) {
			throw new HomeworkException(HomeworkErrorCode.ROOM_NOT_FOUND);
		}
		return room;
	}
}
