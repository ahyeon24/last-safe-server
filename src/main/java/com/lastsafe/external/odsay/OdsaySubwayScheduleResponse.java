package com.lastsafe.external.odsay;

import java.util.List;

/**
 * ODsay {@code searchSubwaySchedule} 응답 (spec.md 3절).
 *
 * ⚠️ [2026-09-07 미검증] 아직 API 키가 없어 실제 호출로 확인하지 못했다 — 아래 필드명은 공식
 * 가이드 요약을 근거로 작성한 최선의 추정이다. 실제 키를 받으면 진짜 응답과 대조해서 다르면
 * 이 파일만 고치면 된다({@link OdsaySubwayScheduleClient} 밖으로는 이 클래스가 노출되지 않음).
 * TAGO/서울시 API도 처음엔 이렇게 추정으로 시작해서 실제 호출로 여러 번 재확인하며 확정했다
 * (CLAUDE.md 참고) — 같은 절차를 따르는 것.
 */
record OdsaySubwayScheduleResponse(Result result) {

    record Result(
            List<ScheduleEntry> weekdaySchedule,
            List<ScheduleEntry> saturdaySchedule,
            List<ScheduleEntry> holidaySchedule
    ) {
    }

    /**
     * time: "HH:mm" 형식으로 가정(미검증, "HHmm"일 가능성도 있음 — 서울시 API가
     * {@code firstBusTm}을 "HHMMSS"로 준 전례가 있어 ODsay도 구분자 없는 형식일 수 있음).
     * firstLastFlag: 1=첫차, 2=막차로 가정(미검증) — 이 클라이언트는 시각 자체로 최댓값을
     * 구하므로 이 필드에 의존하지 않는다.
     */
    record ScheduleEntry(String time, Integer firstLastFlag) {
    }
}
