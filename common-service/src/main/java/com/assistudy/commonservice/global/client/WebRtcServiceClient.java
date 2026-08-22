package com.assistudy.commonservice.global.client;

/**
 * webrtc-service 호출 인터페이스. 실제 구현은 {@link WebRtcServiceFeignClient}(원격 호출)를
 * {@link WebRtcServiceClientWrapper}(Circuit Breaker)가 감싸는 형태이며, 호출부는 항상 이 타입으로 주입받는다.
 */
public interface WebRtcServiceClient {

    /**
     * LiveKit 서버에 실제 방 리소스를 생성한다. 실패하면 예외를 그대로 던진다 - 호출부(방 생성 SAGA)가
     * 이 실패를 보고 보상 트랜잭션(방 삭제)을 수행해야 하므로, 다른 클라이언트들과 달리 fail-open으로
     * 감추지 않는다.
     */
    void provisionRoom(Long roomId);
}
