package com.lastsafe.domain.lasttrain;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lastsafe.entity.TransportType;

/**
 * 막차 역산 알고리즘 (docs/spec.md 5절, 5.1절).
 *
 * 경로 구간을 역방향(목적지→출발지)으로 순회하며, 각 교통 구간에서
 * "그 구간 자체 막차 시각"과 "다음 구간이 요구하는 시각에서 역산한 값" 중
 * 더 이른 쪽을 채택한다. 이 지점이 병목 구간(FR-04)이 된다.
 *
 * v0.1에서 발생했던 버그 재발 방지 포인트 (spec.md 5절 참고):
 * 1. implied_by_next가 채택돼도 bottleneck_index는 갱신하지 않는다 (이미 확정된 더 뒤쪽
 *    구간의 병목이 진짜 병목).
 * 2. WALK 구간 소요시간은 즉시 차감하지 않고 pendingWalkSec에 누적했다가 다음 교통
 *    구간에서 반영한다. 경로가 WALK로 끝나면 순회 종료 후 남은 값을 최종 반영해야 한다.
 * 3. NO_SERVICE(막차 없음, 정상 도메인 결과)와 FETCH_ERROR(조회 실패, 시스템 오류)는
 *    반드시 분리 처리한다.
 *
 * 5.1절 스냅: 각 교통 구간의 candidate가 확정되는 즉시(다음 구간으로 전파되기 전에)
 * {@link TripSnapper}로 실제 배차에 맞춰 보정한다. 첫 구간만 후처리로 스냅하면, 중간
 * 구간에서 implied_by_next로 채택된 값(실제 배차가 아닐 수 있음)이 그대로 앞 구간까지
 * 전파돼 틀린 값을 오염시킬 수 있어서 — 모든 교통 구간에서, 전파 직전에 스냅해야 한다.
 * 이렇게 하면 "첫 구간 탑승 데드라인이 도보시간만큼 어긋나는" 문제도 자연히 해결된다 —
 * candidate는 항상 그 구간 자체의 탑승 시각 기준이고, 도보시간 차감은 그 이후에 일어나기
 * 때문이다. 스냅이 필요 없으면(순수 계산만 검증할 때) {@link TripSnapper#none()}을 넘긴다.
 */
@Service
public class LastTrainCalculationService {

    public LastTrainCalculationResult calculate(
            List<RouteSegment> segments, LocalDateTime departureDatetime, TripSnapper tripSnapper) {
        boolean hasTransitSegment = segments.stream().anyMatch(s -> s instanceof TransitSegment);
        if (!hasTransitSegment) {
            throw new IllegalArgumentException(
                    "전 구간이 WALK인 경로는 막차 계산 대상이 아닙니다 — 호출 전에 걸러내야 합니다.");
        }

        LocalDateTime requiredArrivalTime = null;
        long pendingWalkSec = 0;
        int bottleneckSegmentIndex = -1;

        for (int i = segments.size() - 1; i >= 0; i--) {
            RouteSegment segment = segments.get(i);

            if (segment instanceof WalkSegment walk) {
                pendingWalkSec += walk.durationSec();
                continue;
            }

            TransitSegment transit = (TransitSegment) segment;
            LastDeparture lastDeparture = transit.lastDeparture();

            if (lastDeparture.status() == LastDepartureStatus.NO_SERVICE) {
                return new LastTrainCalculationResult.NoService(i);
            }
            if (lastDeparture.status() == LastDepartureStatus.FETCH_ERROR) {
                return new LastTrainCalculationResult.FetchError(i);
            }

            LocalDateTime candidate = lastDeparture.time();

            if (requiredArrivalTime != null) {
                LocalDateTime impliedByNext = requiredArrivalTime
                        .minusSeconds(pendingWalkSec)
                        .minusSeconds(transit.durationSec());

                if (impliedByNext.isBefore(candidate)) {
                    candidate = impliedByNext;
                    // bottleneckSegmentIndex 갱신하지 않음 — 재발 방지 포인트 1
                } else {
                    bottleneckSegmentIndex = i;
                }
            } else {
                bottleneckSegmentIndex = i;
            }

            // 5.1절: 다음(앞쪽) 구간으로 전파되기 전에 스냅 — implied_by_next로 채택된
            // candidate는 실제 배차가 아닐 수 있으므로, 여기서 보정 안 하면 틀린 값이 그대로
            // 전파된다.
            SnapResult snapResult = tripSnapper.findLatestTripAtOrBefore(transit, candidate);
            if (snapResult instanceof SnapResult.Snapped snapped) {
                candidate = snapped.time();
            } else if (snapResult instanceof SnapResult.NoTripBeforeDeadline) {
                return new LastTrainCalculationResult.NoService(i);
            }
            // ScheduleUnavailable이면 candidate를 그대로 둔다(폴백).

            requiredArrivalTime = candidate;
            pendingWalkSec = 0;
        }

        if (requiredArrivalTime == null) {
            // hasTransitSegment 확인을 통과했다면 루프에서 TransitSegment를 최소 한 번은
            // 처리했어야 하므로(그 순간 requiredArrivalTime이 채워짐), 여기 도달하면 이
            // 메서드 자체의 로직 버그다. NPE로 조용히 죽는 대신 원인을 바로 알 수 있게 한다.
            throw new IllegalStateException("requiredArrivalTime이 채워지지 않았습니다 — 알고리즘 구현 버그입니다.");
        }

        // 재발 방지 포인트 2: 경로가 WALK로 끝나는 경우 남은 pendingWalkSec을 여기서 최종 반영
        LocalDateTime minDepartureTimeRaw = requiredArrivalTime.minusSeconds(pendingWalkSec);
        boolean isLastTrainGone = departureDatetime.isAfter(minDepartureTimeRaw);

        long totalBufferSec = segments.stream()
                .filter(s -> s instanceof TransitSegment t && t.type() == TransportType.BUS && t.isEstimated())
                .mapToLong(s -> ((TransitSegment) s).bufferSec())
                .sum();

        LocalDateTime minDepartureTime = minDepartureTimeRaw.minusSeconds(totalBufferSec);

        return new LastTrainCalculationResult.Ok(
                minDepartureTimeRaw, minDepartureTime, isLastTrainGone, bottleneckSegmentIndex);
    }
}
