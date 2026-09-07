package com.lastsafe.domain.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class BusNodePassageEstimatorTest {

    private static LocalDateTime at(int hour, int minute, int second) {
        return LocalDateTime.of(2026, 9, 7, hour, minute, second);
    }

    @Test
    void 폴링이_목표_순번을_건너뛰면_직전_직후_관측_사이로_보간한다() {
        // 목표 정류소 순번 5인데, 폴링 사이에 3 -> 6으로 건너뜀
        List<VehiclePositionObservation> observations = List.of(
                new VehiclePositionObservation("서울70사1234", 3, at(23, 50, 0)),
                new VehiclePositionObservation("서울70사1234", 6, at(23, 50, 15)));

        List<NodePassageEstimate> result = BusNodePassageEstimator.estimatePassages(observations, 5);

        assertThat(result).containsExactly(
                new NodePassageEstimate("서울70사1234", at(23, 50, 0), at(23, 50, 15), false));
    }

    @Test
    void 폴링이_목표_순번에_정확히_맞으면_직접_관측으로_기록한다() {
        List<VehiclePositionObservation> observations = List.of(
                new VehiclePositionObservation("서울70사1234", 4, at(23, 50, 0)),
                new VehiclePositionObservation("서울70사1234", 5, at(23, 50, 15)));

        List<NodePassageEstimate> result = BusNodePassageEstimator.estimatePassages(observations, 5);

        assertThat(result).containsExactly(
                new NodePassageEstimate("서울70사1234", at(23, 50, 15), at(23, 50, 15), true));
    }

    @Test
    void 목표_순번에_도달하지_못하면_아무_결과도_없다() {
        List<VehiclePositionObservation> observations = List.of(
                new VehiclePositionObservation("서울70사1234", 1, at(23, 50, 0)),
                new VehiclePositionObservation("서울70사1234", 2, at(23, 50, 15)));

        assertThat(BusNodePassageEstimator.estimatePassages(observations, 5)).isEmpty();
    }

    @Test
    void 첫_관측이_이미_목표_순번_이후면_통과_시점을_추정하지_않는다() {
        // 비교할 "직전" 관측이 없어서 언제 지나갔는지 알 방법이 없음 — 억지로 추정하지 않음
        List<VehiclePositionObservation> observations = List.of(
                new VehiclePositionObservation("서울70사1234", 7, at(23, 50, 0)),
                new VehiclePositionObservation("서울70사1234", 8, at(23, 50, 15)));

        assertThat(BusNodePassageEstimator.estimatePassages(observations, 5)).isEmpty();
    }

    @Test
    void 여러_차량이_섞여있어도_차량별로_구분해서_추정한다() {
        List<VehiclePositionObservation> observations = List.of(
                new VehiclePositionObservation("A차량", 3, at(23, 0, 0)),
                new VehiclePositionObservation("B차량", 4, at(23, 0, 5)),
                new VehiclePositionObservation("A차량", 6, at(23, 0, 15)),
                new VehiclePositionObservation("B차량", 5, at(23, 0, 20)));

        List<NodePassageEstimate> result = BusNodePassageEstimator.estimatePassages(observations, 5);

        assertThat(result).containsExactlyInAnyOrder(
                new NodePassageEstimate("A차량", at(23, 0, 0), at(23, 0, 15), false),
                new NodePassageEstimate("B차량", at(23, 0, 20), at(23, 0, 20), true));
    }

    @Test
    void 입력_순서가_시간순이_아니어도_정렬해서_처리한다() {
        List<VehiclePositionObservation> observations = List.of(
                new VehiclePositionObservation("A차량", 6, at(23, 0, 15)),
                new VehiclePositionObservation("A차량", 3, at(23, 0, 0)));

        List<NodePassageEstimate> result = BusNodePassageEstimator.estimatePassages(observations, 5);

        assertThat(result).containsExactly(
                new NodePassageEstimate("A차량", at(23, 0, 0), at(23, 0, 15), false));
    }
}
