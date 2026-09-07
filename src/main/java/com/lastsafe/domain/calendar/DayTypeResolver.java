package com.lastsafe.domain.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.lastsafe.external.DayType;
import com.lastsafe.external.HolidayClient;

/**
 * 날짜 하나로부터 {@link DayType}을 판별한다. 일요일과 공휴일은 국내 대중교통 배차표상
 * 동일 스케줄로 취급되므로 하나로 묶는다(DayType.java 참고) — 그래서 토요일이 아닌 날은
 * 요일만으로 못 정하고 반드시 {@link HolidayClient} 조회를 거쳐야 한다(평일에 낀 설날 등).
 *
 * {@code @Service}를 아직 안 붙였다 — {@link HolidayClient}의 실제 구현체(data.go.kr 호출)가
 * 없는 상태에서 이걸 스프링 빈으로 등록하면, 의존성을 주입할 대상이 없어 앱 컨텍스트 자체가
 * 뜨지 않는다(실제로 겪은 문제). HolidayClient 구현체를 만들 때 같이 등록할 것.
 */
public class DayTypeResolver {

    /**
     * 새벽 이 시각 이전엔 "오늘"이 아니라 "어제 밤 운행의 연장"으로 취급한다 — 예를 들어
     * 일요일 밤이 월요일 새벽 2시까지 이어지면, 그 배차는 월요일(평일) 시간표가 아니라
     * 일요일 시간표를 따른다. 국내 다수 사업자 심야버스/막차 관행 기준 04시로 잡음 —
     * 정확한 값은 확정된 게 아니라 사업자마다 다를 수 있음(spec.md 10절 참고).
     */
    private static final LocalTime SERVICE_DAY_CUTOFF = LocalTime.of(4, 0);

    private final HolidayClient holidayClient;

    public DayTypeResolver(HolidayClient holidayClient) {
        this.holidayClient = holidayClient;
    }

    public DayType resolve(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY || holidayClient.isHoliday(date)) {
            return DayType.SUNDAY_OR_HOLIDAY;
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return DayType.SATURDAY;
        }
        return DayType.WEEKDAY;
    }

    /**
     * 실제 요청 시각(예: departure_datetime) 기준으로 서비스 데이를 판별해서 {@link #resolve}한다.
     * 자정 넘어 새벽에 검색해도, 그 시각이 {@link #SERVICE_DAY_CUTOFF} 이전이면 전날 기준으로
     * 판별한다.
     */
    public DayType resolveForDateTime(LocalDateTime dateTime) {
        return resolve(resolveServiceDate(dateTime));
    }

    public LocalDate resolveServiceDate(LocalDateTime dateTime) {
        if (dateTime.toLocalTime().isBefore(SERVICE_DAY_CUTOFF)) {
            return dateTime.toLocalDate().minusDays(1);
        }
        return dateTime.toLocalDate();
    }
}
