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
     * (ODsay {@code searchSubwaySchedule} 같은 리스트 응답)에 일반화한 버전이다.
     * {@code orderedTimes}가 실제 운행 순서대로(자정을 넘기더라도 이어서) 정렬돼 들어온다고
     * 가정하고, 이전 항목보다 시각이 "역행"하는 지점을 자정을 넘긴 지점으로 판단해 날짜를
     * 하루씩 넘긴다 — 첫차/막차 두 값만 비교하던 규칙을 N개로 그대로 확장한 것뿐이라 별도
     * 근거(예: "몇 시 이전은 다음날" 같은 임의 컷오프)가 필요 없다.
     */
    public static List<LocalDateTime> resolveScheduleSequence(LocalDate serviceDate, List<LocalTime> orderedTimes) {
        List<LocalDateTime> result = new ArrayList<>(orderedTimes.size());
        LocalDate currentDate = serviceDate;
        LocalTime previous = null;
        for (LocalTime time : orderedTimes) {
            if (previous != null && time.isBefore(previous)) {
                currentDate = currentDate.plusDays(1);
            }
            result.add(currentDate.atTime(time));
            previous = time;
        }
        return result;
    }
}
