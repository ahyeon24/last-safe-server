**막차 세이프 (Last Safe)**

API 명세서

API Specification v0.11

작성일: 2026-08-20

작성자: 아현

관련 문서: 요구사항명세서 v0.15, 막차계산알고리즘설계서 v0.5

# 목차

1. 개요

2. 공통 사항

3. 인증 API

4. 경로 조회 API

5. 알람 API

6. 즐겨찾기 목적지 API

7. 회원 API

8. 요구사항 추적표

# 1. 개요

본 문서는 막차 세이프(Last Safe) last-safe-server가 last-safe-ios 클라이언트에 제공하는 REST API를 정의한다. 요구사항명세서(SRS) v0.15와 막차계산알고리즘설계서 v0.5를 기준으로 작성되었다.

경로 조회는 사용자가 여러 경로 후보를 비교한 뒤 하나를 선택하는 흐름(FR-02a)을 지원해야 하므로, 서버는 ODsay에서 받은 모든 후보에 대해 막차계산알고리즘설계서의 계산을 미리 수행하여 한 번의 응답에 포함한다 (FR-02b에 따른 설계 결정).

v0.4는 자체 비판 검토를 반영한 개정판이다. 주요 변경 사항은 8.1절 참조. v0.5는 알람 발송 방식이 서버 원격 푸시(APNs)에서 클라이언트 iOS 로컬 알림으로 전환됨(SRS v0.12)에 따른 개정판이며, 주요 변경 사항은 8.2절 참조. v0.6은 경로 탐색·막차 조회를 Tmap+ODsay 이원화에서 ODsay 단일 API로 통합함(SRS v0.13)에 따른 개정판이며, 주요 변경 사항은 8.3절 참조.

# 2. 공통 사항

## 2.1 Base URL

| https://api.lastsafe.app/v1 |
| --- |

## 2.2 인증

로그인 API를 제외한 모든 요청은 Authorization 헤더에 Bearer 토큰을 포함해야 한다 (FR-14, NFR-05).

| Authorization: Bearer {access_token} |
| --- |

## 2.3 공통 응답 포맷

성공 응답:

| {   "success": true,   "data": { ... } } |
| --- |

실패 응답:

| {   "success": false,   "error": {     "code": "ROUTE_CALCULATION_FAILED",     "message": "경로 계산 중 오류가 발생했습니다."   } } |
| --- |

## 2.4 공통 에러 코드

| **HTTP 상태** | **에러 코드** | **의미** |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 토큰 없음 또는 만료 |
| 403 | FORBIDDEN | 본인 소유 리소스가 아닌 요청 (예: 다른 사용자의 알람/즐겨찾기 수정·삭제 시도) |
| 404 | NOT_FOUND | 리소스 없음 |
| 409 | INVALID_STATE | 리소스 상태상 허용되지 않는 요청 (예: 이미 완료·취소된 알람의 수정 시도) |
| 410 | CANDIDATE_EXPIRED | 경로 후보 캐시가 만료되어 알람 등록에 사용할 수 없음 (4.1절 참조) |
| 422 | VALIDATION_ERROR | 요청 값 검증 실패 |
| 503 | EXTERNAL_API_UNAVAILABLE | 외부 API(ODsay 등) 장애 (NFR-04) |

# 3. 인증 API

## 3.1 소셜 로그인

FR-14. OAuth2 기반 소셜 로그인 후 서버 자체 액세스 토큰을 발급한다.

| **POST** | /auth/login |
| --- | --- |

요청 바디:

| {   "provider": "APPLE",       // APPLE │ KAKAO   "provider_token": "string" // OAuth2 제공자로부터 받은 토큰 } |
| --- |

응답 (200):

| {   "success": true,   "data": {     "access_token": "string",     "user_id": 1001,     "is_new_user": false   } } |
| --- |

비고: 비밀번호는 서버에 저장하지 않는다 (NFR-05). provider_token 검증은 각 OAuth2 제공자의 토큰 검증 API를 통해 서버 측에서 수행한다. 알람 발송은 서버가 아닌 클라이언트의 iOS 로컬 알림으로 처리되므로(FR-11), 본 API는 디바이스 푸시 토큰을 다루지 않는다.

# 4. 경로 조회 API

## 4.1 경로 후보 검색

FR-01~FR-02c, FR-03~FR-08b. 출발지·목적지를 입력받아, ODsay로 경로 후보를 조회하고 각 후보에 막차계산알고리즘설계서 v0.5의 계산을 적용하여 반환한다. 막차 계산 기준 시각(departure_datetime)은 사용자가 입력하지 않으며, 서버가 이 요청을 수신한 시각을 그대로 사용한다 (SRS v0.10: 출발 예정 시각 지정 기능은 범위에서 제외됨).

| **POST** | /routes/search |
| --- | --- |

요청 바디:

| {   "origin": { "lat": 37.5012, "lng": 127.0396 },   // FR-01a/c   "destination": { "lat": 37.4979, "lng": 127.0276 } } |
| --- |

응답 (200) — 정상 (경로 존재):

| {   "success": true,   "data": {     "status": "OK",     "candidates": [       {         "candidate_id": "a1b2c3d4-...",  // 서버 캐시 참조 키 (아래 비고 참조)         "total_duration_sec": 2580,         "total_cost": 1500,         "min_departure_time": "2026-08-20T23:10:00+09:00",     // FR-05a 버퍼 적용         "min_departure_time_raw": "2026-08-20T23:15:00+09:00", // FR-08b 판정 기준         "is_last_train_gone": false,         "bottleneck_segment_index": 1,         "destination": { "lat": 37.4979, "lng": 127.0276 },  // FR-02d 딥링크용, 요청 destination과 동일값 반영         "segments": [           { "seq": 0, "type": "WALK", "duration_sec": 300 },           {             "seq": 1, "type": "SUBWAY", "route_name": "2호선",             "duration_sec": 900, "last_departure_time": "2026-08-20T23:20:00+09:00",             "is_estimated": false           },           { "seq": 2, "type": "WALK", "duration_sec": 180 },           {             "seq": 3, "type": "BUS", "route_name": "146번",             "duration_sec": 1200, "last_departure_time": "2026-08-20T23:50:00+09:00",             "is_estimated": true, "buffer_sec": 300           }         ]       }       // 이하 추가 경로 후보 (FR-02a)     ]   } } |
| --- |

응답 (200) — 막차 전멸 (FR-08a):

| {   "success": true,   "data": {     "status": "NO_ROUTE_AVAILABLE",     "candidates": [],     "bottleneck_segment_index": 1   // 어느 구간에서 막차가 종료됐는지   } } |
| --- |

응답 (503) — 외부 API 실패:

| {   "success": false,   "error": {     "code": "EXTERNAL_API_UNAVAILABLE",     "message": "일부 경로의 막차 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."   } } |
| --- |

시퀀스 다이어그램 — 경로 후보 검색 흐름 (클라이언트-서버-Redis-ODsay):

비고 — candidate_id와 서버 측 캐시: candidate_id는 클라이언트에 노출되는 값일 뿐, 응답에 포함된 계산 결과(막차 시각, 버퍼, 병목 구간 등) 전체는 서버가 Redis에 candidate_id를 키로 하여 별도로 캐싱해 둔다 (TTL 10분). 사용자가 후보를 선택해 알람을 등록할 때(5.1절)는 클라이언트가 이 계산값을 다시 전송하지 않고 candidate_id만 전달하며, 서버는 캐시에서 원본을 조회하여 저장한다. 이는 클라이언트가 전송한 계산값을 서버가 검증 없이 신뢰하는 것을 방지하기 위함이다. 캐시가 TTL 만료로 사라진 경우 5.1절의 CANDIDATE_EXPIRED 에러가 발생하며, 클라이언트는 경로를 다시 검색해야 한다.

비고 — 길찾기 딥링크(FR-02d): 각 candidate에는 destination 좌표가 포함되며, 사용자가 클라이언트에서 "길찾기" 버튼을 누르면 이 좌표(및 요청에 사용한 origin)를 외부 지도 앱(카카오맵/네이버지도/Tmap 등)의 딥링크 URL 스킴에 담아 전달한다. 서버는 딥링크 URL 자체를 생성하지 않으며, URL 스킴 구성은 클라이언트 책임이다. 외부 앱이 실제로 안내하는 경로는 본 API가 계산한 segments(노선 조합)와 다를 수 있으며, 이는 설계상 허용된 차이다(앱 내 자체 경로 렌더링은 범위 외, SRS 5.4절 참조).

비고 — 두 캐시의 TTL이 다른 이유: 서버는 성격이 다른 두 캐시를 구분해서 관리한다. (1) 구간별 막차 시각 캐시는 같은 노선·같은 요일 유형이면 하루 동안 값이 바뀌지 않으므로, candidate 캐시보다 훨씬 긴 TTL(예: 조회 당일 자정 만료)을 사용해 동일 구간에 대한 반복 조회를 줄인다. 다만 ODsay 데이터가 월 2회 갱신되므로(SRS 5.1절) 무기한 캐싱은 하지 않는다. (2) candidate(경로 후보) 계산 결과 캐시는 위 비고에서 설명한 대로 TTL 10분을 유지한다 — 이는 데이터가 자주 바뀌어서가 아니라, 사용자가 후보를 검토하고 알람을 등록하기까지 걸리는 합리적 시간을 기준으로 정한 값이며, 캐시를 무기한 유지하면 재조회되지 않는 candidate가 Redis에 계속 쌓여 메모리를 낭비하게 된다.

비고 — 알고리즘 설계서와의 상태값 매핑: 아래 표는 막차계산알고리즘설계서 v0.5의 반환 status를 본 API의 응답으로 매핑한 것이다.

| **알고리즘 설계서 status** | **API 응답** |
| --- | --- |
| OK | 200, data.status = "OK" |
| NO_SERVICE (경로 전체 중 하나 이상의 구간) | 200, data.status = "NO_ROUTE_AVAILABLE" |
| FETCH_ERROR | 503, error.code = "EXTERNAL_API_UNAVAILABLE" |

# 5. 알람 API

## 5.1 알람 등록

FR-09, FR-10. 사용자가 선택한 경로 후보를 기준으로 출발 알람을 등록한다. 이 시점에 4.1절 비고에서 설명한 서버 캐시의 candidate 원본이 ROUTE_CANDIDATE와 SEGMENT로 DB에 저장된다 (ERD 참조). 클라이언트는 응답의 alarm_time을 받아 즉시 iOS 로컬 알림으로 예약해야 한다 (FR-11).

| **POST** | /alarms |
| --- | --- |

요청 바디:

| {   "candidate_id": "a1b2c3d4-...",  // 4.1절 응답에서 선택한 후보의 id   "minutes_before": 10   // FR-10 } |
| --- |

응답 (201):

| {   "success": true,   "data": {     "alarm_id": 5001,     "alarm_time": "2026-08-20T23:00:00+09:00",     "status": "PENDING"   // FR-13 상태값 중 하나   } } |
| --- |

응답 (410) — 캐시 만료:

| {   "success": false,   "error": {     "code": "CANDIDATE_EXPIRED",     "message": "선택한 경로 정보가 만료되었습니다. 경로를 다시 검색해주세요."   } } |
| --- |

시퀀스 다이어그램 — 알람 등록 흐름 (클라이언트-서버-Redis-MySQL):

## 5.2 알람 목록 조회

FR-12. 기본적으로 상태와 무관하게 최근 항목을 반환하며, status 쿼리 파라미터로 필터링할 수 있다.

| **GET** | /alarms?status=PENDING&limit=20&offset=0 |
| --- | --- |

쿼리 파라미터:

| **파라미터** | **설명** |
| --- | --- |
| status | 선택. PENDING │ COMPLETED │ CANCELLED. 미지정 시 전체 상태 반환 |
| limit | 선택. 기본값 20, 최대 100 |
| offset | 선택. 기본값 0 |

응답 (200):

| {   "success": true,   "data": {     "alarms": [       {         "alarm_id": 5001,         "alarm_time": "2026-08-20T23:00:00+09:00",         "status": "PENDING",   // PENDING │ COMPLETED │ CANCELLED (FR-13)         "destination_label": "집"       }     ],     "total_count": 1   } } |
| --- |

## 5.3 알람 수정

FR-12. 발송 시점(minutes_before)만 수정 가능. 기존 값을 덮어쓴다 (알고리즘설계서 v0.2 확정 사항). status가 PENDING인 알람만 수정 가능하다. 응답 수신 후 클라이언트는 기존에 예약된 iOS 로컬 알림을 갱신된 시각으로 재예약해야 한다 (FR-11a).

| **PATCH** | /alarms/{alarm_id} |
| --- | --- |
| {   "minutes_before": 15 } |

응답 (409) — PENDING 상태가 아닌 경우:

| {   "success": false,   "error": {     "code": "INVALID_STATE",     "message": "이미 완료되었거나 취소된 알람은 수정할 수 없습니다."   } } |
| --- |

본인 소유가 아닌 alarm_id로 요청 시 403 FORBIDDEN을 반환한다 (2.4절).

## 5.4 알람 취소

FR-12. 상태를 CANCELLED로 변경한다 (실제 행 삭제 아님, FR-13 상태 관리). status가 PENDING인 알람만 취소 가능하며, 그 외 상태에서는 5.3절과 동일한 409 INVALID_STATE를 반환한다. 본인 소유가 아닌 alarm_id로 요청 시 403 FORBIDDEN을 반환한다 (2.4절). 응답 수신 후 클라이언트는 기존에 예약된 iOS 로컬 알림을 취소해야 한다 (FR-11a).

| **DELETE** | /alarms/{alarm_id} |
| --- | --- |

응답 (200): 빈 data와 함께 success: true 반환.

# 6. 즐겨찾기 목적지 API

FR-01b. 수동으로 등록·관리한다 (자동 빈도 집계 아님, 설계 확정 사항).

## 6.1 즐겨찾기 등록

| **POST** | /favorites |
| --- | --- |
| {   "label": "집",   "latitude": 37.4979,   "longitude": 127.0276 } |

## 6.2 즐겨찾기 목록 조회

| **GET** | /favorites |
| --- | --- |

## 6.3 즐겨찾기 삭제

본인 소유가 아닌 favorite_id로 요청 시 403 FORBIDDEN을 반환한다 (2.4절).

| **DELETE** | /favorites/{favorite_id} |
| --- | --- |

# 7. 회원 API

## 7.1 회원 탈퇴

FR-15a. 관련 법령 및 정책에 따라 정해진 기간 내 개인정보(위치 기록, 즐겨찾기, 알람 이력)를 삭제한다. 구체적 보존/삭제 정책은 서비스 배포 전 재검토 필요 (SRS 5.1절).

| **DELETE** | /users/me |
| --- | --- |

응답 (200): 즉시 계정 비활성화(soft delete, USER.deleted_at 설정) 후 실제 개인정보 삭제는 배치로 처리. 요청 시점에 발급되어 있던 모든 access_token은 즉시 무효화되며, 이후 해당 토큰으로의 요청은 401 UNAUTHENTICATED를 반환한다.

# 8. 요구사항 추적표

| **FR ID** | **엔드포인트** |
| --- | --- |
| FR-01, 01a~01c, 02, 02a~02c, 03~08b | POST /routes/search |
| FR-02d | 클라이언트 내부 로직 (POST /routes/search 응답의 destination 좌표를 사용해 외부 지도 앱 딥링크 실행, 별도 API 호출 없음) |
| FR-09, 10 | POST /alarms |
| FR-12 (조회) | GET /alarms |
| FR-12 (수정) | PATCH /alarms/{id} |
| FR-12 (취소) | DELETE /alarms/{id} |
| FR-13 | alarm.status 필드 (모든 알람 API 응답에 포함) |
| FR-01b | POST, GET, DELETE /favorites |
| FR-14 | POST /auth/login |
| FR-09a | 클라이언트 내부 로직 (iOS 알림 권한 확인, API 호출 없음) |
| FR-15a | DELETE /users/me |

FR-11(로컬 알림 예약), FR-11a(로컬 알림 갱신·취소)는 클라이언트 내부 동작으로, POST/PATCH/DELETE /alarms 응답을 받은 직후 클라이언트가 수행해야 하는 후속 처리다. 서버 API 자체로 존재하지 않으므로 위 표의 각 알람 엔드포인트 설명에 병기하였다 (5.1, 5.3, 5.4절).

## 8.1 v0.4에서 수정된 사항

- 경로 후보의 계산 결과(막차 시각, 버퍼, 병목 구간 등)를 클라이언트가 재전송하여 그대로 저장하던 방식을 폐기. 서버가 Redis에 candidate_id로 캐싱(TTL 10분)하고, 알람 등록 시 candidate_id만 전달받아 서버가 원본을 조회하는 방식으로 변경 (4.1절, 5.1절) — 클라이언트 제공 계산값을 무검증 신뢰하던 문제 해결

- 이에 따라 POST /alarms 요청 바디에서 origin/destination/route_candidate 중복 제거

- PATCH, DELETE /alarms/{id}는 status가 PENDING인 경우만 허용하도록 명시. 위반 시 409 INVALID_STATE 응답 추가 (5.3, 5.4절)

- GET /alarms에 status 필터와 페이지네이션(limit/offset) 추가 (5.2절)

- 본인 소유가 아닌 리소스에 대한 403 FORBIDDEN 발생 조건을 각 엔드포인트에 명시 (5.3, 5.4, 6.3절)

- 회원 탈퇴 시 access_token 즉시 무효화 명시 (7.1절)

- 경로 후보 검색 API에 외부 API 실패(503) 응답 예시 추가, 알고리즘설계서 v0.4의 상태값(OK/NO_SERVICE/FETCH_ERROR)과 본 API 응답의 매핑표 추가 (4.1절)

- NO_ROUTE_AVAILABLE 응답에 bottleneck_segment_index 추가

## 8.2 v0.5에서 수정된 사항

- SRS v0.12 반영: 알람 발송 방식을 서버 원격 푸시(APNs)에서 클라이언트 iOS 로컬 알림으로 전환

- POST /auth/login에서 push_token 필드 제거. PATCH /users/me/push-token 엔드포인트 삭제 (3.1절)

- 알람 상태값을 PENDING | SENT | FAILED | CANCELLED에서 PENDING | COMPLETED | CANCELLED로 단순화 (5.2절)

- 알람 등록·수정·취소 API 응답 수신 후 클라이언트가 iOS 로컬 알림을 예약·갱신·취소해야 함을 각 절에 명시 (5.1, 5.3, 5.4절)

## 8.3 v0.6에서 수정된 사항

- SRS v0.13 반영: 경로 탐색·막차 조회를 Tmap+ODsay 이원화에서 ODsay 단일 API로 통합

- 4.1절 경로 후보 검색 API 설명에서 Tmap → ODsay로 정정

- 2.4절, 4.1절의 외부 API 장애 관련 서술에서 Tmap 제거

## 8.4 v0.7에서 수정된 사항 (SRS 종합 검토 반영)

- 1절 개요의 개정 히스토리 서술 순서를 버전 순(v0.4→v0.5→v0.6)으로 재정렬 (기존에는 v0.6 서술이 v0.4/v0.5보다 앞에 위치하는 오류가 있었음)

- 표지 및 1절의 SRS·알고리즘설계서 버전 참조를 v0.13/v0.4 → v0.14/v0.5로 갱신 (문서 간 참조 누락 수정)

- 2.4절 INVALID_STATE 설명 예시 문구에서 "발송·취소" → "완료·취소"로 정정 (FR-13 상태값 명칭과 일치, v0.12 반영 누락 수정)

- 4.1절 두 곳의 알고리즘설계서 버전 참조를 v0.4 → v0.5로 갱신

## 8.5 v0.8에서 수정된 사항 (SRS v0.15 반영)

- SRS v0.15 반영: FR-02d(외부 지도 앱 딥링크를 통한 길찾기) 신설에 따라, 경로 후보 검색 API의 candidate 응답에 destination 좌표 필드 추가 (4.1절)

- 4.1절에 길찾기 딥링크 관련 비고 신설: 딥링크 URL 생성은 클라이언트 책임이며, 외부 지도 앱이 안내하는 경로는 본 API의 계산 결과(segments)와 다를 수 있음을 명시

- 8절 요구사항 추적표에 FR-02d 행 추가 (클라이언트 내부 로직, 별도 서버 엔드포인트 없음)

- 표지 및 1절의 SRS 버전 참조를 v0.14 → v0.15로 갱신

## 8.6 v0.9에서 수정된 사항

- 4.1절에 경로 후보 검색 흐름 시퀀스 다이어그램 추가 (클라이언트-서버-Redis-ODsay, 캐시 히트/미스 분기 및 OK/NO_ROUTE_AVAILABLE/EXTERNAL_API_UNAVAILABLE 3가지 상태 분기 포함)

- 5.1절에 알람 등록 흐름 시퀀스 다이어그램 추가 (candidate_id 캐시 조회, 캐시 히트/만료(CANDIDATE_EXPIRED) 분기 포함)

## 8.7 v0.10에서 수정된 사항

- 4.1절 경로 후보 검색 시퀀스 다이어그램 개선: 구간별 막차 시각 캐시 조회를 개별 GET 반복(loop) 대신 Redis MGET으로 일괄 조회하도록 변경, 캐시 미스 구간은 순차 호출 대신 병렬(par) 호출하도록 변경하여 응답 지연 축소

- 4.1절에 구간별 막차 시각 캐시와 candidate 계산결과 캐시의 TTL을 구분하는 비고 신설. 막차 시각 캐시는 당일 자정 만료, candidate 캐시는 기존과 동일하게 TTL 10분 유지

## 8.8 v0.11에서 수정된 사항

- 5.1절 알람 등록 시퀀스 다이어그램 개선: ROUTE_CANDIDATE·SEGMENT·DEPARTURE_ALARM 저장을 하나의 트랜잭션(@Transactional) 경계로 명시하여 중간 실패 시 원자적 롤백이 되도록 표현

- SEGMENT 저장을 구간별 개별 INSERT 대신 벌크 저장(saveAll)으로 처리하도록 명시하여 다중 구간 경로의 저장 지연 축소