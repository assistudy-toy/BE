# 방 삭제/생성 SAGA 검증 리포트

측정일: 2026-08-22 (prod 환경, 실제 장애 시연 포함)

## 목차
1~5: 방 삭제 SAGA(Choreography, 재시도+DLQ)
6: 방 생성 SAGA(보상 트랜잭션)

## 배경

common-service 분리 이후 Room(common-service DB)과 Homework/Feedback(homework-service
DB)이 물리적으로 분리되어 로컬 트랜잭션으로 정합성을 보장할 수 없게 됨. Kafka 기반
Choreography SAGA(#40)로 방 삭제 시 Homework/Feedback을 비동기 정리하도록 구현하고,
정상 흐름뿐 아니라 실제 장애 상황(컨슈머 다운, 강제 실패)을 prod에서 직접 유발해 검증함.

## 1. 정상 흐름

room 545 삭제 → homework 544 관련 이벤트 처리:
- common-service: `[RoomDeletedEvent] published - roomId=545, offset=0`
- homework-service: `received`/`processed - roomId=545` (약 114ms 내 처리)
- DB 직접 조회: `homework.id=544, room_id=545, is_deleted=1` 확인

## 2. 시나리오 A — 컨슈머 다운 중 이벤트 유실 여부

| 단계 | 결과 |
|---|---|
| homework-service `desired-count 0`으로 다운 | running:0 확인 |
| 방 545 삭제 (`DELETE /rooms/545`) | HTTP 200 (Room 삭제 자체는 Kafka와 무관하게 성공) |
| 다운 상태에서 DB 조회 | `homework.is_deleted=0` — 이벤트가 유실 없이 Kafka에 보존됨 |
| homework-service `desired-count 1`로 복구 | 파티션 재할당 후 자동으로 밀린 이벤트 소비 |

복구 후 로그:
```
17:36:34.690  [RoomDeletedEvent] received - roomId=545
17:36:35.102  [RoomDeletedEvent] processed - roomId=545
```
파티션 할당 완료(`17:36:34.316`) 직후 약 0.4초 내 자동 처리. 이후 DB 재조회 시
`is_deleted=1` 반영 확인.

## 3. 시나리오 B — 재시도 → DLQ 전환 (+ 실제 버그 발견)

임시 fault 코드(sentinel `hostUserId=-999`에서만 예외 발생)를 배포해 강제로
컨슈머를 실패시킴.

**재시도 확인** — `FixedBackOff(1000ms, 2회)` 설정대로 정확히 1초 간격 재시도:
```
18:25:38.782  [RoomDeletedEvent] received - roomId=-1   (1차 시도)
18:25:39.847  [RoomDeletedEvent] received - roomId=-1   (2차 시도, +1.06s)
18:25:40.847  [RoomDeletedEvent] received - roomId=-1   (3차 시도, +1.00s)
18:25:40.467  DeadLetterPublishingRecoverer WARN: 목적지 파티션 불일치
```

**발견한 버그**: `DeadLetterPublishingRecoverer` 기본 목적지 리졸버가 의도한
`room-deleted.DLT`가 아니라 `room-deleted-dlt`(소문자+대시, 브로커 auto-create로
생성된 별개 토픽)로 발행하고 있었음. 미리 3파티션/1레플리카로 프로비저닝해둔
`room-deleted.DLT`는 계속 비어있는 상태였음.

**수정**: 목적지 리졸버를 `record.topic() + ".DLT"`로 명시적으로 지정 (커밋
[261f800](https://github.com/assistudy-toy/BE/commit/261f8006f603508a0784d705a09541c6f7ca0d8c)).
재검증 결과 `room-deleted.DLT`에 원본 메시지가 정상적으로 발행되는 것을
`kafka-console-consumer`로 직접 확인.

검증 완료 후 임시 fault 코드는 제거, DLQ 수정 사항만 남겨 최종 이미지로 재배포.

### 부수 발견: 실제 인프라 장애

시나리오 B 재검증 도중 이 작업과 무관한 실제 장애를 우연히 발견함 — Kafka
EC2 인스턴스(10.20.10.230)의 SSM 제어채널이 끊기고(03:17 KST), 이어서 실제
9092 포트 Kafka 트래픽까지 끊김(03:36 KST). EC2 자체의 AWS 레벨 상태 체크는
계속 "passed"였음(인스턴스 내부 네트워크/Docker 데몬 문제로 추정). 인스턴스
재부팅으로 복구, 이후 재시도 사이클이 재부팅 타이밍과 겹쳐 동일 이벤트가
DLQ에 여러 번 발행되는 현상이 실제로 관찰됨 — Kafka at-least-once 특성상
정상적인 동작이며, 조건부 UPDATE 기반 멱등 처리 덕분에 실제 데이터에는 영향
없음을 확인.

## 4. 멱등성

컨테이너 재배포/재부팅 타이밍과 겹쳐 동일 이벤트가 실제로 여러 차례
재전달되는 상황이 우연히 발생했으나(위 3번 참고), `WHERE isDeleted = false`
조건부 UPDATE 구조 덕분에 결과가 항상 동일함을 실사용 중 확인.

## 5. Zipkin 분산 트레이스 — SAGA 전체 흐름을 하나의 트레이스로 확인

기본적으로 Kafka 발행/소비는 Micrometer Observation이 계측되지 않아 HTTP
요청 트레이스와 끊어져서 보였음. `KafkaTemplate`/리스너 컨테이너에
`setObservationEnabled(true)` + `setObservationRegistry(...)`를 명시적으로
추가(커밋 [182ec45](https://github.com/assistudy-toy/BE/commit/182ec45bb9df234d77ec0a00db568a8f02de6651),
수동으로 등록한 빈이라 Spring Boot 자동설정을 안 타서 직접 설정 필요)한 뒤,
방 546을 생성/삭제해서 재검증.

**결과: gateway → common-service → Kafka 발행 → homework-service 소비까지
하나의 트레이스로 완전히 이어짐.**

트레이스 ID: `6a8921dbcfda0246eefc6ad1f4f57b40`
(원본 JSON: [saga-zipkin-trace-6a8921db.json](./saga-zipkin-trace-6a8921db.json))

| 경과 | 소요 | 서비스 | span |
|---:|---:|---|---|
| 0.00ms | 2282.964ms | apigateway-service | http delete |
| 8.34ms | 2273.139ms | apigateway-service | http delete |
| 11.38ms | 2270.831ms | common-service | http delete /rooms/{roomid} |
| 11.97ms | 0.991ms | common-service | security filterchain before |
| 12.54ms | 0.192ms | common-service | authorize request |
| 13.01ms | 2267.829ms | common-service | secured request |
| 373.78ms | 2004.168ms | common-service | **room-deleted send** (Kafka 발행) |
| 2280.96ms | 0.392ms | common-service | security filterchain after |
| 2393.70ms | 657.329ms | homework-service | **room-deleted process** (Kafka 소비) |

`room-deleted send`가 부모(`secured request`), `room-deleted process`가
`room-deleted send`의 자식으로 정확히 연결됨 — 방 삭제 HTTP 요청과
homework-service의 비동기 이벤트 처리가 하나의 분산 트레이스로 묶여있음을
증명.

**참고**: `room-deleted send`(~2004ms)와 `room-deleted process`(~657ms)의
소요시간은 이례적으로 긴데, 방금 재배포된 직후의 첫 메시지라 Kafka
프로듀서의 초기 연결/메타데이터 fetch, JPA 첫 쿼리 워밍업 등 콜드스타트
비용이 섞여있는 것으로 추정됨 — 실제 정상 흐름(1번 항목)에서는 발행부터
처리까지 총 ~114ms였음. 정상 운영 중 지연시간을 보려면 콜드 콜을 제외한
추가 측정이 필요함(이번 리포트의 주 목적은 지연시간 측정이 아니라 트레이스
연결 자체의 증명).

---

## 6. 방 생성 SAGA — 보상 트랜잭션(Compensating Transaction)

기존 방 삭제 SAGA는 재시도+DLQ(forward recovery)만 쓰고 보상 트랜잭션이
없었는데("삭제 취소"는 도메인적으로 의미가 없어서), 실제로 보상 트랜잭션이
자연스러운 자리를 찾아 새로 추가함: **방 생성 시 webrtc-service를 통해
LiveKit에 실제 방 리소스를 프로비저닝하고, 실패하면 방금 커밋된 Room을
삭제(보상)한다.**

### 설계
- common-service `createRoom()`: Room/RoomParticipant 저장을 `TransactionTemplate`
  + `PROPAGATION_REQUIRES_NEW`로 별도 트랜잭션에 즉시 커밋 (클래스 레벨
  `@Transactional`로 열려있는 바깥 트랜잭션과 무관하게 DB에 반영되어야,
  이후 실패 시 되돌리는 게 "이미 커밋된 걸 되돌리는" 진짜 보상 트랜잭션이 됨 —
  단순 롤백이 아님)
- 커밋 직후 webrtc-service의 `POST /webrtc/internal/rooms/{roomId}` 호출 →
  webrtc-service가 LiveKit SDK의 `RoomServiceClient.createRoom()`으로 실제
  LiveKit 서버에 방 생성 요청
- 실패 시(webrtc-service 다운, LiveKit 오류 등) 별도 `REQUIRES_NEW` 트랜잭션으로
  Room/RoomParticipant를 soft-delete하고, 클라이언트에는 `ROOM016` 에러 응답
  (성공한 것처럼 보이는 채로 뒤에서 조용히 삭제되는 일이 없도록 예외를 그대로
  전파함 — Circuit Breaker도 fail-open이 아니라 재throw로 구성)

### 검증
| 시나리오 | 결과 |
|---|---|
| 정상 생성 (room 548, 551) | webrtc-service 로그 `[LiveKit] 방 생성 성공 - roomName=room_548` 확인, API 200 |
| webrtc-service 강제 다운 후 방 생성 시도 (room 549) | common-service 로그: `Load balancer does not contain an instance for the service webrtc-service` → `[SAGA] LiveKit 방 프로비저닝 실패 - roomId=549, 보상 트랜잭션(방 삭제) 수행` → API `500 ROOM016` 응답 → DB `is_deleted=1` 확인 |
| webrtc-service 복구 후 재시도 | 약 40초 콜드스타트 이후 정상 성공(room 551) — Eureka 등록/로드밸런서 캐시 갱신 지연으로 인한 타이밍 이슈였고, 서비스 자체 문제는 아니었음 |

### Zipkin 분산 트레이스
Kafka와 달리 Feign 호출은 Spring Boot가 기본으로 계측해줘서 별도 설정 없이도
자동으로 하나의 트레이스에 이어짐. room 548 생성 트레이스(`6a892f6a1523fffbd0a2510bec32da90`,
원본: [saga-zipkin-trace-provision-6a892f6a.json](./saga-zipkin-trace-provision-6a892f6a.json)):

```
apigateway-service │ http post
  └─ common-service │ http post /rooms
       └─ common-service │ http post  (Feign으로 webrtc-service 호출)
            └─ webrtc-service │ http post /webrtc/internal/rooms/{roomid}
```

gateway → common-service → (Feign) → webrtc-service까지 방 삭제 SAGA 때와
동일하게 하나의 트레이스로 완전히 이어져 있음을 확인.

### 부수 발견: 실제 버그
1차 검증(room 547)에서 `webrtc-service`가 LiveKit 방 생성 자체는 성공했는데
(`[LiveKit] 방 생성 성공 - roomName=room_547`) 응답을 만드는 과정에서
`ApiResponse.onSuccess(null)`이 `onSuccess(T result)`가 아니라
`onSuccess(BaseSuccessCode code)` 오버로드로 잘못 해석되어 NPE로 500을
반환하는 버그가 있었음. 그 결과 common-service는 이걸 "webrtc-service 실패"로
간주해 정상적으로 보상 트랜잭션(방 547 삭제)을 수행했지만, **LiveKit 서버에는
이미 `room_547`이 실제로 생성되어 남아있는 상태**가 됨 — 로컬 트랜잭션은
정확히 되돌렸지만 원격 부수효과(LiveKit 리소스)까지는 되돌리지 못하는,
SAGA 보상 트랜잭션의 근본적인 한계를 실제로 보여준 사례. `ApiResponse.onSuccess()`
(무인자 버전)로 수정해서 재발은 막았지만, 일반적으로 "응답 실패 ≠ 원격 부수효과
실패"일 수 있다는 점은 구조적으로 남아있는 한계임(LiveKit은 빈 방을
`emptyTimeout` 이후 자동 정리하므로 운영상 심각한 문제는 아님).
