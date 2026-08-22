package com.assistudy.commonservice.global.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * WebRtcServiceClient의 기본 구현체. 실제 원격 호출은 {@link WebRtcServiceFeignClient}에 위임하되
 * Resilience4j Circuit Breaker(instance name: "webRtcService")로 감쌈.
 *
 * {@link UserServiceClientWrapper}의 다른 호출들과 달리 이 호출은 fail-open(완화된 기본값 반환)도
 * fail-closed(무조건 거부)도 아닌 "그대로 재throw"로 설계했다 - 방 생성 SAGA에서 이 실패를
 * 신호로 받아 보상 트랜잭션(방 삭제)을 수행해야 하기 때문에, CB가 실패를 감추면 안 된다.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class WebRtcServiceClientWrapper implements WebRtcServiceClient {

    private static final String CB_NAME = "webRtcService";

    private final WebRtcServiceFeignClient feignClient;

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "provisionRoomFallback")
    public void provisionRoom(Long roomId) {
        feignClient.provisionRoom(roomId);
    }

    private void provisionRoomFallback(Long roomId, Throwable t) {
        log.error("[CB] provisionRoom fallback (재throw, fail-open 아님) - roomId={}, cause={}", roomId, t.toString());
        if (t instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("webrtc-service 방 프로비저닝 실패 - roomId=" + roomId, t);
    }
}
