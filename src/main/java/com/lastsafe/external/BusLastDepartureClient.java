package com.lastsafe.external;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.lastsafe.domain.lasttrain.LastDeparture;

/**
 * 버스 정류장별 막차 시각 조회를 추상화한다 (NFR-07). 서울은 data.go.kr
 * "서울특별시_정류소정보조회 서비스"({@code getBustimeByStationList}, spec.md 3절) 직접 조회,
 * 그 외 지역은 국토부 노선정보 API 기반 1단계 추정 계산(5.2절) — 구현체가 달라도 도메인/서비스
 * 계층은 이 인터페이스 하나만 알면 된다.
 *
 * 5.1절 실제 배차 스냅은 [2026-09-07] BUS에도 적용하기로 확정했다 — 버스가 병목이 아닐 때
 * (예: 지하철 막차 환승 때문에 그보다 이른 버스를 타야 하는 경우) "막차 시각 하나"만으로는
 * "데드라인 이하 중 가장 늦은 실제 운행"을 찾을 수 없기 때문. {@code findLatestDepartureAtOrBefore}는
 * 5.3절의 {@code BUS_NODE_PASSAGE}(실시간 차량 위치 기반 정류소 통과 기록)를 조회하는 구현체가
 * 맡는다 — 그 데이터가 아직 없는 신규/저관측 노선은 5.2절 추정치로 폴백.
 */
public interface BusLastDepartureClient {

    /**
     * {@code serviceDate}가 필요한 이유는 {@link SubwayScheduleClient#findLastDeparture}와 동일 —
     * dayType만으론 시각표 항목을 실제 {@link LocalDateTime}으로 못 만든다. {@code cityCode}는
     * TAGO 버스 계열 API 전부(5.2절 `getRouteInfoIem`/`getRouteAcctoThrghSttnList`, 5.3절
     * `getRouteAcctoBusLcList`)가 routeId만으론 조회가 안 되고 요구하는 필수 파라미터라 여기서도
     * 받는다 — 어떤 도시코드를 쓸지는 조립 계층이 안다고 가정.
     */
    LastDeparture findLastDeparture(String stationId, String routeId, String cityCode, LocalDate serviceDate, DayType dayType);

    /**
     * deadline 이하의 실제(또는 5.2절 추정) 배차 시각 중 가장 늦은 것. {@code SubwayScheduleClient}의
     * 동명 메서드와 같은 목적 — 첫 구간이 BUS일 때 역산된 데드라인을 실제 운행에 맞추는 스냅 로직에
     * 쓰인다 (spec.md 5.1절). 그런 배차가 없으면(예: deadline이 그날 첫차보다 이른 경우) empty.
     */
    Optional<LocalDateTime> findLatestDepartureAtOrBefore(
            String stationId, String routeId, String cityCode, LocalDateTime deadline, DayType dayType);
}
