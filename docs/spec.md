# 막차 세이프 (Last Safe) — 스펙 요약

> 이 문서는 Claude Code가 코딩 중 빠르게 참조하는 핵심 요약입니다.
> 전체 근거·개정 이력은 `docs/source/`의 원본 .docx를 참조하세요.
>
> 기준 버전: SRS v0.15 · API 명세서 v0.11 · 알고리즘 설계서 v0.5 · 화면명세서 v0.1

## 1. 프로젝트 개요

- 사용자가 출발지·목적지를 입력하면 막차를 놓치지 않기 위한 **최소 출발 시각**을 계산해 안내하고, **iOS 로컬 알림**으로 알려주는 서비스.
- 목표: (1) 실제 앱스토어 출시, (2) 백엔드 개발자 취업 포트폴리오 — 단순 API 프록시가 아닌 실질 도메인 로직(막차 역산, 알람 스케줄링, 인증, 외부 API 통합) 구현.
- 초기 범위: **[2026-09-07 확정] 서울·경기도 한정 (MVP)** — "수도권"이라는 모호한 표현 대신 명확히 확정. 인천은 MVP 범위 밖(추후 확장 검토 대상). 지하철은 서울/경기 노선 위주, 버스는 서울(3절 서울시 API)·경기(TAGO 도시코드 31xxx, 5.2·5.3절) 위주로 데이터 확보 범위를 좁힘.

## 2. 기술 스택 & 레포 구조

| 구성요소 | 스택 | 레포 |
|---|---|---|
| last-safe-server | Java, Spring Boot, MySQL, Redis, Gradle | `ahyeon24/last-safe-server` |
| last-safe-ios | Swift, SwiftUI | `ahyeon24/last-safe-ios` |

로컬 루트: `~/Developer/last-safe-server`, `~/Developer/last-safe-ios`

## 3. 외부 연동 API

| 연동 대상 | 용도 |
|---|---|
| ODsay (`searchPubTransPathT`) | 대중교통 경로 탐색(SX/SY/EX/EY 좌표 기반). **[2026-09-07 변경] 지하철역 막차 시각 조회는 더 이상 ODsay를 쓰지 않는다** — 아래 TAGO 지하철정보 서비스로 대체(무료 + 요일별 직접 조회 가능, 실제 활용가이드 문서로 검증됨). ODsay는 이제 "경로 자체를 찾는" 용도로만 남음(대체 수단 없음 — pathfinding은 직접 구현하기엔 부담스러워 계속 ODsay 사용). |
| 국토교통부(TAGO) 지하철정보 서비스(`GetSubwaySttnAcctoSchdulList`, data.go.kr) | **[2026-09-07 최종 채택 — ODsay → 서울교통공사 API → 이걸로 두 번 더 갈아탐]** 지하철역별 시간표 조회, 무료, **요일구분코드(`dailyTypeCode`: 01=평일/02=토요일/03=일요일·공휴일) + 상하행구분코드(`upDownTypeCode`: U/D) 직접 지원** — 서울시 버스 API와 달리 배치 관측 우회 불필요. **오픈API활용가이드_국토교통부(TAGO)_지하철정보v1.1 문서로 파라미터·응답 필드 확인함**(요청: `serviceKey`/`subwayStationId`/`dailyTypeCode`/`upDownTypeCode`/`numOfRows`/`pageNo`, 응답: `depTime`/`arrTime`(`HHMISS` 6자리)/`dailyTypeCode`/`upDownTypeCode`/`subwayStationId`/`subwayRouteId`, 서비스 URL `http://apis.data.go.kr/1613000/SubwayInfo`) — 서울교통공사 API 때와 달리 UI 라벨 추측이 아니라 실제 가이드 문서 기반이라 근거가 훨씬 탄탄함. ⚠️ 그래도 **실제 키로 호출해서 동작을 확인한 적은 아직 없음** — 문서와 실제 응답이 다를 가능성은 항상 있음, 키 받으면 재확인할 것. `역명 → subwayStationId` 변환은 같은 서비스의 `GetKwrdFndSubwaySttnList`(키워드기반 지하철역 목록 조회)로 한다. 국토교통부 산하 서비스라 서울교통공사 자체 API보다 **커버리지가 넓을 가능성이 있음**(신분당선 등 타 사업자 포함 여부는 실제 호출 전까지 미확정 — 10절 미해결). |
| 서울특별시_정류소정보조회 서비스(`getBustimeByStationList`, data.go.kr) | 서울 지역 버스 막차 (정류장 기준 직접 제공, "첫차·막차 예정 시간") — **2026-09-06 실제 호출 검증 완료(활용가이드 20250924 대조). 요청 파라미터: `arsId`(정류소번호), `busRouteId`(노선ID). 응답: `firstBusTm`/`lastBusTm`(`HHMMSS` 6자리, 초 단위 포함 — TAGO의 `HHmm` 4자리와 형식 다름 주의). 필드 설명이 "금일첫차시간"/"금일막차시간"으로 명시돼있어 **요일 지정 파라미터가 없고 항상 "오늘" 기준 값만 반환** — 평일/토요일/일요일 구분 조회 불가. 데이터 갱신주기는 매일 새벽 5시. 이 때문에 요일별 데이터는 매일 배치 동기화로 직접 관측·누적해야 함(아래 4절 배치 전략 참고).** |
| 국토교통부(TAGO) 버스노선정보 서비스 API | 서울 외 지역 버스 막차 **1단계 추정 계산**(5.2절, 기점 막차 + 경유 정류소 → 정류장 간 평균 소요시간 누적) — **2026-09-05 재확인: TAGO 노선정보 API는 정류소별이 아닌 노선 전체 기준 첫차/막차(`startvehicletime`/`endvehicletime`)만 제공, TAGO 버스도착정보 API에도 막차 여부 필드 없음(2026-09-06 공식 활용가이드 v1.0 대조로 재확인 — 응답은 `arrtime`(초 단위 도착예상), `arrprevstationcnt`뿐이고 실시간 위치추적용, 갱신주기 10~20초) → 1단계는 추정 계산 방식이 맞음. 정확도 개선은 아래 두 API 기반 5.3절(Phase 2) 참고.** |
| 국토교통부(TAGO) 버스위치정보(`getRouteAcctoBusLcList`) | 노선의 현재 운행 차량 위치(`vehicleno`, 현재 정류소 `nodeid`/`nodeord`) 전국 단위 실시간 조회 — 5.3절 심야 운행 기록 수집(Phase 2 기반)의 핵심 데이터 소스. GBIS(경기도 전용) 대신 이걸 사용해 전국 단일 인터페이스로 통합. |
| 국토교통부(TAGO) 버스정류소정보(`버스노선정보`와 별개 데이터셋) | `좌표기반근접정류소 목록조회`(반경 500m)로 서울 외 지역의 "사용자 좌표 → 근처 정류소ID" 변환 해결(10절 참고). 실시간 REST API(정적 다운로드 파일 아님) 확인됨. |
| 한국천문연구원_특일 정보(`SpcdeInfoService`, `getHolidayInfo`) | **[2026-09-07 신규 발견 — 이전까지 완전히 누락돼있던 항목]** `DayType.SUNDAY_OR_HOLIDAY` 판별에 필수. 이거 없이는 공휴일이 평일에 걸릴 때(예: 설날이 화요일) `DayType`을 WEEKDAY로 잘못 계산하게 됨. 파라미터 `solYear`(필수)/`solMonth`(옵션), 응답 `locdate`(날짜)/`isHoliday`(공공기관 휴일 여부 Y/N)/`dateName`. 연 1회(또는 월 1회) 배치로 해당 연도 공휴일 목록을 캐싱해두고, 매일 배치(4절)에서 오늘 날짜의 `DayType` 판별 시 조회하는 방식 권장 — 매 요청마다 외부 호출할 필요 없음. |

- 알람 발송은 서버 푸시(APNs) 아님 — **iOS 로컬 알림(UNUserNotificationCenter)** 전용.
- ODsay는 월 2회 데이터 갱신, Basic 무료 등급 일 1,000회 제한 (실검증 필요).

## 4. 핵심 설계 결정 (변경 시 반드시 확인받을 것)

- **로컬 알림 전용**: APNs, 푸시 토큰, 재시도 로직, ALARM_LOG 불필요.
- **ODsay 실시간 API + Redis TTL 캐싱** 유지. 배치로 자체 DB 구축하는 방식은 ODsay 이용약관 제4.5.10조(사전 동의 없는 결과 데이터 무단 저장·가공·배포 금지) 및 운영정책 섹션2(사전 동의 없는 데이터 분석 목적·대용량 호출 금지) 위반 소지 및 정확도 문제로 기각됨 — 2026-09-06 실제 약관 대조 확인.
- **경로 후보는 `candidate_id` 기반 Redis 캐싱(TTL 10분)**. 클라이언트는 알람 등록 시 계산값을 재전송하지 않고 `candidate_id`만 전달 → 서버가 캐시에서 원본 조회. 클라이언트 제공 계산값을 무검증 신뢰하지 않기 위함.
- **막차 시각 캐시와 candidate 캐시는 TTL이 다름**: 막차 시각 캐시는 당일 자정 만료(긴 TTL), candidate 캐시는 10분(사용자 검토 시간 기준).
- **지도 내비게이션은 스코프 제외**. 카카오맵 등 외부 지도 앱 딥링크(FR-02d)로 대체. URL 스킴 구성은 클라이언트 책임, 서버는 딥링크 URL을 생성하지 않음.
- **출발 시간 사용자 지정 기능 없음**. 서버는 항상 API 요청 수신 시각을 `departure_datetime`으로 사용 (FR-01d 삭제됨, v0.10).
- **경로 조회 이력은 알람 등록된 경로만 DB 저장**, 나머지는 폐기 (보존 기간 정책은 미해결 사항).
- **버퍼(여유시간)는 국토부 추정 기반 버스 구간에만, 구간별로 적용 후 합산**. 지하철·서울시 API 기반 버스에는 미적용.
- **막차 전멸 판정(FR-08a/b)은 버퍼 적용 전 원본 시각(`min_departure_time_raw`) 기준**. 순서를 바꾸면 오판 가능.
- **모든 교통 구간(SUBWAY·BUS)의 candidate는 역산 도중, 앞 구간으로 전파되기 전에 실제(또는 5.2절 추정) 시간표에 스냅된다**(5.1절, 2026-09-07 설계 재검토로 "첫 구간만"에서 확장). 첫 구간만 스냅하면 중간 구간에서 채택된 산술적 값(실제 배차가 아닐 수 있음)이 그대로 전파돼 최종 결과 자체가 틀릴 수 있음이 반례로 확인됨.
- **서울시 버스 막차 API(`getBustimeByStationList`)와 TAGO 버스노선정보는 사용자 요청 시 실시간 호출 대상이 아니라 배치 동기화 대상.** 둘 다 (1) TAGO는 서버 응답이 불안정(타임아웃 빈발 확인, 2026-09-06)하고, (2) 서울시 API는 "금일" 기준 값만 주고 요일 지정이 불가능해 평일/토요일/일요일 3종을 얻으려면 여러 날에 걸쳐 관측해야 함. 따라서 매일 배치 작업(서울시는 새벽 5시 갱신 이후 실행)으로 그날의 값을 우리 DB(`BUS_SEOUL_SCHEDULE_OBSERVATION`: `station_id`, `route_id`, `service_date`, `day_of_week`, `is_holiday`, `departure_time`, `fetched_at`)에 적재하고, 실제 막차 계산 요청 처리 시엔 외부 API를 직접 부르지 않고 이 적재된 데이터만 조회한다. **[2026-09-07 반영] `day_type`(WEEKDAY/SATURDAY/SUNDAY_OR_HOLIDAY)을 저장 시점에 확정해서 넣지 않고, `day_of_week`·`is_holiday` 원본 사실만 저장한 뒤 조회 시점에 파생시킨다** — TAGO 조회 키로는 3종(DayType.java)이면 충분하지만, 우리가 직접 관측하는 이 데이터는 나중에 "공휴일이 정말 일요일과 같은 스케줄인지"를 원본으로 재검증할 수 있어야 하므로 조회 편의를 위해 미리 뭉개면 안 됨. 신규 출시 직후에는 아직 관측 못 한 요일(예: 평일에 출시하면 첫 주말 전까지 SAT/SUN 없음)이 있을 수 있음 — 그 경우의 폴백은 미확정(10절).
- **[2026-09-07 신규] 벤더별 분기는 "조립 계층"(경로 세그먼트를 만들면서 `SubwayScheduleClient`/`BusLastDepartureClient` 등을 호출하는 아직 미착수 계층) 안의 리졸버/팩토리 한 곳에만 두고, `LastTrainCalculationService`나 컨트롤러 등 다른 코드엔 `if type==SUBWAY ... else if seoulBus ... else tago` 식의 분기를 절대 넣지 않는다.** `SubwayScheduleClient.findLatestDepartureAtOrBefore`와 `BusLastDepartureClient.findLatestDepartureAtOrBefore`가 이미 거의 동일한 시그니처(stationId+노선/방향+deadline+dayType)로 맞춰져 있어서, 조립 계층에서 "이 세그먼트 타입·지역엔 어떤 구현체를 쓸지"만 한 군데서 결정하면 나머지 코드는 구현체가 몇 개든 신경 안 써도 됨. 이렇게 해두면 5.2절 추정 방식이 나중에 5.3절 통계 기반으로 바뀌어도 알고리즘·API 코드는 안 건드려도 된다.
- **[2026-09-07 신규] 자정 넘김·서비스 데이 처리 규칙 확정 (막차 서비스 특성상 실제 버그로 터지기 쉬운 부분).**
  1. **막차 시각이 첫차 시각보다 이르면 다음날로 취급**한다 — TAGO 등 API가 `startvehicletime=0530, endvehicletime=0010`처럼 자정 넘긴 막차를 그냥 `HH:mm`으로만 주기 때문에, "오늘 날짜 + HH:mm"으로 그대로 파싱하면 막차가 첫차보다 이른 시각으로 잘못 계산된다. `domain/calendar/OvernightTimeResolver`로 구현·테스트 완료 — 실제 외부 클라이언트를 만들 때 반드시 이걸 거쳐서 `LocalDateTime`을 만들 것, 직접 파싱 금지.
  2. **"지금이 몇 요일 스케줄이냐"는 달력 날짜가 아니라 서비스 데이 기준.** 새벽 04시 이전 검색은 전날 밤 운행의 연장으로 보고, 전날 기준 `DayType`을 적용한다(예: 월요일 새벽 2시 검색 → 일요일 스케줄 적용). `DayTypeResolver.resolveForDateTime`/`resolveServiceDate`로 구현·테스트 완료. **04시라는 컷오프 값은 업계 관행 추정치일 뿐 확정된 근거는 아님**(10절 미해결로 추가) — 실제 사업자별로 다를 수 있음.
  3. 핵심 계산기(`LastTrainCalculationService`) 자체는 `LocalDateTime` 비교 연산만 쓰므로, 입력값의 날짜가 정확히만 매겨지면 자정 넘김을 별도 처리할 필요가 없다(회귀 테스트로 확인) — 정확성은 전적으로 위 1·2번을 거쳐 값을 만드는 조립/외부클라이언트 계층의 책임.

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

### 5.1 교통 구간 실제 배차 스냅 (SUBWAY·BUS, 구현 완료)

**배경**: 역산 알고리즘이 만드는 각 구간의 "필요 도착/탑승 시각"은 산술 계산값일 뿐, 그 시각에 실제 열차·버스가 있다는 보장이 없다. 예: 데드라인이 23:07인데 실제 지하철 배차가 23:05·23:10뿐이면 23:05차를 놓치는 순간 이미 늦은 것인데, 그대로 보여주면 오판을 유발한다.

**[2026-09-07 설계 재검토 후 확정] 첫 구간만이 아니라 모든 교통 구간을 역산 도중 실시간으로 스냅한다.** 처음엔 "사용자가 실제로 행동을 결정하는 건 첫 구간뿐"이라는 이유로 첫 구간만 후처리로 스냅했는데, 이게 틀렸다는 게 반례로 드러났다:

```
지하철(막차 23:10, 끝 구간) ← 버스(막차 23:50이지만 실제 운행은 22:20·23:50뿐) ← 지하철(첫 구간)
```
역산 과정에서 중간 버스 구간의 candidate가 "자기 막차(23:50)"가 아니라 "다음 구간이 요구하는 시각에서 역산한 값(예: 22:45)"으로 채택될 수 있는데, **이 22:45는 산술적으로 나온 값일 뿐 실제 버스가 그 시각에 없을 수 있다**(위 예시는 22:20·23:50뿐이라 22:45엔 차가 없음). 첫 구간만 스냅하면 이 틀린 22:45가 그대로 앞쪽 구간까지 전파돼 최종 결과 자체가 부정확해진다(위 예시 기준 22:30이라는, 실제로는 불가능한 값이 나옴 — 실제로는 22:05까지 나가야 함). 그래서 **각 구간의 candidate가 확정되는 즉시, 앞 구간으로 전파되기 전에 스냅**해야 한다.

**부수 효과**: 이렇게 하면 "스냅 기준이 집 출발 시각이냐 탑승 시각이냐" 하는 별도 문제도 자동으로 해결된다 — candidate는 항상 그 구간 자체의 탑승 데드라인이고, 도보시간 차감은 그보다 나중(다음 반복 또는 순회 종료 후)에 일어나기 때문에 애초에 섞일 여지가 없다. 또한 이전엔 "raw 기준 전멸 판정 + 스냅 기준 전멸 재판정"을 이중으로 뒀는데, 스냅이 이제 순회 안에서 일어나 `min_departure_time_raw` 자체가 이미 스냅 반영값이 되므로 **판정을 한 번만 하면 되도록 단순해졌다**.

**구현**: `LastTrainCalculationService.calculate(segments, departureDatetime, tripSnapper)` — `TripSnapper` 함수를 주입받아, 각 교통 구간의 candidate 확정 직후 호출한다. 결과는 `SnapResult` 3분류:
```java
sealed interface SnapResult {
    record Snapped(LocalDateTime time) {}         // 실제 배차로 교체
    record ScheduleUnavailable() {}                // 시간표 데이터 자체가 없음 → candidate 그대로(폴백)
    record NoTripBeforeDeadline() {}               // 시간표는 있는데 데드라인 이하 배차가 없음 → 이 경로 자체가 불가능 → NoService
}
```
**`ScheduleUnavailable`과 `NoTripBeforeDeadline`을 반드시 구분해야 한다** — 전자를 "데이터가 없으니 계산값 그대로 보여주자"는 정당한 폴백이지만, 후자를 똑같이 처리하면 실제로는 존재하지 않는 배차를 탈 수 있는 것처럼 안내하는 버그가 된다(이 기능 전체가 막으려던 바로 그 문제). `NoTripBeforeDeadline`은 해당 구간 인덱스와 함께 `LastTrainCalculationResult.NoService`로 반환한다.

`TripSnapper`는 `SubwayScheduleClient`/`BusLastDepartureClient` 등 외부 클라이언트 타입을 몰라도 되는 얇은 함수형 인터페이스라, `LastTrainCalculationService`는 여전히 외부 API 타입에 직접 의존하지 않는다(4절 "벤더 분기는 조립 계층에만" 원칙 유지) — 실제 조회 로직은 조립 계층이 `TripSnapper` 구현체를 만들어서 주입한다. 순수 계산만 검증할 땐 `TripSnapper.none()`(항상 `ScheduleUnavailable`, 즉 스냅 안 함)을 넘긴다 — 테스트는 이걸로 기존 동작을 그대로 재현한다.

**데이터 소스**: SUBWAY는 **[2026-09-07 변경]** TAGO 지하철정보 서비스 `GetSubwaySttnAcctoSchdulList`(3절), BUS는 5.3절 `BUS_NODE_PASSAGE`(관측 부족 시 5.2절 추정치, 즉 `ScheduleUnavailable`이 아니라 추정값을 `LastDeparture`에 담아 candidate 자체가 이미 추정치인 채로 스냅 없이 통과).

**미해결**:
- 스냅용 "역/노선 시간표" 조회 결과 캐싱 전략 — 기존 막차 시각 캐시(당일 자정 만료)와 통합할지, 별도 캐시를 둘지.
- BUS는 5.3절 관측 데이터가 아직 부족한 신규 서비스 초기엔 대부분의 구간이 `ScheduleUnavailable`(5.2절 추정값 그대로 통과)로 처리될 것 — 이 폴백이 실제로 안전한지는 10절 참고.

### 5.2 서울 외 지역 정류소별 막차 시각 추정 (1단계 계산식, 2026-09-06 확정)

**[2026-09-07 구현 완료]** `TagoBusLastDepartureClient`(`external/tago/`)로 `BusLastDepartureClient`
실제 구현 완료 — `getRouteInfoIem` + `getRouteAcctoThrghSttnList` 실제 문서 기반 연동, 순수 계산
(`BusStopLastDepartureEstimator`, 이미 구현·테스트돼있던 것)에 그대로 위임. 평균속도는
`bus-estimate.average-speed-mps` 설정값으로 뺐음(기본 10m/s, 미확정치 — 아래 문단 그대로).
`BusLastDepartureClient.findLastDeparture`/`findLatestDepartureAtOrBefore`에 `cityCode`
파라미터가 추가됨 — TAGO 버스 계열 API 전부 routeId만으론 조회가 안 되고 필수라서(TransitSegment
에도 `cityCode` 필드 추가, 조립 계층이 채워줘야 함). ⚠️ 실제 키로 호출 확인은 아직 안 함.
**findLatestDepartureAtOrBefore는 근사치임에 주의** — 이 API가 주는 건 "노선 막차 시각 1건"뿐이라
진짜 배차 목록 조회가 아니라 "추정 막차가 deadline 이하인지"만 확인한다(5.3절 실제 관측
데이터 기반 구현체가 이 근사를 나중에 대체).

**전제 데이터** (모두 TAGO 버스노선정보 서비스, 실제 호출로 확인됨):
- `노선정보항목조회`(`getRouteInfoIem`): 노선의 `endvehicletime`(막차시간, `HHMM`), 요일별 배차간격 `intervaltime`(평일)/`intervalsattime`(토요일)/`intervalsuntime`(일요일)
- `노선별경유정류소목록조회`(`getRouteAcctoThrghSttnList`): 노선의 전체 정류소를 기점부터 순서대로(`nodeord`, 기점=1) 좌표(`gpslati`/`gpslong`)와 함께 나열. **정류소 간 거리·소요시간 필드는 없음** — 좌표로 직접 계산해야 함.

**해석 확정**: `endvehicletime`은 "막차가 **기점**(`nodeord=1`)에서 출발하는 시각" 기준으로 해석한다. 활용가이드 원문은 "막차시간"이라고만 되어 있어 기점/종점 어느 기준인지 명시하지 않지만, (1) 국내 버스 노선 정보 표기 관행상 첫차/막차는 항상 기점 출발 시각을 의미하고, (2) 실제 응답에서 `nodeord=1`이 `startnodenm`(기점)과 일치함을 확인했으므로 이 해석을 채택한다. **단, 문서로 명문화된 사실은 아니므로 실제 노선 하나를 골라 정류장 게시 시간표와 대조 검증 필요(10절 미해결로 추가).**

**계산식**:
```
정류소(nodeord=N)의 막차 통과 예정시각
  = endvehicletime (기점, nodeord=1 막차 출발시각)
  + Σ[i=1..N-1] ( haversine_distance(정류소i, 정류소i+1) / 가정 평균속도 )
```
평균속도는 도심 버스 정차 포함 기준으로 가정치를 둔다(정확한 값 미확정 — 10절 참고, 심야 시간대 속도 괴리 이슈와 연결됨). 요일별 값은 `endvehicletime`이 아니라 배차간격(`intervaltime` 등)에만 요일 구분이 있으므로, 막차 시각 자체는 요일 무관 고정값으로 가정하고 배차간격만 요일별로 다르게 적용할지, 아니면 이 부분도 별도 확인이 필요한지는 미확정.

**5.2절은 1단계(즉시 출시용) 계산식이고, 실제 정확도는 5.3절 관측 데이터가 쌓이면서 대체되는 임시 추정치다.**

### 5.3 버스 심야 운행 기록 수집 (Phase 2 기반, 2026-09-07 방향 확정)

**[2026-09-07 구현 현황]** 아래 스키마의 엔티티 3종(`BusPollAttempt`/`BusVehiclePosition`/
`BusNodePassage`) + Repository, 통과 시각 보간 로직(`domain/bus/BusNodePassageEstimator`,
연속된 두 관측 사이에서 목표 정류소 순번을 건너뛰었는지/정확히 맞았는지로 `isObserved` 판정),
TAGO `getRouteAcctoBusLcList` 실제 클라이언트(`external/tago/TagoBusPositionClient`, 실제
활용가이드 문서 기반, `cityCode`가 필수 파라미터임을 확인함)까지 구현 완료. **아직 없는 것**:
스케줄러 본체(`@Scheduled`로 "이 노선을 지금 폴링해야 하는지" 판단해서 실제로 도는 부분) —
"막차 예정 시각(5.2절) 기준 앞뒤 2~3시간"을 판단하려면 5.2절 실연동(노선별 추정 막차 시각
계산)이 먼저 배선돼야 하는데 그게 아직 없어서, 지금 스케줄러를 만들면 속이 빈 코드가 됨.
5.2절 연동 붙을 때 같이 만들 것. `cityCode` 목록(서울 + MVP 대상 경기 시·군) 관리도 미착수.

**목표 재정의**: 애초 "정류소별 막차 시각 하나"를 모으는 게 목표였는데, 이걸로는 환승 시나리오를 못 푼다는 게 드러났다(5.1절). 지하철 막차 환승 때문에 버스를 23:50까지는 타야 하는데, 그 버스의 실제 막차가 00:30이면 "막차 정보"만으론 23:50 근처에 탈 수 있는 차가 있는지조차 알 수 없다. 그래서 목표는 **"막차 하나"가 아니라 "막차 직전 몇 시간의 실제 운행 기록(심야 시간표)"을 재구축하는 것**이다.

**데이터 소스**: `국토교통부(TAGO)_버스위치정보`(`getRouteAcctoBusLcList`, 3절) — 전국 단위 실시간 차량 위치 API. 노선ID로 조회하면 그 노선에서 현재 운행 중인 모든 차량의 `vehicleno`, 현재 정류소 `nodeid`/`nodeord`를 반환한다. (참고: 좌표 기반 정류소 조회는 별도 데이터셋 `국토교통부(TAGO)_버스정류소정보`의 `좌표기반근접정류소 목록조회`로 해결 — 10절의 서울 외 지역 좌표→정류소 미해결 항목이 이걸로 해소됨.)

**수집 범위**: 노선당 하루 종일이 아니라 **막차 예정 시각(5.2절 계산값) 기준 앞뒤 2~3시간만** 폴링한다 — 환승 시나리오 대응에 필요한 만큼만 확보하면서 API 호출량도 통제(4절 배치 전략과 같은 취지).

**데이터 흐름 및 스키마**:
```
스케줄러 → TAGO 버스위치정보 폴링
  → BUS_POLL_ATTEMPT   (폴링 시도 자체 기록 — 성공/실패, 관측된 차량 수)
  → BUS_VEHICLE_POSITION (시도 1건당 관측된 차량 위치들, poll_attempt_id FK)
  → [배치] BUS_NODE_PASSAGE (관심 정류소를 특정 차량이 통과한 시각 추정 — 직전/직후 관측 사이로 보간, passage_time_lower/upper + is_observed)
```
- `BUS_POLL_ATTEMPT`: id, city_code, route_id, collected_at, fetch_success, vehicle_count — "버스가 안 옴"과 "우리 폴러가 죽음"을 구분하기 위해 성공 여부를 매 시도마다 명시적으로 남긴다.
- `BUS_VEHICLE_POSITION`: id, poll_attempt_id FK, vehicle_no, node_id, node_ord — 원시 관측 그대로 보존(가공 없음).
- `BUS_NODE_PASSAGE`: id, service_date(자정 넘어도 원래 운행일 기준), route_id, node_id, vehicle_no, passage_time_lower, passage_time_upper, is_observed — "그날의 막차 1건"으로 뭉개지 않고 그날 관측된 **모든 통과 기록**을 보존한다. 병목 구간 계산 시 필요한 건 "가장 늦은 것"이 아니라 "deadline 이하 중 가장 늦은 것"이라 원본 기록을 지우면 안 됨(5.1절 `findLatestDepartureAtOrBefore`가 이 테이블을 직접 쿼리).

**막차 판별 시 주의(이전 GBIS 방식 기반 제안에서 채택)**:
- 마지막으로 관측된 차량만 보고 그날 막차로 확정하지 않는다 — 수집 누락·단축운행 가능성이 있으므로 여러 근거(공식 배차간격, 해당 시간대 수집 성공 여부 등)를 함께 봐야 함. 근거 부족한 날은 통계 집계 시 제외.
- 알림 판단은 관측된 값들의 평균이 아니라 **이른 쪽(가장 빠른 관측치)에 가깝게** 잡는다 — 심야엔 배차가 당겨질 수 있고, 이 방향의 오차가 사용자가 막차를 놓치는 위험한 방향이기 때문(10절과 동일한 원칙).

**참고**: `BUS_LAST_TIME_STAT`(요일별 집계 통계 테이블)은 Phase 2에서 이 원시 기록들을 몇 주치 모은 뒤 만든다 — ERD는 7절 참고.

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
      "min_departure_time": "...",  // 전 구간 스냅 반영된 최종값 (5.1절)
      "min_departure_time_raw": "...",  // 스냅 미적용, 전멸 판정 원본 시각용
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

### 5.1 `POST /alarms` — 알람 등록 (FR-09, FR-10, FR-11b)
요청: `{ "candidate_id": "uuid", "minutes_before": 10 }`
응답(201): `{ alarm_id, pre_alarm_time, departure_time, status: "PENDING" }`
응답(410): `CANDIDATE_EXPIRED`
- 이 시점에 캐시의 candidate 원본을 `ROUTE_CANDIDATE` + `SEGMENT`로 DB 영구 저장 (ERD 참조).
- `ROUTE_CANDIDATE`·`SEGMENT`·`DEPARTURE_ALARM` 저장은 하나의 `@Transactional` 경계로 원자적 처리.
- SEGMENT는 개별 INSERT 대신 `saveAll` 벌크 저장.
- **[2026-09-07 신규, FR-11b — 원본 요구사항명세서 FR-09~11엔 없던 추가 사항]** 알림은 1개가 아니라 최대 2개 예약한다: `pre_alarm_time`(= `departure_time - minutes_before`, "곧 출발해야 함") + `departure_time`(= `min_departure_time`, 도보시간·스냅 이미 반영된 값, "지금 출발해야 함"). **단, `minutes_before=0`이면 두 시각이 같아지므로 알림은 1개만 예약**(중복 알림 방지). `DEPARTURE_ALARM` 테이블엔 새 컬럼 불필요 — `departure_time`은 `ROUTE_CANDIDATE.min_departure_time`에서, `pre_alarm_time`은 거기서 `minutes_before`를 뺀 값으로 그때그때 계산.
- 클라이언트는 응답을 받는 즉시 위 알림(들)을 iOS 로컬 알림으로 예약 (FR-11).

### 5.2 `GET /alarms?status=&limit=&offset=` — 목록 조회 (FR-12)
`status`: PENDING | COMPLETED | CANCELLED (미지정 시 전체). `limit` 기본20/최대100, `offset` 기본0.

### 5.3 `PATCH /alarms/{id}` — 수정 (FR-12)
요청: `{ "minutes_before": 15 }` — 기존 값 덮어씀.
PENDING 상태만 수정 가능, 아니면 `409 INVALID_STATE`. 타인 리소스 → `403 FORBIDDEN`.
응답 수신 후 클라이언트가 **두 알림(예약돼있었다면) 모두** 재예약 (FR-11a) — 하나만 갱신하면 나머지가 유령 알림으로 남으므로 주의.

### 5.4 `DELETE /alarms/{id}` — 취소 (FR-12)
상태를 CANCELLED로 변경(실제 삭제 아님). PENDING만 가능, 아니면 409. 타인 리소스 403.
클라이언트가 **예약된 알림 전부**(1개 또는 2개) 취소 (FR-11a).

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
BUS_POLL_ATTEMPT ||--o{ BUS_VEHICLE_POSITION
BUS_VEHICLE_POSITION }o--o{ BUS_NODE_PASSAGE   # 배치로 파생
BUS_NODE_PASSAGE }o--|| BUS_LAST_TIME_STAT     # Phase 2 통계용 (몇 주치 누적 후 생성)
```

- `USER`: id, oauth_provider, oauth_id, nickname, created_at, deleted_at
- `DEPARTURE_ALARM`: id, user_id FK, route_candidate_id FK, origin/dest lat·lng, minutes_before, status (알림 시각은 컬럼으로 안 두고 `route_candidate.min_departure_time`과 `minutes_before`로 매번 계산 — 5.1절 2개 알림 참고)
- `ROUTE_CANDIDATE`: id, total_duration_sec, total_cost, min_departure_time, min_departure_time_raw
- `SEGMENT`: id, route_candidate_id FK, seq_order, transport_type, route_name, last_time_raw, is_estimated, buffer_sec
- `BUS_POLL_ATTEMPT` / `BUS_VEHICLE_POSITION` / `BUS_NODE_PASSAGE` / `BUS_LAST_TIME_STAT`: 버스 심야 운행 기록 수집·통계 고도화용 (5.3절 참고, 원시 위치 로그 → 정류소 통과 보간 → 통계 집계)

## 8. 화면 구조 (화면명세서 v0.1)

전체 화면 9개: 로그인 → 경로 검색(홈) → 목적지 검색 / 경로 후보 목록 → 경로 상세 → 알람 목록 → 알람 상세/수정 · 즐겨찾기 관리 · 설정

모달 4개: 알람 등록(바텀시트) · 알림 권한 안내 · 막차 전멸 안내(NO_ROUTE_AVAILABLE 응답 시) · 회원 탈퇴 확인

핵심 화면 전환:
- 경로 검색 → `POST /routes/search` → 결과가 NO_ROUTE_AVAILABLE이면 막차 전멸 모달, 아니면 경로 후보 목록
- 경로 상세: 길찾기 버튼(외부 지도앱 딥링크, 화면 전환 없음) / 알람 등록 버튼(모달)
- 알람 등록 시 iOS 알림 권한 미허용이면 권한 안내 모달 우선 표시 (FR-09a)

## 9. Phase 2 (MVP 이후)

`BUS_NODE_PASSAGE`(5.3절, 실시간 차량 위치 기반 정류소 통과 기록) 데이터를 `BUS_LAST_TIME_STAT`으로 집계하는 통계 기반 버스 막차 개선. 서버가 막차 예정 시각 전후로 차량 위치를 주기적 폴링 → 일 1회 배치 집계 → 통계 신뢰 임계치(관측 횟수) 미만이면 1단계 계산식(5.2절) 기반 추정치로 폴백.

**[2026-09-07 신규] Phase 2 완료 후 API 응답에 `source`/`confidence` 노출 검토.** 지금은 `TransitSegment.isEstimated`(불리언)로만 "국토부 추정이냐 아니냐"를 구분하는데, 통계 데이터가 쌓이면 `source`(OBSERVED/TAGO_ESTIMATED 등)·`confidence`(관측 횟수 기반) 같은 세분화된 값을 API 응답에 노출해서 클라이언트가 "막차 예상 23:52 — 최근 실제 운행 기반" vs "운행정보 부족으로 추정" 같은 문구를 구분해 보여줄 수 있음. 지금은 이걸 채울 실제 통계가 없어서(HIGH confidence 판단 근거 자체가 없음) 미룸 — Phase 2 통계 집계 로직과 함께 설계.

**착수 시점 방침**: 통계 집계 로직(`BUS_LAST_TIME_STAT`, 배치)은 Phase 2에서 만들지만, 차량 위치 폴링(`BUS_POLL_ATTEMPT`/`BUS_VEHICLE_POSITION`, 5.3절) 자체는 관측 데이터 축적에 시간이 걸리므로 **[2026-09-06 갱신] MVP 완성 직후가 아니라 배포(앱스토어 출시) 이전에 조기 착수**한다 — 출시 시점에 이미 몇 주치 관측 데이터가 쌓여있는 상태로 시작하기 위함. 서울시 버스 API 가용 여부 검증(10절) 완료가 선행 조건(완료됨). **폴링 대상 범위는 전국이 아니라 MVP 커버리지인 서울·경기도 핵심 노선으로 한정**한다(1절 참고 — 인천 등 나머지 수도권은 MVP 범위 밖) — 실사용자 검색 이력이 아직 없는 상태에서 전국 블랭킷 폴링은 9절 하단에서 이미 기각한 것과 같은 비용 문제를 일으키므로, 스코프를 좁혀 비용을 통제한다. 자세한 이유는 `CLAUDE.md` "실제 출시 전 반드시 처리해야 할 것" 참고.

**[2026-09-05 재확인] "출시 전에 미리 관측 데이터를 모아서 바로 Phase 2로 출시"는 검토 후 기각.** 카카오맵 "버스 예상시간표"(요일별 최대 10주 과거 도착 데이터 활용, 공식 블로그 근거)처럼 실제 운행 이력 기반 예측이 업계 표준 방향인 건 맞지만, 사전 수집 방식은 (1) 아직 실사용자 검색 이력이 없어 어떤 노선을 모아야 할지 몰라 수도권 버스망 전체를 블랭킷 폴링해야 함(비용 급증, 4절 비용 관리 원칙과 충돌), (2) 유의미한 심야 데이터 축적에 최소 몇 주가 걸려 출시가 그만큼 늦어짐(목표 1번과 충돌), (3) 그렇게 해도 신규 노선·저빈도 노선은 항상 관측 부족 상태라 폴백이 어차피 필요함 — 이 세 가지 이유로 기각하고 원래 방침(1단계로 즉시 출시 + 출시 직후 폴링 조기 착수 + 관측치 충분한 노선부터 순차 전환)을 유지하기로 확정함.

### 9.1 알람 직전 재검증 (아이디어, Phase 2 이후)

**아이디어**: 알람 등록 시점에 계산한 출발 시각(`min_departure_time`)을 고정하지 않고, 막차 시간 임박 시점에 최신 교통정보로 재확인해서 필요하면 로컬 알림을 재계산·재예약. 예: 23:00에 "23:25 출발" 알림을 예약했는데, 23:15에 막차 예상이 23:34로 당겨졌다면 기존 알림(23:20)이 위험해질 수 있음.

**Phase 2 이후로 미루는 이유(단순 일정 문제가 아님, 2026-09-05 논의)**:
1. **구조적 충돌**: 알림은 iOS 로컬 알림(`UNUserNotificationCenter`)뿐이라 서버가 재계산한 값을 기기에 반영하려면 (a) "APNs 서버 푸시 절대 금지"(4절) 원칙에 예외(최소 사일런트 푸시)를 만들거나, (b) 앱 자체 백그라운드 재확인(`BGAppRefreshTask` 등, iOS가 실행 시점 보장 안 함 — "알람 직전 반드시 재확인" 보장 불가)에 의존해야 함. 실제 설계 시 반드시 확인받고 진행할 것 — 조용히 (a)로 뒤집으면 안 됨.
2. **데이터 전제 조건**: 1단계 계산식(정적 계산, 5.2절)은 재검증해도 값이 안 바뀜 — 이 기능이 의미를 가지려면 Phase 2의 차량 위치 폴링(`BUS_NODE_PASSAGE`, 5.3절) 데이터가 있어야 함. 즉 "나중에 시간 날 때"가 아니라 "Phase 2 인프라가 전제조건"이라 자연스럽게 함께 감.

**연관 항목**: 10절의 "iOS 로컬 알림 소실 대비 서버 백업 로직"도 같은 긴장("로컬 전용 원칙에 예외를 둘지")을 공유하므로, 실제 설계 시 두 항목을 함께 다룰 것.

## 10. 미해결/향후 검토 (SRS 5.3, 5.4)

- 경로 조회 이력 보존 기간, 버스 도착 로그 보존 기간, 통계 신뢰 임계치 — 값 미확정 (참고: 카카오맵은 요일별 최대 10주 데이터 활용한다고 공개함 — 내부 알고리즘은 비공개라 그대로 채택할 근거는 아니지만 자릿수 참고치로는 쓸 만함)
- **[2026-09-07 신규] ODsay 이용약관 제4.5.10조("사전 동의 없는 결과 데이터 무단 저장 금지")가 영구 저장뿐 아니라 당일 자정까지의 일시 Redis 캐싱에도 적용되는지 불확실.** 공개된 약관 문구만으론 "저장"의 범위(영구/일시)가 명확히 구분 안 됨 — **실제 운영 전 ODsay에 직접 문의해서 일시 캐싱 허용 범위를 서면으로 확인 필요**(CLAUDE.md "실제 출시 전 반드시 처리해야 할 것"에도 추가).
- **[2026-09-07 신규] `DayTypeResolver`의 새벽 서비스 데이 컷오프(04:00)가 확정된 근거 없이 업계 관행 추정치로 잡혀있음.** 실제 사업자별로 심야버스 운행 종료 시각이 다를 수 있어, 이 값이 맞는지 검증 필요(4절 참고).
- **[2026-09-07 신규] API 장애 시 stale 캐시 폴백 정책 미확정.** 지금은 외부 API 실패 시 무조건 `FETCH_ERROR`(503)인데, 캐시가 만료 직후이고 값 자체는 크게 안 바뀌었을 상황이라면 "약간 오래된 캐시라도 잠깐 제공"하는 옵션이 완전 장애보다 나을 수 있음. Redis 캐싱 코드 작성 시 같이 결정.
- 1단계 계산식(3절)의 정류장 간 평균 소요시간 오차가 정류장 개수에 비례 누적되는데 버퍼(FR-05a, 고정 5분)가 정류장 수와 무관한 고정값인 점, 그리고 "평균 소요시간"이 낮 시간대 기준이면 심야(막차 시간대) 실제 소요시간과 괴리될 수 있는 점(심야가 대체로 더 빨라 실제 막차가 추정보다 일찍 도착하는 위험한 방향) — 여전히 미확정. **2026-09-06 확인: TAGO `getRouteAcctoThrghSttnList`(노선별경유정류소목록)에는 정류소 간 거리/소요시간 필드가 없고 좌표(`gpslati`/`gpslong`)와 순번(`nodeord`)만 제공 → 하버사인 거리 계산 + 가정 평균속도 방식으로 구간 소요시간을 추정해야 함. 이 추정 오차가 버퍼 재산정 검토의 전제가 됨.**
- ~~서울시 버스 막차 API 실제 가용 여부~~ **2026-09-06 해결: `getBustimeByStationList` 실제 호출 검증 완료(3절 참고, 파라미터·응답 필드명 확정).** 단, 요일 지정이 불가능해 배치로 요일별 관측 필요(4절 신규 배치 전략 항목) — 출시 초기 관측 안 된 요일의 폴백 방식은 별도 미해결로 남음.
- iOS Critical Alert, 로컬 알림 소실 대비 서버 백업 로직 — MVP 범위 외
- 앱 내 자체 길찾기(지도 SDK 렌더링) — MVP 범위 외, 딥링크로 대체 중
- 첫 구간 실제 배차 스냅(5.1절) — `snapped_time`을 못 찾는 극단 케이스 처리(특히 BUS는 5.3 데이터 부족 시 상시 발생 가능, 5.1절 참고), 캐싱 전략 미확정. **2026-09-07 수정: 스냅 기준을 버퍼 적용 후 값에서 버퍼 적용 전(raw) 값으로 변경 — 버퍼 반영된 데드라인으로 스냅하면 실제로는 안전한 배차를 놓치고 불필요하게 이른 시각으로 스냅될 수 있었음(5.1절 참고).**
- ~~공휴일 판별(`DayType.SUNDAY_OR_HOLIDAY`) 데이터 소스는 찾았으나 실제 연동·캐싱 로직은 아직 미구현.~~ **2026-09-07 부분 해결: `DayTypeResolver`(요일+공휴일 → DayType 판별 로직) 구현·테스트 완료. `HolidayClient` 인터페이스만 만들고 실제 data.go.kr 호출 구현체는 아직 미착수 — 캐싱 전략(연/월 단위)도 실제 구현 시 확정 필요.**
- **[2026-09-07 신규] 5.3절 `BUS_NODE_PASSAGE` 생성 시 운행편(trip) 구분 없이 `vehicle_no`+시간순만으로 정류소 통과를 보간하기로 단순화했는데, 감시 시간대(막차 전후 2~3시간) 안에서 같은 차량이 두 번 이상 운행하면 서로 다른 운행을 하나로 잘못 이어붙여 통과시각을 오추정할 위험이 있음 — 실제 구현 전 재검토 필요.**
- 5.2절 `endvehicletime`(기점 막차 출발시각 해석) — 공식 문서로 명문화된 게 아니라 관행 기반 추론. 실제 노선 하나 골라 정류장 게시 시간표와 대조 검증 필요. 요일별 배차간격(`intervaltime` 등)은 있는데 막차 시각 자체엔 요일 구분이 없어, 막차 시각을 요일 무관 고정값으로 볼지 여부도 미확정
- ~~서울 외 지역 "사용자 좌표 → 근처 정류소ID" 변환 방법 미확정.~~ **2026-09-06 해결: `국토교통부(TAGO)_버스정류소정보`(별도 데이터셋, 정적 파일 아니고 실시간 REST API 확인)의 `좌표기반근접정류소 목록조회`(반경 500m)로 해결.**
