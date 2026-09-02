# 막차 세이프 (Last Safe) — 스펙 요약

> 이 문서는 Claude Code가 코딩 중 빠르게 참조하는 핵심 요약입니다.
> 전체 근거·개정 이력은 `docs/source/`의 원본 .docx를 참조하세요.
>
> 기준 버전: SRS v0.15 · API 명세서 v0.11 · 알고리즘 설계서 v0.5 · 화면명세서 v0.1

## 1. 프로젝트 개요

- 사용자가 출발지·목적지를 입력하면 막차를 놓치지 않기 위한 **최소 출발 시각**을 계산해 안내하고, **iOS 로컬 알림**으로 알려주는 서비스.
- 목표: (1) 실제 앱스토어 출시, (2) 백엔드 개발자 취업 포트폴리오 — 단순 API 프록시가 아닌 실질 도메인 로직(막차 역산, 알람 스케줄링, 인증, 외부 API 통합) 구현.
- 초기 범위: 수도권 지하철·버스 중심 (MVP).

## 2. 기술 스택 & 레포 구조

| 구성요소 | 스택 | 레포 |
|---|---|---|
| last-safe-server | Java, Spring Boot, MySQL, Redis, Gradle | `ahyeon24/last-safe-server` |
| last-safe-ios | Swift, SwiftUI | `ahyeon24/last-safe-ios` |

로컬 루트: `~/Developer/last-safe-server`, `~/Developer/last-safe-ios`

## 3. 외부 연동 API

| 연동 대상 | 용도 |
|---|---|
| ODsay (`searchPubTransPathT`, `searchSubwaySchedule`) | 경로 탐색 + 지하철역 막차 시각 조회 (단일 API로 통합, Tmap 미사용) |
| 서울시 정류소별 노선 첫차/막차 API (`getBustimeByStation`) | 서울 지역 버스 막차 (정류장 기준 직접 제공, 필드명 `firstBusTm`/`lastBusTm`) — **서비스 종료 안내 표시됨, 실제 가용 여부 API 키 발급 후 검증 필요** |
| 국토교통부 버스노선정보 서비스 API | 서울 외 지역 버스 막차 **추정 계산** (기점 막차 + 경유 정류소 → 정류장 간 평균 소요시간 누적) |

- 알람 발송은 서버 푸시(APNs) 아님 — **iOS 로컬 알림(UNUserNotificationCenter)** 전용.
- ODsay는 월 2회 데이터 갱신, Basic 무료 등급 일 1,000회 제한 (실검증 필요).

## 4. 핵심 설계 결정 (변경 시 반드시 확인받을 것)

- **로컬 알림 전용**: APNs, 푸시 토큰, 재시도 로직, ALARM_LOG 불필요.
- **ODsay 실시간 API + Redis TTL 캐싱** 유지. 배치로 자체 DB 구축하는 방식은 ODsay ToS(데이터 저장 금지) 위반 및 정확도 문제로 기각됨.
- **경로 후보는 `candidate_id` 기반 Redis 캐싱(TTL 10분)**. 클라이언트는 알람 등록 시 계산값을 재전송하지 않고 `candidate_id`만 전달 → 서버가 캐시에서 원본 조회. 클라이언트 제공 계산값을 무검증 신뢰하지 않기 위함.
- **막차 시각 캐시와 candidate 캐시는 TTL이 다름**: 막차 시각 캐시는 당일 자정 만료(긴 TTL), candidate 캐시는 10분(사용자 검토 시간 기준).
- **지도 내비게이션은 스코프 제외**. 카카오맵 등 외부 지도 앱 딥링크(FR-02d)로 대체. URL 스킴 구성은 클라이언트 책임, 서버는 딥링크 URL을 생성하지 않음.
- **출발 시간 사용자 지정 기능 없음**. 서버는 항상 API 요청 수신 시각을 `departure_datetime`으로 사용 (FR-01d 삭제됨, v0.10).
- **경로 조회 이력은 알람 등록된 경로만 DB 저장**, 나머지는 폐기 (보존 기간 정책은 미해결 사항).
- **버퍼(여유시간)는 국토부 추정 기반 버스 구간에만, 구간별로 적용 후 합산**. 지하철·서울시 API 기반 버스에는 미적용.
- **막차 전멸 판정(FR-08a/b)은 버퍼 적용 전 원본 시각(`min_departure_time_raw`) 기준**. 순서를 바꾸면 오판 가능.

## 5. 막차 역산 알고리즘 (알고리즘 설계서 v0.5)

### 핵심 아이디어
경로의 구간(segment)을 **역방향(목적지→출발지)** 으로 순회하며, 각 교통 구간에서 "그 구간 자체 막차 시각"과 "다음 구간이 요구하는 시각에서 역산한 값" 중 더 이른 쪽을 채택. 이 지점이 병목 구간(FR-04)이 됨.

### 입력
- `segments`: 경로 구성 구간 리스트 (ODsay 결과 가공, 출발지→목적지 순)
  - 각 segment: `type`(WALK/SUBWAY/BUS), `duration_sec`, `last_departure_time`, `is_estimated`
- `departure_datetime`: 서버가 요청 수신 시각으로 자동 설정 (사용자 지정 아님)

### 사전 조건
segments에 SUBWAY/BUS가 하나도 없으면(전 구간 도보) 알고리즘 호출 안 함 → "막차 계산 대상 아님" 즉시 처리.

### 의사코드 요지
```
역방향 순회 (마지막 구간 → 첫 구간):
  WALK: pending_walk_sec에 누적만 (즉시 차감 X)
  SUBWAY/BUS:
    last_dep = get_last_departure_time(seg, day_type)
    NO_SERVICE → { status: NO_ROUTE_AVAILABLE, bottleneck_segment_index: i } 반환
    FETCH_ERROR → { status: CALCULATION_FAILED, bottleneck_segment_index: i } 반환

    candidate = last_dep  # (a) 이 구간 자체 막차 제약
    if required_arrival_time != null:
      implied_by_next = required_arrival_time - pending_walk_sec - seg.duration_sec  # (b)
      if implied_by_next < candidate:
        candidate = implied_by_next
        # bottleneck_index 갱신 안 함 (이미 더 뒤쪽 구간에서 확정된 병목이 진짜 병목)
      else:
        bottleneck_index = i  # (a) 채택 = 이 구간이 새 병목 (tie는 (a) 우선)
    else:
      bottleneck_index = i

    required_arrival_time = candidate
    pending_walk_sec = 0  # 초기화

순회 종료 후:
  min_departure_time_raw = required_arrival_time - pending_walk_sec  # 남은 도보시간 최종 반영
  is_last_train_gone = (departure_datetime > min_departure_time_raw)  # 버퍼 적용 '전' 기준

  total_buffer = sum(seg.buffer_sec for seg in segments if seg.type==BUS and seg.is_estimated)
  min_departure_time = min_departure_time_raw - total_buffer

  return { status: OK, min_departure_time_raw, min_departure_time, is_last_train_gone, bottleneck_segment_index }
```

### 주의할 버그 포인트 (과거 v0.1에서 발생했던 것, 재발 방지)
1. **병목 인덱스 재대입 금지**: `implied_by_next` 채택 시 `bottleneck_index = i+1`로 덮어쓰면 안 됨 — i+1이 항상 실제 병목이라는 보장 없음. 갱신하지 않고 유지.
2. **도보 구간 소요시간 유실 방지**: WALK는 즉시 차감이 아니라 `pending_walk_sec` 누적 → 다음 교통 구간에서 일괄 반영. 경로가 도보로 끝나는 경우 순회 종료 후 남은 값을 최종 반영해야 함.
3. **NO_SERVICE ≠ FETCH_ERROR**: 막차 데이터상 운행 없음(정상 도메인 결과) vs 외부 API/캐시 조회 실패(시스템 오류)는 반드시 구분.
4. **Tie 처리**: `implied_by_next < candidate` (등호 미포함) → 동률이면 구간 자체 막차(a)가 병목으로 채택됨.

### 검증 예시 (문서 6절)
| 구간 | 종류 | 소요 | 막차 | 추정치 |
|---|---|---|---|---|
| 0 | 도보(집→A역) | 5분 | - | - |
| 1 | 지하철(A→B역) | 15분 | 23:20 | 아니오 |
| 2 | 도보(환승) | 3분 | - | - |
| 3 | 버스(B역→C정류장) | 20분 | 23:50(국토부 추정) | 예(버퍼5분) |
| 4 | 도보(C→목적지) | 4분 | - | - |

결과: `min_departure_time_raw` = 23:15, `min_departure_time` = 23:10 (버퍼 5분 차감), `bottleneck_segment_index` = 1(지하철 구간).

## 6. API 명세 (API 명세서 v0.11)

Base URL: `https://api.lastsafe.app/v1`
인증: `Authorization: Bearer {access_token}` (로그인 API 제외 전 요청 필수)

공통 응답:
```json
// 성공
{ "success": true, "data": { ... } }
// 실패
{ "success": false, "error": { "code": "...", "message": "..." } }
```

공통 에러 코드: `401 UNAUTHENTICATED` · `403 FORBIDDEN` · `404 NOT_FOUND` · `409 INVALID_STATE` · `410 CANDIDATE_EXPIRED` · `422 VALIDATION_ERROR` · `503 EXTERNAL_API_UNAVAILABLE`

### 3.1 `POST /auth/login` — 소셜 로그인 (FR-14)
요청: `{ "provider": "APPLE"|"KAKAO", "provider_token": "string" }`
응답: `{ access_token, user_id, is_new_user }`
비고: 비밀번호 미저장. push_token 필드 없음(로컬 알림 전환으로 v0.5에서 제거).

### 4.1 `POST /routes/search` — 경로 후보 검색 (FR-01~02c, FR-03~08b)
요청: `{ "origin": {lat,lng}, "destination": {lat,lng} }`
`departure_datetime`은 사용자 입력 없이 서버가 요청 수신 시각 사용.

응답(200, 정상):
```json
{
  "success": true,
  "data": {
    "status": "OK",
    "candidates": [{
      "candidate_id": "uuid",
      "total_duration_sec": 2580,
      "total_cost": 1500,
      "min_departure_time": "...",
      "min_departure_time_raw": "...",
      "is_last_train_gone": false,
      "bottleneck_segment_index": 1,
      "destination": {lat,lng},
      "segments": [
        { "seq": 0, "type": "WALK", "duration_sec": 300 },
        { "seq": 1, "type": "SUBWAY", "route_name": "2호선", "duration_sec": 900,
          "last_departure_time": "...", "is_estimated": false },
        ...
      ]
    }]
  }
}
```
응답(200, 막차 전멸): `{ status: "NO_ROUTE_AVAILABLE", candidates: [], bottleneck_segment_index }`
응답(503): `{ error: { code: "EXTERNAL_API_UNAVAILABLE" } }`

**candidate_id 캐싱 규칙**: 응답 전체 계산 결과는 서버가 Redis에 `candidate_id` 키로 캐싱(TTL 10분). 알람 등록 시 클라이언트는 `candidate_id`만 전달, 재계산값 재전송 안 함. TTL 만료 시 `410 CANDIDATE_EXPIRED`.

**상태값 매핑** (알고리즘 → API):
| 알고리즘 status | API 응답 |
|---|---|
| OK | 200, `status: "OK"` |
| NO_SERVICE | 200, `status: "NO_ROUTE_AVAILABLE"` |
| FETCH_ERROR | 503, `EXTERNAL_API_UNAVAILABLE` |

### 5.1 `POST /alarms` — 알람 등록 (FR-09, FR-10)
요청: `{ "candidate_id": "uuid", "minutes_before": 10 }`
응답(201): `{ alarm_id, alarm_time, status: "PENDING" }`
응답(410): `CANDIDATE_EXPIRED`
- 이 시점에 캐시의 candidate 원본을 `ROUTE_CANDIDATE` + `SEGMENT`로 DB 영구 저장 (ERD 참조).
- `ROUTE_CANDIDATE`·`SEGMENT`·`DEPARTURE_ALARM` 저장은 하나의 `@Transactional` 경계로 원자적 처리.
- SEGMENT는 개별 INSERT 대신 `saveAll` 벌크 저장.
- 클라이언트는 응답의 `alarm_time`으로 즉시 iOS 로컬 알림 예약 (FR-11).

### 5.2 `GET /alarms?status=&limit=&offset=` — 목록 조회 (FR-12)
`status`: PENDING | COMPLETED | CANCELLED (미지정 시 전체). `limit` 기본20/최대100, `offset` 기본0.

### 5.3 `PATCH /alarms/{id}` — 수정 (FR-12)
요청: `{ "minutes_before": 15 }` — 기존 값 덮어씀.
PENDING 상태만 수정 가능, 아니면 `409 INVALID_STATE`. 타인 리소스 → `403 FORBIDDEN`.
응답 수신 후 클라이언트가 로컬 알림 재예약 (FR-11a).

### 5.4 `DELETE /alarms/{id}` — 취소 (FR-12)
상태를 CANCELLED로 변경(실제 삭제 아님). PENDING만 가능, 아니면 409. 타인 리소스 403.
클라이언트가 로컬 알림 취소 (FR-11a).

### 6. 즐겨찾기 (FR-01b) — 수동 등록·관리 (자동 빈도 집계 아님)
`POST /favorites` `{ label, latitude, longitude }` · `GET /favorites` · `DELETE /favorites/{id}` (타인 리소스 403)

### 7.1 `DELETE /users/me` — 회원 탈퇴 (FR-15a)
즉시 soft delete(`USER.deleted_at`) + access_token 전체 무효화. 실제 개인정보 삭제는 배치 처리 (보존기간 정책 미확정).

## 7. ERD 핵심 (last_safe_erd_current.html 기준)

```
USER ||--o{ FAVORITE_DESTINATION
USER ||--o{ DEPARTURE_ALARM
DEPARTURE_ALARM ||--|| ROUTE_CANDIDATE
ROUTE_CANDIDATE ||--o{ SEGMENT
BUS_ARRIVAL_LOG }o--|| BUS_LAST_TIME_STAT   # Phase 2 통계용
```

- `USER`: id, oauth_provider, oauth_id, nickname, created_at, deleted_at
- `DEPARTURE_ALARM`: id, user_id FK, route_candidate_id FK, origin/dest lat·lng, alarm_time, minutes_before, status
- `ROUTE_CANDIDATE`: id, total_duration_sec, total_cost, min_departure_time, min_departure_time_raw
- `SEGMENT`: id, route_candidate_id FK, seq_order, transport_type, route_name, last_time_raw, is_estimated, buffer_sec
- `BUS_ARRIVAL_LOG` / `BUS_LAST_TIME_STAT`: Phase 2 버스 막차 통계 고도화용 (원시 로그 → 일 1회 배치 집계)

## 8. 화면 구조 (화면명세서 v0.1)

전체 화면 9개: 로그인 → 경로 검색(홈) → 목적지 검색 / 경로 후보 목록 → 경로 상세 → 알람 목록 → 알람 상세/수정 · 즐겨찾기 관리 · 설정

모달 4개: 알람 등록(바텀시트) · 알림 권한 안내 · 막차 전멸 안내(NO_ROUTE_AVAILABLE 응답 시) · 회원 탈퇴 확인

핵심 화면 전환:
- 경로 검색 → `POST /routes/search` → 결과가 NO_ROUTE_AVAILABLE이면 막차 전멸 모달, 아니면 경로 후보 목록
- 경로 상세: 길찾기 버튼(외부 지도앱 딥링크, 화면 전환 없음) / 알람 등록 버튼(모달)
- 알람 등록 시 iOS 알림 권한 미허용이면 권한 안내 모달 우선 표시 (FR-09a)

## 9. Phase 2 (MVP 이후)

`BUS_ARRIVAL_LOG` 폴링 데이터를 `BUS_LAST_TIME_STAT`으로 집계하는 통계 기반 버스 막차 개선. 서버가 막차 시간대에 실시간 도착정보를 주기적 폴링 → 일 1회 배치 집계 → 통계 신뢰 임계치(관측 횟수) 미만이면 1단계 계산식 기반 추정치로 폴백.

## 10. 미해결/향후 검토 (SRS 5.3, 5.4)

- 경로 조회 이력 보존 기간, 버스 도착 로그 보존 기간, 통계 신뢰 임계치 — 값 미확정
- 서울시 버스 막차 API 실제 가용 여부 (문서상 "서비스 종료 안내" 표시됨) — API 키 발급 후 직접 검증 필요
- iOS Critical Alert, 로컬 알림 소실 대비 서버 백업 로직 — MVP 범위 외
- 앱 내 자체 길찾기(지도 SDK 렌더링) — MVP 범위 외, 딥링크로 대체 중
