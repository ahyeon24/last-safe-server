package com.lastsafe.domain.lasttrain;

import java.time.LocalDateTime;

/**
 * 알고리즘 실행 결과. 상태별로 의미 있는 필드가 다르기 때문에(NO_SERVICE/FETCH_ERROR일 때는
 * 시각·전멸 여부 자체가 존재하지 않음) enum + nullable 필드 대신 sealed interface로 분리한다.
 * 호출부는 switch 패턴 매칭으로 세 갈래를 전부 다뤄야 컴파일되므로, "상태 체크를 깜빡하고
 * null 필드에 접근"하는 실수가 구조적으로 불가능해진다.
 *
 * API 레벨 상태 코드로의 매핑(Ok->200, NoService->200 NO_ROUTE_AVAILABLE,
 * FetchError->503 EXTERNAL_API_UNAVAILABLE)은 이 레이어의 책임이 아니라 상위(API) 레이어에서 처리한다.
 */
public sealed interface LastTrainCalculationResult permits
        LastTrainCalculationResult.Ok,
        LastTrainCalculationResult.NoService,
        LastTrainCalculationResult.FetchError {

    int bottleneckSegmentIndex();

    record Ok(
            LocalDateTime minDepartureTimeRaw,
            LocalDateTime minDepartureTime,
            boolean isLastTrainGone,
            int bottleneckSegmentIndex
    ) implements LastTrainCalculationResult {
    }

    record NoService(int bottleneckSegmentIndex) implements LastTrainCalculationResult {
    }

    record FetchError(int bottleneckSegmentIndex) implements LastTrainCalculationResult {
    }
}
