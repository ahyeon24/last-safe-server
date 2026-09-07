package com.lastsafe.domain.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;

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
}
