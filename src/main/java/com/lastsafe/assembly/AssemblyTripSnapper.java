package com.lastsafe.assembly;

import java.time.LocalDateTime;
import java.util.Optional;

import com.lastsafe.domain.calendar.DayTypeResolver;
import com.lastsafe.domain.lasttrain.SnapResult;
import com.lastsafe.domain.lasttrain.TransitSegment;
import com.lastsafe.domain.lasttrain.TripSnapper;
import com.lastsafe.entity.TransportType;
import com.lastsafe.external.BusLastDepartureClient;
import com.lastsafe.external.DayType;
import com.lastsafe.external.SubwayScheduleClient;

/**
 * {@link TripSnapper}의 실제 구현체 — spec.md 4절 "벤더별 분기는 조립 계층 한 곳에만" 원칙에 따라,
 * SUBWAY는 {@link SubwayScheduleClient}, BUS는 {@link BusLastDepartureClient}로 위임하는 분기가
 * 이 클래스 하나에만 있다. {@link com.lastsafe.domain.lasttrain.LastTrainCalculationService}는
 * 이 클래스의 존재도, ODsay/서울시/TAGO라는 이름도 모른다.
 *
 * 클라이언트를 {@link Optional}로 받는 이유: ODsay/서울시/TAGO 실연동이 아직 없는 상태에서도
 * 이 클래스를 안전하게 만들어 쓸 수 있어야 하기 때문. 클라이언트가 비어있으면 스냅을 시도하지
 * 않고 {@link SnapResult.ScheduleUnavailable}로 폴백해 계산값을 그대로 통과시킨다 —
 * {@link DayTypeResolver}가 {@link com.lastsafe.external.HolidayClient} 없이 {@code @Service}로
 * 등록되지 않는 것과 같은 이유로, 이 클래스도 아직 {@code @Service}로 등록하지 않는다. 실 클라이언트
 * 구현체를 만들 때 조립 설정(빈 등록)에서 이 클래스도 같이 등록할 것.
 *
 * BUS는 spec.md 5.1절 미해결 사항대로, 5.3절 관측 데이터(BUS_NODE_PASSAGE) 기반 실 클라이언트가
 * 붙기 전까지는 busClient가 비어있는 채로 운영될 것으로 예상된다 — 그동안은 5.2절 추정치
 * (candidate 자체가 이미 추정값)가 스냅 없이 그대로 통과된다. 서울/TAGO 등 BUS 구현체가 지역별로
 * 여러 개가 될 때의 리졸버/팩토리는 그 구현체들을 실제로 만들 때 함께 도입한다 — 지금은 구현체가
 * 하나도 없어 미리 만들어봤자 근거 없는 추상화가 된다.
 */
public class AssemblyTripSnapper implements TripSnapper {

    private final Optional<SubwayScheduleClient> subwayClient;
    private final Optional<BusLastDepartureClient> busClient;
    private final DayTypeResolver dayTypeResolver;

    public AssemblyTripSnapper(Optional<SubwayScheduleClient> subwayClient,
                                Optional<BusLastDepartureClient> busClient,
                                DayTypeResolver dayTypeResolver) {
        this.subwayClient = subwayClient;
        this.busClient = busClient;
        this.dayTypeResolver = dayTypeResolver;
    }

    @Override
    public SnapResult findLatestTripAtOrBefore(TransitSegment segment, LocalDateTime deadline) {
        DayType dayType = dayTypeResolver.resolveForDateTime(deadline);

        if (segment.type() == TransportType.SUBWAY) {
            if (subwayClient.isEmpty()) {
                return new SnapResult.ScheduleUnavailable();
            }
            return toSnapResult(subwayClient.get().findLatestDepartureAtOrBefore(
                    segment.stationId(), segment.routeOrDirectionId(), deadline, dayType));
        }

        if (busClient.isEmpty()) {
            return new SnapResult.ScheduleUnavailable();
        }
        return toSnapResult(busClient.get().findLatestDepartureAtOrBefore(
                segment.stationId(), segment.routeOrDirectionId(), segment.cityCode(), deadline, dayType));
    }

    private SnapResult toSnapResult(Optional<LocalDateTime> latestTripAtOrBefore) {
        return latestTripAtOrBefore
                .<SnapResult>map(SnapResult.Snapped::new)
                .orElseGet(SnapResult.NoTripBeforeDeadline::new);
    }
}
