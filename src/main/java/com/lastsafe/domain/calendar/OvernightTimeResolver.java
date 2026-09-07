package com.lastsafe.domain.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * TAGO/서울시 API가 주는 "HH:mm" 첫차/막차 시각을 올바른 달력 날짜의 {@link LocalDateTime}으로
 * 바꾼다. 국내 버스는 자정을 넘어서도 전날 운행으로 취급하는 경우가 흔해서(예: TAGO
 * {@code endvehicletime=0010}은 그날 첫차 시각 이후에 오므로 실제로는 다음날 새벽), 그냥
 * "오늘 날짜 + HH:mm"으로 파싱하면 막차가 첫차보다 이른 시각으로 잘못 계산될 수 있다.
 *
 * 규칙: 막차 시각이 첫차 시각보다 이르면(시:분만 비교) 다음날로 취급한다 — 실제 응답
 * (spec.md 5.2절 예: startvehicletime=0530, endvehicletime=0010)에서 관찰된 패턴 그대로다.
 */
public final class OvernightTimeResolver {

    private OvernightTimeResolver() {
    }

    public static LocalDateTime resolveLastDeparture(
            LocalDate serviceDate, LocalTime firstDepartureTime, LocalTime lastDepartureTime) {
        LocalDateTime lastDeparture = serviceDate.atTime(lastDepartureTime);
        if (lastDepartureTime.isBefore(firstDepartureTime)) {
            lastDeparture = lastDeparture.plusDays(1);
        }
        return lastDeparture;
    }
}
