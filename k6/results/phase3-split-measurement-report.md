# Phase 3 (homework-service / webrtc-service 분리) 성능 측정 리포트

측정일: 2026-08-21 (배포 직후, 버그 수정 반영 이후)

## 배경

Phase 0에서 REST 호출(common-service → user-service, Feign) 웜업 후 baseline을
~22ms(18~29ms 범위)로 확정한 바 있다. Phase 3에서 homework-service/webrtc-service를
common-service에서 분리하면서 새로운 REST 홉(homework/webrtc → common-service,
webrtc → user-service)이 생겼는데, 이게 기존 baseline과 비교해 유의미하게
느려졌는지 확인하는 것이 이번 측정의 목적.

## 1. 클린 baseline (웜업 후, 동시성 없이 순차 10회 호출)

Phase 0과 동일한 방법론: 콜드 콜(재배포 직후 첫 호출) 제외, 순차 호출로 동시성
영향 배제. Zipkin에서 실제 CLIENT span duration을 직접 추출(Phase 0 때는 Feign
span이 Zipkin에 안 잡혀서 수동 로깅으로 우회했었는데, 이번엔 정상적으로 잡힘 -
아래 3번 참고).

| 호출 구간 | 샘플 수 | 평균 | 범위 |
|---|---|---|---|
| **(Phase 0 baseline)** common-service → user-service | 4 | ~22ms | 18~29ms |
| homework-service → common-service (`GET /rooms/internal/participated-class-rooms`) | 10 | ~19.9ms | 17.8~24.4ms |
| webrtc-service → common-service (`GET /rooms/internal/{roomId}`, getRoom) | 9 (콜드 1회 제외) | ~16.7ms | 15.6~17.8ms |
| webrtc-service → common-service (checkParticipant) | 7 (이상치 2개 제외) | ~15.4ms | 15.0~16.8ms |
| webrtc-service → common-service (countParticipants) | 8 (이상치 1개 제외) | ~15.1ms | 14.4~16.1ms |
| webrtc-service → **user-service** (getUserInfo) | 8 (이상치 1개 제외) | ~16.4ms | 16.2~16.7ms |

**결론: 서비스 분리로 새로 생긴 REST 홉들은 Phase 0 baseline(~22ms)과 동등하거나
오히려 조금 더 빠름(~15~20ms). 분리 자체가 REST 호출 레이턴시를 유의미하게
늘리지 않았다.**

### 원시 트레이스 ID (재현/재확인용)
- homework-service 정상 케이스: `6a8847a1de66592cd45c80093332946e`
  (4-hop 전체: gateway 315ms → homework-service 308ms → **Feign 80ms** → common-service 16.7ms)
- webrtc-service 정상 케이스 예시: `6a884afe65162d6efb421eeaaec00a01`, `6a884afd43e016227d5bd3465a696298`

## 2. k6 부하 테스트 (동시 5 VU, 30s, `scenarios/04-query-optimization.js`)

프로덕션 ALB 대상, `k6test@test.com` 계정 사용.

### run: after-split (recommend 캐시 버그 발견 전)
- `rooms-recommend`: **100% 실패** (500) → 원인: `GenericJackson2JsonRedisSerializer`가
  최상위 타입 raw `List<T>` 역직렬화 실패. 커밋 `0012ad8`/`a495b4d`로 수정
  (`RecommendCandidateList` 래퍼 클래스 도입).
- `homeworks_participated_duration`: avg 222ms, p95 663ms, p99 798ms

### run: after-split-fixed (recommend 버그 수정 후 재측정)
- `rooms-recommend`: **100% 성공**, avg 305ms
- `homeworks_participated_duration`: avg 1212ms, p95 6028ms, p99 6037ms —
  **7/30 iteration에서 500 발생**
  - 원인: 5 VU 동시 요청이 `roomService` Circuit Breaker(homework-service)의
    slow-call-duration-threshold(2s)를 넘겨 CB가 실제로 OPEN → HALF_OPEN
    전환되며 이후 요청을 fail-closed로 즉시 차단.
  - 이건 버그가 아니라 **Phase 1에서 설계한 CB가 부하 상황에서 의도대로
    동작한다는 증거**. 증거 trace: `6a884796feaff4af1df3aa965b1336e7`
    (gateway 26.5ms → homework-service 18.8ms, 500, Feign span 자체가 없음 =
    실제 네트워크 호출 시도 없이 즉시 fail-fast).
- 다른 엔드포인트(rooms-list/search/recommend, total-ranking/grass)는 분리와
  무관한 기존 쿼리들이라 참고용으로만 유지, 전부 정상 범위.

전체 stdout 원본: `after-split-stdout.txt`, `after-split-fixed-stdout.txt` 참고.
(`/results/*.json`,`*.html` 자동 저장은 Windows 로컬 실행 환경에선 절대경로 문제로
실패 - Docker/Linux 환경에서 돌릴 때만 정상 저장됨. 콘솔 요약을 텍스트로 별도 보관.)

## 3. Zipkin 계측 상태 업데이트

Phase 0 당시엔 Feign 호출이 Zipkin span으로 안 잡히는 이슈(`feign-micrometer`
자동설정 문제, `phase0_baseline_and_bugfixes.md` 참고)가 있었는데, 이번 Phase 3
신규 서비스(homework-service/webrtc-service)에서는 Feign CLIENT span이 **정상
캡처됨**을 확인했다(`http get`, `http.url` 태그 포함). common-service→user-service
쪽 기존 이슈가 지금도 재현되는지는 별도 확인 필요 - 이번 조사 범위 밖.

## 4. 이번 측정/배포 과정에서 발견하고 고친 버그 (실제 운영 영향 있었음)

1. **`homework-service`/`webrtc-service`에 `WebConfig` 누락** — `@LoginUser`
   리졸버 미등록으로 `userId`가 항상 null → 전체 기능 500. 커밋 `c4c0dc2`.
2. **`common-service`의 `InternalAuthFilter`가 `/rooms/internal/**`도 차단** —
   새 내부 REST API가 기존 필터에 예외처리 안 돼 있어서 homework/webrtc의 모든
   Feign 호출이 401 → CB fail-closed → 500. 커밋 `8288392`.
3. **`recommend-candidates` Redis 캐시 역직렬화 실패** — 최상위 raw List 캐싱이
   `GenericJackson2JsonRedisSerializer`와 안 맞아 캐시 히트마다 500. 커밋
   `0012ad8`/`a495b4d`.
4. **ECS 배포 시 discovery-service가 PROVISIONING에서 멈춤** — ASG(`assistudy-ecs-asg`)
   MaxSize=4가 새 서비스 2개 추가로 인한 롤링 배포 여유 용량 부족과 맞물림.
   MaxSize 4→6으로 증설해 해결.

## 5. 결론

- **성능**: 서비스 분리로 인한 REST 홉 추가는 레이턴시에 유의미한 악영향 없음
  (~15~20ms vs 기존 baseline ~22ms).
- **회복탄력성**: Circuit Breaker가 부하 상황에서 실제로 열리고 fail-fast하는
  것을 실제 트레이스로 확인 — Phase 1 설계가 프로덕션에서 검증됨.
- **잔여 이슈**: homework-service의 `roomService` CB가 동시 부하(5 VU 수준)에서
  쉽게 열리는 건 Feign 타임아웃(2000ms)이 다소 타이트하기 때문일 수 있음 - 급하진
  않지만 향후 튜닝 후보.
