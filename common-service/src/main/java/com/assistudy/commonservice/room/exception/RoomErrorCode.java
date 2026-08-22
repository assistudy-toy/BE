package com.assistudy.commonservice.room.exception;

import com.assistudy.shared.response.ApiResponse;
import com.assistudy.shared.exception.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RoomErrorCode implements BaseErrorCode {

    ROOM_NOT_FOUND(HttpStatus.BAD_REQUEST, "ROOM001", "방을 찾을 수 없습니다."),
    ROOM_INACTIVE(HttpStatus.BAD_REQUEST, "ROOM002", "비활성화된 방입니다."),
    ROOM_DELETED(HttpStatus.BAD_REQUEST, "ROOM003", "삭제된 방입니다."),
    ROOM_FULL(HttpStatus.BAD_REQUEST, "ROOM004", "방이 가득 찼습니다."),
    ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "ROOM005", "이미 존재하는 방입니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ROOM006", "방에 접근할 권한이 없습니다."),
    ROOM_UPDATE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "ROOM007", "방을 수정할 권한이 없습니다."),
    ROOM_DELETE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "ROOM008", "방을 삭제할 권한이 없습니다."),
    ROOM_ALREADY_JOINED(HttpStatus.CONFLICT, "ROOM009", "이미 참여 중인 방입니다."),
    ROOM_NOT_JOINED(HttpStatus.BAD_REQUEST, "ROOM010", "참여하지 않은 방입니다."),
    PRIVATE_ROOM_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "ROOM011", "비공개 방은 비밀번호가 필요합니다."),
    PASSWORD_NOT_MATCHED(HttpStatus.BAD_REQUEST, "ROOM012", "비밀번호가 일치하지 않습니다."),
    INVALID_ROOM_REQUEST(HttpStatus.BAD_REQUEST, "ROOM013", "유효하지 않은 방 요청입니다."),
    ROOM_MAX_PARTICIPANTS_TOO_SMALL(HttpStatus.BAD_REQUEST, "ROOM014", "최대 참여자 수는 현재 참여자 수보다 적을 수 없습니다."),
    NO_UPDATE_FIELDS(HttpStatus.BAD_REQUEST, "ROOM015", "업데이트할 필드가 없습니다."),
    ROOM_PROVISIONING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ROOM016", "화상회의 방 생성에 실패해 방 생성이 취소되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public <T> ApiResponse<T> getResponse() {
        return ApiResponse.onFailure(this.status, this.code, this.message, null);
    }
} 