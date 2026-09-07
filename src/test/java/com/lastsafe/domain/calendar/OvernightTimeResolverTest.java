package com.lastsafe.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class OvernightTimeResolverTest {

    private final LocalDate serviceDate = LocalDate.of(2026, 9, 7);

    @Test
    void 막차가_첫차보다_이른_시각이면_다음날로_계산된다() {
        // spec.md 5.2절 실제 관측 예시: startvehicletime=0530, endvehicletime=0010
        LocalDateTime result = OvernightTimeResolver.resolveLastDeparture(
                serviceDate, LocalTime.of(5, 30), LocalTime.of(0, 10));

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 9, 8, 0, 10));
    }

    @Test
    void 막차가_첫차보다_늦은_시각이면_같은날로_계산된다() {
        LocalDateTime result = OvernightTimeResolver.resolveLastDeparture(
                serviceDate, LocalTime.of(5, 30), LocalTime.of(23, 50));

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 9, 7, 23, 50));
    }

    @Test
    void 시각표_전체를_넘기면_자정을_넘는_지점부터_다음날로_계산된다() {
        List<LocalDateTime> result = OvernightTimeResolver.resolveScheduleSequence(serviceDate, List.of(
                LocalTime.of(5, 30), LocalTime.of(23, 50), LocalTime.of(0, 10), LocalTime.of(1, 0)));

        assertThat(result).containsExactly(
                LocalDateTime.of(2026, 9, 7, 5, 30),
                LocalDateTime.of(2026, 9, 7, 23, 50),
                LocalDateTime.of(2026, 9, 8, 0, 10),
                LocalDateTime.of(2026, 9, 8, 1, 0));
    }

    @Test
    void 시각표에_자정_넘김이_없으면_전부_같은날로_계산된다() {
        List<LocalDateTime> result = OvernightTimeResolver.resolveScheduleSequence(serviceDate, List.of(
                LocalTime.of(5, 30), LocalTime.of(12, 0), LocalTime.of(23, 50)));

        assertThat(result).containsExactly(
                LocalDateTime.of(2026, 9, 7, 5, 30),
                LocalDateTime.of(2026, 9, 7, 12, 0),
                LocalDateTime.of(2026, 9, 7, 23, 50));
    }
}
