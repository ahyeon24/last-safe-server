package com.lastsafe.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lastsafe.external.DayType;
import com.lastsafe.external.HolidayClient;

class DayTypeResolverTest {

    /** 2026-09-15(화)를 임시공휴일로 가정한 가짜 구현 — 실제 API 없이 판별 로직만 검증. */
    private static final HolidayClient FAKE_HOLIDAY_CLIENT =
            date -> Set.of(LocalDate.of(2026, 9, 15)).contains(date);

    private final DayTypeResolver resolver = new DayTypeResolver(FAKE_HOLIDAY_CLIENT);

    @Test
    void 평일은_WEEKDAY로_판별된다() {
        // 2026-09-07은 월요일
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 7))).isEqualTo(DayType.WEEKDAY);
    }

    @Test
    void 토요일은_SATURDAY로_판별된다() {
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 12))).isEqualTo(DayType.SATURDAY);
    }

    @Test
    void 일요일은_SUNDAY_OR_HOLIDAY로_판별된다() {
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 13))).isEqualTo(DayType.SUNDAY_OR_HOLIDAY);
    }

    @Test
    void 평일에_낀_공휴일도_SUNDAY_OR_HOLIDAY로_판별된다() {
        // 2026-09-15는 화요일이지만 가짜 공휴일 목록에 있음
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 15))).isEqualTo(DayType.SUNDAY_OR_HOLIDAY);
    }
}
