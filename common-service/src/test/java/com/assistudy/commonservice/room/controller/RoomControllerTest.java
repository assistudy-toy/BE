package com.assistudy.commonservice.room.controller;

import com.assistudy.commonservice.room.dto.request.CreateRoomRequest;
import com.assistudy.commonservice.room.dto.request.JoinRoomRequest;
import com.assistudy.commonservice.room.dto.request.UpdateRoomRequest;
import com.assistudy.commonservice.room.entity.Room;
import com.assistudy.commonservice.room.entity.RoomParticipant;
import com.assistudy.commonservice.room.entity.enums.RoomType;
import com.assistudy.commonservice.room.repository.RoomParticipantRepository;
import com.assistudy.commonservice.room.repository.RoomRepository;
import com.assistudy.commonservice.support.IntegrationTestSupport;
import com.assistudy.shared.constants.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerTest extends IntegrationTestSupport {

    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository roomParticipantRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long HOST_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private Room saveRoom(RoomType type, boolean isPrivate, String rawPassword, int maxParticipants) {
        Room room = Room.builder()
                .hostUserId(HOST_ID)
                .name("테스트방")
                .type(type)
                .isPrivate(isPrivate)
                .password(rawPassword == null ? null : passwordEncoder.encode(rawPassword))
                .micActive(false)
                .maxParticipants(maxParticipants)
                .isActive(true)
                .isDeleted(false)
                .build();
        return roomRepository.save(room);
    }

    private void saveParticipant(Room room, Long userId) {
        roomParticipantRepository.save(RoomParticipant.builder()
                .room(room)
                .userId(userId)
                .isDeleted(false)
                .build());
    }

    @Test
    void 방을_생성한다() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .name("스터디방")
                .type(RoomType.STUDY)
                .isPrivate(false)
                .micActive(true)
                .maxParticipants(4)
                .build();

        mockMvc.perform(post("/rooms")
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("스터디방"))
                .andExpect(jsonPath("$.result.maxParticipants").value(4));
    }

    @Test
    void LiveKit_방_프로비저닝이_실패하면_보상_트랜잭션으로_방_생성이_취소된다() throws Exception {
        doThrow(new RuntimeException("LiveKit down")).when(webRtcServiceClient).provisionRoom(anyLong());

        CreateRoomRequest request = CreateRoomRequest.builder()
                .name("프로비저닝실패방")
                .type(RoomType.STUDY)
                .isPrivate(false)
                .micActive(true)
                .maxParticipants(4)
                .build();

        mockMvc.perform(post("/rooms")
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ROOM016"));

        boolean anyActiveRoomWithThatName = roomRepository.findAll().stream()
                .filter(r -> "프로비저닝실패방".equals(r.getName()))
                .anyMatch(r -> !r.getIsDeleted());
        assertThat(anyActiveRoomWithThatName).isFalse();
    }

    @Test
    void 비공개방은_비밀번호가_없으면_생성에_실패한다() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .name("비공개방")
                .type(RoomType.STUDY)
                .isPrivate(true)
                .micActive(true)
                .maxParticipants(4)
                .build();

        mockMvc.perform(post("/rooms")
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM011"));
    }

    @Test
    void 방_상세를_조회한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);

        mockMvc.perform(get("/rooms/{roomId}", room.getId())
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(room.getId()));
    }

    @Test
    void 존재하지_않는_방_조회는_404에_해당하는_에러코드를_반환한다() throws Exception {
        mockMvc.perform(get("/rooms/{roomId}", 999999L)
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM001"));
    }

    @Test
    void 방에_참가한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);

        JoinRoomRequest request = JoinRoomRequest.builder().roomId(room.getId()).build();

        mockMvc.perform(post("/rooms/join")
                        .header(HeaderConstants.USER_ID_HEADER, OTHER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        boolean joined = roomParticipantRepository
                .findByRoomIdAndUserIdAndIsDeletedFalse(room.getId(), OTHER_ID)
                .isPresent();
        assertThat(joined).isTrue();
    }

    @Test
    void 비공개방_참가시_비밀번호가_틀리면_실패한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, true, "1234", 4);
        saveParticipant(room, HOST_ID);

        JoinRoomRequest request = JoinRoomRequest.builder().roomId(room.getId()).password("0000").build();

        mockMvc.perform(post("/rooms/join")
                        .header(HeaderConstants.USER_ID_HEADER, OTHER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM012"));
    }

    @Test
    void 방을_나간다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);
        saveParticipant(room, OTHER_ID);

        mockMvc.perform(post("/rooms/{roomId}/leave", room.getId())
                        .header(HeaderConstants.USER_ID_HEADER, OTHER_ID))
                .andExpect(status().isOk());

        boolean stillJoined = roomParticipantRepository
                .findByRoomIdAndUserIdAndIsDeletedFalse(room.getId(), OTHER_ID)
                .isPresent();
        assertThat(stillJoined).isFalse();
    }

    @Test
    void 방장이_아니면_방_수정에_실패한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);

        UpdateRoomRequest request = UpdateRoomRequest.builder()
                .name("바뀐이름")
                .maxParticipants(4)
                .build();

        mockMvc.perform(put("/rooms/{roomId}", room.getId())
                        .header(HeaderConstants.USER_ID_HEADER, OTHER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM007"));
    }

    @Test
    void 방장이_방을_수정한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);

        UpdateRoomRequest request = UpdateRoomRequest.builder()
                .name("바뀐이름")
                .maxParticipants(10)
                .build();

        mockMvc.perform(put("/rooms/{roomId}", room.getId())
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Room updated = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("바뀐이름");
        assertThat(updated.getMaxParticipants()).isEqualTo(10);
    }

    @Test
    void 방장이_방을_삭제한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);

        mockMvc.perform(delete("/rooms/{roomId}", room.getId())
                        .header(HeaderConstants.USER_ID_HEADER, HOST_ID))
                .andExpect(status().isOk());

        Room deleted = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(deleted.getIsDeleted()).isTrue();
    }

    @Test
    void 방장이_아니면_방_삭제에_실패한다() throws Exception {
        Room room = saveRoom(RoomType.STUDY, false, null, 4);
        saveParticipant(room, HOST_ID);

        mockMvc.perform(delete("/rooms/{roomId}", room.getId())
                        .header(HeaderConstants.USER_ID_HEADER, OTHER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM008"));
    }
}
