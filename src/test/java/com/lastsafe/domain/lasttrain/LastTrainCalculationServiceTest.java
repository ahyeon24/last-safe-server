package com.lastsafe.domain.lasttrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lastsafe.entity.TransportType;

class LastTrainCalculationServiceTest {

    private final LastTrainCalculationService service = new LastTrainCalculationService();
    private final LocalDate day = LocalDate.of(2026, 9, 5);
    private final TripSnapper none = TripSnapper.none();

    private LocalDateTime at(int hour, int minute) {
        return day.atTime(hour, minute);
    }

    private LastTrainCalculationResult.Ok assertOk(LastTrainCalculationResult result) {
        assertThat(result).isInstanceOf(LastTrainCalculationResult.Ok.class);
        return (LastTrainCalculationResult.Ok) result;
    }

    /**
     * spec.md 6절 검증 예시.
     * 0 WALK 5분 / 1 SUBWAY 15분(막차 23:20) / 2 WALK 3분
     * / 3 BUS 20분(막차 23:50 추정, 버퍼 5분) / 4 WALK 4분
     * 기대값: min_departure_time_raw=23:15, min_departure_time=23:10, bottleneck=1(지하철)
     */
    @Test
    void 문서_검증_예시대로_계산된다() {
        List<RouteSegment> segments = List.of(
                new WalkSegment(300),
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0),
                new WalkSegment(180),
                new TransitSegment(TransportType.BUS, 1200, LastDeparture.found(at(23, 50)), true, 300),
                new WalkSegment(240)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(21, 0), none));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(23, 15));
        assertThat(result.minDepartureTime()).isEqualTo(at(23, 10));
        assertThat(result.bottleneckSegmentIndex()).isEqualTo(1);
        assertThat(result.isLastTrainGone()).isFalse();
    }

    @Test
    void 출발시각이_원본_막차시각_이후면_막차_전멸로_판정된다() {
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(23, 25), none));

        assertThat(result.isLastTrainGone()).isTrue();
    }

    @Test
    void implied_by_next가_채택되어도_병목_인덱스는_갱신되지_않는다() {
        // 뒤쪽(버스) 구간이 이미 병목으로 확정된 뒤, 앞쪽(지하철) 구간에서 implied_by_next가
        // 채택되더라도 병목은 뒤쪽 버스 구간(index 1)에 그대로 남아야 한다 — 재발 방지 포인트 1.
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(at(23, 0)), false, 0),
                new TransitSegment(TransportType.BUS, 600, LastDeparture.found(at(22, 0)), false, 0)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(20, 0), none));

        assertThat(result.bottleneckSegmentIndex()).isEqualTo(1);
        // implied_by_next = 22:00 - 10분 = 21:50 < 23:00 이므로 채택되어야 함
        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(21, 50));
    }

    @Test
    void 동률이면_구간_자체_막차가_병목으로_채택된다() {
        // implied_by_next(21:50)와 candidate(21:50)이 동일 — 부등호(<) 기준상 등호는 포함하지
        // 않으므로 구간 자체 막차(a)가 채택되고 병목도 이 구간(index 0)이어야 한다.
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(at(21, 50)), false, 0),
                new TransitSegment(TransportType.BUS, 600, LastDeparture.found(at(22, 0)), false, 0)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(20, 0), none));

        assertThat(result.bottleneckSegmentIndex()).isEqualTo(0);
    }

    @Test
    void 선행_도보시간은_순회_종료_후_최종_반영된다() {
        // WALK(집->역)가 첫 구간이면 역방향 순회에서는 가장 나중에 처리되어 다음 교통 구간이
        // 없다 — pendingWalkSec이 루프 종료 시점까지 소비되지 않고 남아있으므로, 루프가 끝난
        // 뒤 별도로 반영해야 한다(재발 방지 포인트 2).
        List<RouteSegment> segments = List.of(
                new WalkSegment(300),
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(21, 0), none));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(23, 15));
    }

    @Test
    void 하차_후_도보시간은_막차_계산에_영향을_주지_않는다() {
        // 마지막 교통 구간 뒤(하차 후)의 도보는 다음 교통편에 대한 제약이 없으므로,
        // 해당 교통 구간 처리 시 pendingWalkSec이 리셋되며 결과에 반영되지 않아야 한다.
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0),
                new WalkSegment(300)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(21, 0), none));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(23, 20));
    }

    @Test
    void NO_SERVICE는_해당_구간_인덱스와_함께_즉시_반환된다() {
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0),
                new TransitSegment(TransportType.BUS, 600, LastDeparture.noService(), true, 300)
        );

        LastTrainCalculationResult result = service.calculate(segments, at(21, 0), none);

        assertThat(result).isInstanceOf(LastTrainCalculationResult.NoService.class);
        assertThat(result.bottleneckSegmentIndex()).isEqualTo(1);
    }

    @Test
    void FETCH_ERROR는_NO_SERVICE와_분리되어_반환된다() {
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.fetchError(), false, 0)
        );

        LastTrainCalculationResult result = service.calculate(segments, at(21, 0), none);

        assertThat(result).isInstanceOf(LastTrainCalculationResult.FetchError.class);
        assertThat(result.bottleneckSegmentIndex()).isEqualTo(0);
    }

    @Test
    void 버퍼는_추정된_버스_구간에만_적용되어_합산된다() {
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0),
                new TransitSegment(TransportType.BUS, 600, LastDeparture.found(at(23, 40)), true, 300)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(21, 0), none));

        // 버스 구간이 병목이 아니므로 raw 시각은 지하철 막차(23:20) 기준으로 결정되고,
        // 버퍼(5분)는 버스가 추정 구간이므로 최종 min_departure_time에서만 차감된다.
        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(23, 20));
        assertThat(result.minDepartureTime()).isEqualTo(at(23, 15));
    }

    @Test
    void 전_구간이_도보면_예외가_발생한다() {
        List<RouteSegment> segments = List.of(new WalkSegment(300));

        assertThatThrownBy(() -> service.calculate(segments, at(21, 0), none))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 5.1절 스냅 ──────────────────────────────────────────────────────

    @Test
    void 스냅되면_해당_구간_후보가_실제_배차로_교체된다() {
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0)
        );
        TripSnapper snapper = (segment, deadline) -> new SnapResult.Snapped(at(23, 15));

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(21, 0), snapper));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(23, 15));
    }

    @Test
    void 스냅_데이터가_없으면_계산값을_그대로_사용한다() {
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0)
        );
        TripSnapper snapper = (segment, deadline) -> new SnapResult.ScheduleUnavailable();

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(21, 0), snapper));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(23, 20));
    }

    @Test
    void 데드라인_이하_실제_배차가_없으면_NO_SERVICE로_처리된다() {
        // 시간표는 있는데 데드라인 이하에 탈 수 있는 차가 없는 경우 — raw로 폴백하면
        // 존재하지 않는 배차를 탈 수 있는 것처럼 보여주는 버그가 되므로 경로 자체를 탈락시킨다.
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.SUBWAY, 900, LastDeparture.found(at(23, 20)), false, 0)
        );
        TripSnapper snapper = (segment, deadline) -> new SnapResult.NoTripBeforeDeadline();

        LastTrainCalculationResult result = service.calculate(segments, at(21, 0), snapper);

        assertThat(result).isInstanceOf(LastTrainCalculationResult.NoService.class);
        assertThat(result.bottleneckSegmentIndex()).isEqualTo(0);
    }

    @Test
    void 중간_구간의_스냅_결과가_앞쪽_구간_데드라인에_반영된다() {
        // 0 SUBWAY(막차 23:00) - WALK 5분 - 2 BUS(막차 23:50, 실제론 22:20/23:50만 운행,
        // 그 사이엔 차가 없음) - WALK 5분 - 4 SUBWAY(막차 23:10, 병목)
        //
        // 버스 구간의 계산상 후보(implied_by_next)는 22:45인데, 실제로는 그 시각에 차가
        // 없고 22:20이 그 이전 중 가장 늦은 실제 배차다. 첫 구간에서만 스냅했다면 이 22:45가
        // 그대로 앞쪽 지하철까지 전파돼 22:30이라는 틀린(너무 늦은) 값이 나왔을 것이다.
        TransitSegment subway0 = new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(at(23, 0)), false, 0);
        TransitSegment bus2 = new TransitSegment(TransportType.BUS, 1200, LastDeparture.found(at(23, 50)), false, 0);
        TransitSegment subway4 = new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(at(23, 10)), false, 0);

        List<RouteSegment> segments = List.of(subway0, new WalkSegment(300), bus2, new WalkSegment(300), subway4);

        TripSnapper snapper = (segment, deadline) -> {
            if (segment == bus2) {
                return new SnapResult.Snapped(at(22, 20));
            }
            return new SnapResult.ScheduleUnavailable();
        };

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(20, 0), snapper));

        // 스냅 없이 계산했다면(TripSnapper.none()) 22:30이 나왔을 것 — 아래 별도 테스트에서 대조.
        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(22, 5));
        assertThat(result.bottleneckSegmentIndex()).isEqualTo(4);
    }

    @Test
    void 막차가_자정을_넘어가도_순서가_뒤집히지_않는다() {
        // 막차가 다음날 00:10(자정 넘김)이어도 LocalDateTime으로 올바른 날짜가 들어오기만
        // 하면 비교 연산이 정상 동작한다는 걸 확인 — 실제 날짜 부여는 OvernightTimeResolver
        // (외부 클라이언트 구현 시점) 책임이고, 이 계산기 자체엔 별도 로직이 필요 없다.
        LocalDateTime lastDeparture = LocalDateTime.of(2026, 9, 8, 0, 10);
        List<RouteSegment> segments = List.of(
                new TransitSegment(TransportType.BUS, 900, LastDeparture.found(lastDeparture), false, 0)
        );

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(23, 50), none));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(lastDeparture);
        assertThat(result.isLastTrainGone()).isFalse(); // 23:50(당일)은 다음날 00:10보다 이르므로 안 늦음
    }

    @Test
    void 중간_구간을_스냅하지_않으면_실제보다_늦은_틀린_값이_나온다() {
        // 위 테스트와 같은 경로를 TripSnapper.none()으로 계산 — 버스의 실제 운행 공백을
        // 무시한 채 산술적으로만 역산해서, 실제로는 불가능한 22:30을 답으로 내놓는다.
        // (5.1절이 왜 "첫 구간만"이 아니라 "모든 구간"을 스냅해야 하는지 보여주는 회귀 테스트)
        TransitSegment subway0 = new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(at(23, 0)), false, 0);
        TransitSegment bus2 = new TransitSegment(TransportType.BUS, 1200, LastDeparture.found(at(23, 50)), false, 0);
        TransitSegment subway4 = new TransitSegment(TransportType.SUBWAY, 600, LastDeparture.found(at(23, 10)), false, 0);

        List<RouteSegment> segments = List.of(subway0, new WalkSegment(300), bus2, new WalkSegment(300), subway4);

        LastTrainCalculationResult.Ok result = assertOk(service.calculate(segments, at(20, 0), none));

        assertThat(result.minDepartureTimeRaw()).isEqualTo(at(22, 30));
    }
}
