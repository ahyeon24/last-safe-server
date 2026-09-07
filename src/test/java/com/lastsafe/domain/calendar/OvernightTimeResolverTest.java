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
    void 입력_순서가_뒤섞여있어도_심야_공백_뒤가_첫차로_판별된다() {
        // TAGO가 정렬 순서를 보장하지 않는다고 가정 — 일부러 순서를 섞어서 검증.
        // 실제 지하철 시간표(TAGO 예시 하루 257건)처럼 낮 동안은 배차 간격이 촘촘하고(2시간
        // 이내), 01:00~05:30 사이(4시간 반)만 운행이 끊긴다고 가정 — 이 공백이 다른 어떤
        // 배차 간격보다 뚜렷하게 커야 "largest gap" 판별이 우연이 아니라 실제로 유효하다.
        List<LocalDateTime> result = OvernightTimeResolver.resolveUnorderedDailySchedule(serviceDate, List.of(
                LocalTime.of(21, 30), LocalTime.of(1, 0), LocalTime.of(13, 30), LocalTime.of(5, 30),
                LocalTime.of(9, 30), LocalTime.of(23, 50), LocalTime.of(17, 30), LocalTime.of(0, 10),
                LocalTime.of(7, 30), LocalTime.of(11, 30), LocalTime.of(19, 30), LocalTime.of(15, 30)));

        assertThat(result).containsExactly(
                LocalDateTime.of(2026, 9, 7, 5, 30),
                LocalDateTime.of(2026, 9, 7, 7, 30),
                LocalDateTime.of(2026, 9, 7, 9, 30),
                LocalDateTime.of(2026, 9, 7, 11, 30),
                LocalDateTime.of(2026, 9, 7, 13, 30),
                LocalDateTime.of(2026, 9, 7, 15, 30),
                LocalDateTime.of(2026, 9, 7, 17, 30),
                LocalDateTime.of(2026, 9, 7, 19, 30),
                LocalDateTime.of(2026, 9, 7, 21, 30),
                LocalDateTime.of(2026, 9, 7, 23, 50),
                LocalDateTime.of(2026, 9, 8, 0, 10),
                LocalDateTime.of(2026, 9, 8, 1, 0));
    }

    @Test
    void 자정_넘김이_없으면_전부_같은날로_계산된다() {
        // 자정 근처(23:50→익일 05:30, 5시간 40분) 공백이 낮 동안의 어떤 배차 간격(2시간
        // 이내)보다도 뚜렷하게 크다고 가정 — 촘촘한 낮 시간표를 흉내냄.
        List<LocalDateTime> result = OvernightTimeResolver.resolveUnorderedDailySchedule(serviceDate, List.of(
                LocalTime.of(23, 50), LocalTime.of(9, 30), LocalTime.of(19, 30), LocalTime.of(5, 30),
                LocalTime.of(15, 30), LocalTime.of(7, 30), LocalTime.of(21, 30), LocalTime.of(11, 30),
                LocalTime.of(23, 30), LocalTime.of(13, 30), LocalTime.of(17, 30)));

        assertThat(result).containsExactly(
                LocalDateTime.of(2026, 9, 7, 5, 30),
                LocalDateTime.of(2026, 9, 7, 7, 30),
                LocalDateTime.of(2026, 9, 7, 9, 30),
                LocalDateTime.of(2026, 9, 7, 11, 30),
                LocalDateTime.of(2026, 9, 7, 13, 30),
                LocalDateTime.of(2026, 9, 7, 15, 30),
                LocalDateTime.of(2026, 9, 7, 17, 30),
                LocalDateTime.of(2026, 9, 7, 19, 30),
                LocalDateTime.of(2026, 9, 7, 21, 30),
                LocalDateTime.of(2026, 9, 7, 23, 30),
                LocalDateTime.of(2026, 9, 7, 23, 50));
    }

    @Test
    void 시각이_하나뿐이면_그대로_같은날로_반환한다() {
        List<LocalDateTime> result = OvernightTimeResolver.resolveUnorderedDailySchedule(
                serviceDate, List.of(LocalTime.of(23, 50)));

        assertThat(result).containsExactly(LocalDateTime.of(2026, 9, 7, 23, 50));
    }

    @Test
    void 빈_시각표는_빈_리스트를_반환한다() {
        assertThat(OvernightTimeResolver.resolveUnorderedDailySchedule(serviceDate, List.of())).isEmpty();
    }
}
