package com.lastsafe.external;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.lastsafe.domain.lasttrain.LastDeparture;

/**
 * 지하철 역별 배차 시각 조회를 추상화한다 (ODsay {@code searchSubwaySchedule} 연동, NFR-07).
 * 도메인/서비스 계층이 ODsay API 형식에 직접 의존하지 않도록 이 인터페이스 뒤로 감춘다.
 */
public interface SubwayScheduleClient {

    /**
     * 해당 역·방향의 {@code serviceDate} 기준 막차 시각. {@code LastTrainCalculationService}가
     * 구간별 {@code last_departure_time} 입력으로 사용한다 (spec.md 5절). {@code dayType}만으로는
     * "그날"이 어떤 달력 날짜인지 알 수 없어(평일이 한 달에 여러 날 있음) 실제 시각표 항목을
     * {@link LocalDateTime}으로 바꾸려면 {@code serviceDate}가 반드시 필요하다 —
     * {@code DayTypeResolver.resolveServiceDate}로 구한 값을 넘길 것.
     */
    LastDeparture findLastDeparture(String stationId, String direction, LocalDate serviceDate, DayType dayType);

    /**
     * deadline 이하의 실제 배차 시각 중 가장 늦은 것. 첫 구간이 SUBWAY일 때 역산된 데드라인을
     * 실제 시간표에 맞추는 스냅 로직에 쓰인다 (spec.md 5.1절). 그런 배차가 없으면(예: deadline이
     * 그날 첫차보다 이른 경우) empty.
     */
    Optional<LocalDateTime> findLatestDepartureAtOrBefore(
            String stationId, String direction, LocalDateTime deadline, DayType dayType);
}
