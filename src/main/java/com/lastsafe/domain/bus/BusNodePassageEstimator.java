package com.lastsafe.domain.bus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TAGO 버스위치정보 폴링(10~20초 주기)으로 관심 정류소(순번 {@code targetNodeOrd})를
 * 특정 차량이 언제 통과했는지 추정한다 (spec.md 5.3절).
 *
 * 아이디어: 같은 차량의 연속된 두 관측 사이에서 정류소 순번이 targetNodeOrd 미만 →
 * targetNodeOrd 이상으로 바뀌는 지점을 찾는다. 폴링이 정확히 그 순번일 때 잡히면(운 좋게
 * 딱 맞춰 관측됨) 직접 관측(isObserved=true)이고, 건너뛰었으면(폴링 사이에 이미 지나감)
 * 직전·직후 관측 시각 사이로 보간(isObserved=false)한다.
 */
public final class BusNodePassageEstimator {

    private BusNodePassageEstimator() {
    }

    public static List<NodePassageEstimate> estimatePassages(
            List<VehiclePositionObservation> observations, int targetNodeOrd) {
        Map<String, List<VehiclePositionObservation>> byVehicle = observations.stream()
                .sorted(Comparator.comparing(VehiclePositionObservation::observedAt))
                .collect(Collectors.groupingBy(
                        VehiclePositionObservation::vehicleNo, LinkedHashMap::new, Collectors.toList()));

        List<NodePassageEstimate> result = new ArrayList<>();
        for (List<VehiclePositionObservation> vehicleObservations : byVehicle.values()) {
            for (int i = 1; i < vehicleObservations.size(); i++) {
                VehiclePositionObservation previous = vehicleObservations.get(i - 1);
                VehiclePositionObservation current = vehicleObservations.get(i);

                boolean crossedTarget = previous.nodeOrd() < targetNodeOrd && current.nodeOrd() >= targetNodeOrd;
                if (!crossedTarget) {
                    continue;
                }

                if (current.nodeOrd() == targetNodeOrd) {
                    result.add(new NodePassageEstimate(
                            current.vehicleNo(), current.observedAt(), current.observedAt(), true));
                } else {
                    result.add(new NodePassageEstimate(
                            current.vehicleNo(), previous.observedAt(), current.observedAt(), false));
                }
            }
        }
        result.sort(Comparator.comparing(NodePassageEstimate::passageTimeLower));
        return result;
    }
}
