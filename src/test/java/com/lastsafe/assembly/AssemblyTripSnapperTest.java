package com.lastsafe.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.lastsafe.domain.calendar.DayTypeResolver;
import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.domain.lasttrain.SnapResult;
import com.lastsafe.domain.lasttrain.TransitSegment;
import com.lastsafe.entity.TransportType;
import com.lastsafe.external.BusLastDepartureClient;
import com.lastsafe.external.DayType;
import com.lastsafe.external.HolidayClient;
import com.lastsafe.external.SubwayScheduleClient;

class AssemblyTripSnapperTest {

    private static final HolidayClient NO_HOLIDAYS = date -> false;
    private static final DayTypeResolver DAY_TYPE_RESOLVER = new DayTypeResolver(NO_HOLIDAYS);

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 9, 7, hour, minute); // 2026-09-07은 월요일(WEEKDAY)
    }

    private static TransitSegment subwaySegment(LocalDateTime candidateLastDeparture) {
        return new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(candidateLastDeparture),
                false, 0, "stationA", "up");
    }

    private static TransitSegment busSegment(LocalDateTime candidateLastDeparture) {
        return new TransitSegment(TransportType.BUS, 600, LastDeparture.found(candidateLastDeparture),
                true, 300, "stopA", "routeA", "25");
    }

    @Test
    void 클라이언트가_비어있으면_스냅을_시도하지_않고_ScheduleUnavailable을_반환한다() {
        AssemblyTripSnapper snapper = new AssemblyTripSnapper(
                Optional.empty(), Optional.empty(), DAY_TYPE_RESOLVER);

        SnapResult result = snapper.findLatestTripAtOrBefore(subwaySegment(at(23, 7)), at(23, 7));

        assertThat(result).isInstanceOf(SnapResult.ScheduleUnavailable.class);
    }

    @Test
    void SUBWAY는_실제_배차가_있으면_Snapped를_반환한다() {
        SubwayScheduleClient fakeClient = new SubwayScheduleClient() {
            @Override
            public LastDeparture findLastDeparture(String stationId, String direction, LocalDate serviceDate, DayType dayType) {
                throw new UnsupportedOperationException("이 테스트에선 사용 안 함");
            }

            @Override
            public Optional<LocalDateTime> findLatestDepartureAtOrBefore(
                    String stationId, String direction, LocalDateTime deadline, DayType dayType) {
                return Optional.of(at(23, 5)); // 데드라인(23:07) 이하 실제 배차
            }
        };
        AssemblyTripSnapper snapper = new AssemblyTripSnapper(
                Optional.of(fakeClient), Optional.empty(), DAY_TYPE_RESOLVER);

        SnapResult result = snapper.findLatestTripAtOrBefore(subwaySegment(at(23, 7)), at(23, 7));

        assertThat(result).isEqualTo(new SnapResult.Snapped(at(23, 5)));
    }

    @Test
    void SUBWAY는_데드라인_이하_배차가_없으면_NoTripBeforeDeadline을_반환한다() {
        SubwayScheduleClient fakeClient = new SubwayScheduleClient() {
            @Override
            public LastDeparture findLastDeparture(String stationId, String direction, LocalDate serviceDate, DayType dayType) {
                throw new UnsupportedOperationException("이 테스트에선 사용 안 함");
            }

            @Override
            public Optional<LocalDateTime> findLatestDepartureAtOrBefore(
                    String stationId, String direction, LocalDateTime deadline, DayType dayType) {
                return Optional.empty();
            }
        };
        AssemblyTripSnapper snapper = new AssemblyTripSnapper(
                Optional.of(fakeClient), Optional.empty(), DAY_TYPE_RESOLVER);

        SnapResult result = snapper.findLatestTripAtOrBefore(subwaySegment(at(23, 7)), at(23, 7));

        assertThat(result).isInstanceOf(SnapResult.NoTripBeforeDeadline.class);
    }

    @Test
    void BUS는_busClient가_비어있으면_추정치를_그대로_통과시킨다() {
        // spec.md 5.1절: 5.3절 관측 데이터가 붙기 전까지 BUS는 대부분 ScheduleUnavailable로
        // 처리되는 게 의도된 동작 — busClient를 비워둔 채로도 이 클래스는 정상 동작해야 한다.
        AssemblyTripSnapper snapper = new AssemblyTripSnapper(
                Optional.empty(), Optional.empty(), DAY_TYPE_RESOLVER);

        SnapResult result = snapper.findLatestTripAtOrBefore(busSegment(at(22, 45)), at(22, 45));

        assertThat(result).isInstanceOf(SnapResult.ScheduleUnavailable.class);
    }

    @Test
    void BUS는_busClient가_있으면_SUBWAY와_동일하게_실제_배차로_스냅된다() {
        BusLastDepartureClient fakeClient = new BusLastDepartureClient() {
            @Override
            public LastDeparture findLastDeparture(
                    String stationId, String routeId, String cityCode, LocalDate serviceDate, DayType dayType) {
                throw new UnsupportedOperationException("이 테스트에선 사용 안 함");
            }

            @Override
            public Optional<LocalDateTime> findLatestDepartureAtOrBefore(
                    String stationId, String routeId, String cityCode, LocalDateTime deadline, DayType dayType) {
                return Optional.of(at(22, 20));
            }
        };
        AssemblyTripSnapper snapper = new AssemblyTripSnapper(
                Optional.empty(), Optional.of(fakeClient), DAY_TYPE_RESOLVER);

        SnapResult result = snapper.findLatestTripAtOrBefore(busSegment(at(22, 45)), at(22, 45));

        assertThat(result).isEqualTo(new SnapResult.Snapped(at(22, 20)));
    }
}
