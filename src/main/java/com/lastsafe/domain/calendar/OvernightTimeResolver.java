package com.lastsafe.domain.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * {@link #resolveLastDeparture}를 첫차/막차 한 쌍이 아니라, 그날의 배차 시각표 전체
     * (TAGO {@code GetSubwaySttnAcctoSchdulList} 같은 리스트 응답)에 일반화한 버전이다.
     *
     * {@code times}가 어떤 순서로 오는지 API가 보장하지 않으므로(TAGO 활용가이드에 정렬 순서
     * 명시 없음) 입력 순서에 의존하지 않는다 — 대신 시각을 하루 안(00:00~23:59)에서 오름차순
     * 정렬한 뒤, "순환적으로(23:59 다음은 다시 00:00)" 가장 긴 공백 구간을 찾는다. 실제
     * 지하철은 심야에 운행이 끊기는 구간이 반드시 있으므로, 그 가장 긴 공백 직후가 "그날의
     * 첫차"이고 공백 직전까지가 "자정을 넘긴 막차들"이라는 논리다 — 몇 시 이전은 다음날이라는
     * 임의 컷오프 없이, 실제 데이터의 운행 공백만으로 자정 넘김을 판별한다.
     *
     * 시각이 1개 이하이거나 하루 종일 고르게 퍼져있어 공백이 딱히 두드러지지 않는 경우엔
     * (막차 서비스 특성상 실제로는 거의 없음) 가장 긴 공백 뒤를 그냥 그대로 채택한다.
     *
     * ⚠️ 전제: 실제 배차 간격(낮 동안)이 심야 운행 공백보다 항상 짧다 — TAGO 활용가이드
     * 예시(역 하나·하루 257건)처럼 실제 지하철 시간표는 이 전제를 만족한다. 배차가 아주
     * 뜸한 노선에서 낮 시간대 배차 간격이 우연히 심야 공백보다 길면 오판할 수 있음 — 지금은
     * 근거 없는 조기 대응이라 처리 안 함, 실제로 문제되면 그때 대응.
     */
    public static List<LocalDateTime> resolveUnorderedDailySchedule(LocalDate serviceDate, List<LocalTime> times) {
        if (times.isEmpty()) {
            return List.of();
        }
        List<LocalTime> sorted = times.stream().sorted().distinct().toList();
        if (sorted.size() == 1) {
            return List.of(serviceDate.atTime(sorted.get(0)));
        }

        int rolloverIndex = 0;
        long largestGapSeconds = -1;
        for (int i = 0; i < sorted.size(); i++) {
            LocalTime current = sorted.get(i);
            LocalTime next = sorted.get((i + 1) % sorted.size());
            long gapSeconds = current.until(next, java.time.temporal.ChronoUnit.SECONDS);
            if (gapSeconds < 0) {
                gapSeconds += 24 * 3600; // sorted 리스트의 마지막→처음으로 넘어가는 순환 구간
            }
            if (gapSeconds > largestGapSeconds) {
                largestGapSeconds = gapSeconds;
                rolloverIndex = (i + 1) % sorted.size(); // 이 공백 바로 다음이 "그날의 첫차"
            }
        }

        List<LocalDateTime> result = new ArrayList<>(sorted.size());
        int sameDayCount = sorted.size() - rolloverIndex;
        for (int i = 0; i < sorted.size(); i++) {
            int idx = (rolloverIndex + i) % sorted.size();
            LocalDate date = i < sameDayCount ? serviceDate : serviceDate.plusDays(1);
            result.add(date.atTime(sorted.get(idx)));
        }
        return result;
    }
}
