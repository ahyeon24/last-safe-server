package com.lastsafe.domain.lasttrain;

import java.time.LocalDateTime;

/**
 * {@link TripSnapper} 조회 결과 (spec.md 5.1절). "못 찾음"을 두 가지로 반드시 구분한다 —
 * 시간표 데이터 자체가 없는 것(관측 부족 등)과, 시간표는 있는데 데드라인 이하 배차가 없는 것은
 * 완전히 다른 상황이다. 전자는 스냅 없이 계산값을 그대로 쓰는 폴백이 맞지만, 후자를 똑같이
 * 취급하면 실제로는 존재하지 않는 배차를 탈 수 있는 것처럼 안내하는 버그가 된다.
 */
public sealed interface SnapResult permits
        SnapResult.Snapped, SnapResult.ScheduleUnavailable, SnapResult.NoTripBeforeDeadline {

    record Snapped(LocalDateTime time) implements SnapResult {
    }

    /** 시간표 데이터 자체를 못 구함 — 스냅 없이 원래 계산값 그대로 사용(폴백 가능). */
    record ScheduleUnavailable() implements SnapResult {
    }

    /** 시간표는 있는데 데드라인 이하 실제 배차가 없음 — 이 경로 자체가 불가능함(폴백 불가). */
    record NoTripBeforeDeadline() implements SnapResult {
    }
}
