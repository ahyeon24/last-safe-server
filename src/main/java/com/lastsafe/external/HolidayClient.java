package com.lastsafe.external;

import java.time.LocalDate;

/**
 * 공휴일 여부 조회를 추상화한다 (NFR-07). 한국천문연구원_특일 정보(data.go.kr,
 * {@code SpcdeInfoService.getHolidayInfo}, spec.md 3절) 기반 — {@link DayType#SUNDAY_OR_HOLIDAY}
 * 판별에 필수. 이게 없으면 평일에 낀 공휴일(예: 설날이 화요일)을 WEEKDAY로 잘못 계산하게 된다.
 *
 * 이 API는 연/월 단위로만 조회 가능해서 매 요청마다 호출하면 비효율적이다 — 구현체가 연 단위로
 * 미리 받아 캐싱해두는 방식을 권장한다(spec.md 4절 배치 전략과 같은 취지).
 */
public interface HolidayClient {

    boolean isHoliday(LocalDate date);
}
