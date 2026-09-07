package com.lastsafe.domain.bus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 서울 외 지역 버스의 정류소별 막차 통과 예정시각 1단계 추정 계산식 (spec.md 5.2절).
 * TAGO가 주는 건 기점(order=1) 막차 출발시각과 정류소 순서·좌표뿐이라, 정류소 간
 * 소요시간을 하버사인 거리 ÷ 가정 평균속도로 직접 추정해서 누적하는 방식이다.
 * 실제 관측 데이터(5.3절 {@code BUS_NODE_PASSAGE})가 그 노선·정류소에 충분히 쌓이기
 * 전까지만 쓰는 임시값 — 정확한 평균속도 값은 미확정이라(10절) 생성자로 주입받는다.
 */
public class BusStopLastDepartureEstimator {

    private final double averageSpeedMetersPerSecond;

    public BusStopLastDepartureEstimator(double averageSpeedMetersPerSecond) {
        if (averageSpeedMetersPerSecond <= 0) {
            throw new IllegalArgumentException("평균 속도는 0보다 커야 합니다.");
        }
        this.averageSpeedMetersPerSecond = averageSpeedMetersPerSecond;
    }

    /**
     * @param stops 노선의 정류소들 (기점 order=1 포함 필수, targetOrder까지만 있어도 됨,
     *              순서 무관 — 내부에서 order 기준으로 정렬 후 사용)
     * @param targetOrder 통과 예정시각을 구하고 싶은 정류소의 order (기점=1이면 originLastDeparture 그대로 반환)
     */
    public LocalDateTime estimate(LocalDateTime originLastDeparture, List<RouteStop> stops, int targetOrder) {
        if (targetOrder < 1) {
            throw new IllegalArgumentException("targetOrder는 1(기점) 이상이어야 합니다: " + targetOrder);
        }

        List<RouteStop> upToTarget = stops.stream()
                .filter(s -> s.order() <= targetOrder)
                .sorted(Comparator.comparingInt(RouteStop::order))
                .toList();

        if (upToTarget.isEmpty() || upToTarget.get(0).order() != 1) {
            throw new IllegalArgumentException("정류소 목록에 기점(order=1)이 없습니다.");
        }
        RouteStop last = upToTarget.get(upToTarget.size() - 1);
        if (last.order() != targetOrder) {
            throw new IllegalArgumentException("targetOrder(" + targetOrder + ")에 해당하는 정류소가 목록에 없습니다.");
        }

        double cumulativeMeters = 0;
        for (int i = 1; i < upToTarget.size(); i++) {
            RouteStop from = upToTarget.get(i - 1);
            RouteStop to = upToTarget.get(i);
            cumulativeMeters += HaversineDistance.meters(
                    from.latitude(), from.longitude(), to.latitude(), to.longitude());
        }

        long cumulativeSeconds = Math.round(cumulativeMeters / averageSpeedMetersPerSecond);
        return originLastDeparture.plusSeconds(cumulativeSeconds);
    }
}
