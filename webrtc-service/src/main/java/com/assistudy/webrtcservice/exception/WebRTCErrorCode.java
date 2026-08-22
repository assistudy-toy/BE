package com.assistudy.webrtcservice.exception;

import com.assistudy.shared.response.ApiResponse;
import com.assistudy.shared.exception.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WebRTCErrorCode implements BaseErrorCode {

    ROOM_NOT_FOUND(HttpStatus.BAD_REQUEST, "WEBRTC001", "방을 찾을 수 없습니다."),
    ROOM_INACTIVE(HttpStatus.BAD_REQUEST, "WEBRTC002", "비활성화된 방입니다."),
    ROOM_DELETED(HttpStatus.BAD_REQUEST, "WEBRTC003", "삭제된 방입니다."),
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "WEBRTC004", "사용자 정보를 찾을 수 없습니다."),
    ROOM_FULL(HttpStatus.BAD_REQUEST, "WEBRTC005", "방이 가득 찼습니다."),
    PRIVATE_ROOM_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "WEBRTC006", "비밀방은 비밀번호가 필요합니다."),
    PASSWORD_NOT_MATCHED(HttpStatus.BAD_REQUEST, "WEBRTC007", "비밀번호가 일치하지 않습니다."),
    ROOM_CLOSE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "WEBRTC008", "방을 종료할 권한이 없습니다."),
    SESSION_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WEBRTC009", "세션 생성에 실패했습니다."),
    TOKEN_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WEBRTC010", "토큰 생성에 실패했습니다."),
    USER_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "WEBRTC011", "방에 참가하지 않은 사용자입니다."),
    INVALID_TOKEN_REQUEST(HttpStatus.BAD_REQUEST, "WEBRTC012", "토큰 생성 요청이 유효하지 않습니다."),
    INVALID_WEBHOOK_DATA(HttpStatus.BAD_REQUEST, "WEBRTC013", "유효하지 않은 Webhook 데이터입니다."),
    SESSION_CLEANUP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WEBRTC014", "세션 정리 중 오류가 발생했습니다."),
    TOKEN_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WEBRTC015", "토큰 생성에 실패했습니다."),
    USER_INFO_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WEBRTC016", "사용자 정보 조회에 실패했습니다."),
    ROOM_PROVISIONING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WEBRTC017", "LiveKit 방 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public <T> ApiResponse<T> getResponse() {
        return ApiResponse.onFailure(this.status, this.code, this.message, null);
    }
}
