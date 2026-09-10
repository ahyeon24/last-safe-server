package com.lastsafe.domain.route;

import java.util.List;

/**
 * ODsay {@code searchPubTransPathT}가 준 경로 후보 1개 (spec.md 3절). {@code /routes/search}
 * 응답의 후보 하나에 대응하는 개념이지만, 아직 {@code candidate_id} 캐싱·요금/버퍼 계산 등은
 * 조립 계층에서 이어서 처리해야 한다 — 이 레코드는 ODsay 원본을 벤더 중립적으로 옮긴 것뿐.
 */
public record PathCandidate(int totalDurationSec, int payment, List<PathLeg> legs) {
}
