package com.lastsafe.domain.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class BusStopLastDepartureEstimatorTest {

    private static final double SPEED_MPS = 10.0; // 테스트용 임의 평균속도(시속 36km) — 실제값은 spec.md 10절 미확정
    private final BusStopLastDepartureEstimator estimator = new BusStopLastDepartureEstimator(SPEED_MPS);

    private final List<RouteStop> stops = List.of(
            new RouteStop(1, 37.00, 127.00),
            new RouteStop(2, 37.01, 127.00),
            new RouteStop(3, 37.02, 127.00)
    );

    @Test
    void 기점_자체의_추정시각은_기점_막차시각과_같다() {
        LocalDateTime origin = LocalDateTime.of(2026, 9, 7, 23, 0);

        assertThat(estimator.estimate(origin, stops, 1)).isEqualTo(origin);
    }

    @Test
    void 구간별_하버사인_거리를_누적해서_추정시각을_계산한다() {
        LocalDateTime origin = LocalDateTime.of(2026, 9, 7, 23, 0);
        double hopMeters = HaversineDistance.meters(37.00, 127.00, 37.01, 127.00);
        long expectedSecondsToStop3 = Math.round(2 * hopMeters / SPEED_MPS);

        LocalDateTime result = estimator.estimate(origin, stops, 3);

        assertThat(result).isEqualTo(origin.plusSeconds(expectedSecondsToStop3));
    }

    @Test
    void targetOrder가_1보다_작으면_예외가_발생한다() {
        assertThatThrownBy(() -> estimator.estimate(LocalDateTime.now(), stops, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 목록에_기점이_없으면_예외가_발생한다() {
        List<RouteStop> noOrigin = List.of(new RouteStop(2, 37.01, 127.00));

        assertThatThrownBy(() -> estimator.estimate(LocalDateTime.now(), noOrigin, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetOrder에_해당하는_정류소가_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> estimator.estimate(LocalDateTime.now(), stops, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 평균속도가_0이하면_생성자에서_예외가_발생한다() {
        assertThatThrownBy(() -> new BusStopLastDepartureEstimator(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
