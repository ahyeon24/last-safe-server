package com.lastsafe.external.odsay;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.calendar.OvernightTimeResolver;
import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.external.DayType;
import com.lastsafe.external.SubwayScheduleClient;

/**
 * ODsay {@code searchSubwaySchedule} 연동 (spec.md 3절, NFR-07).
 *
 * ⚠️ [2026-09-07 미검증] API 키가 아직 없어 실제 호출로 검증하지 못했다. 요청 파라미터
 * ({@code stationID}, {@code wayCode})와 응답 DTO({@link OdsaySubwayScheduleResponse})는
 * 공식 가이드 요약 기준 최선의 추정이다. **실제 키를 받으면 반드시 진짜 호출로 재확인할 것** —
 * TAGO/서울시 API도 같은 절차(추정 구현 → 실제 호출 재확인)를 여러 번 거쳐 확정됐다(CLAUDE.md
 * "실제 출시 전 반드시 처리해야 할 것" 참고). 그 전까지는 CLAUDE.md/spec.md에 "확정"으로
 * 올리지 말 것.
 *
 * 자정을 넘겨 이어지는 시각표(예: 05:30 ~ 익일 01:10)는 {@link OvernightTimeResolver
 * #resolveScheduleSequence}로 날짜를 넘긴다 — ODsay가 시각표를 운행 순서대로 정렬해서
 * 준다고 가정한다(미검증).
 */
@Service
public class OdsaySubwayScheduleClient implements SubwayScheduleClient {

    private static final String SEARCH_SUBWAY_SCHEDULE_PATH = "/searchSubwaySchedule";

    private final RestClient restClient;
    private final OdsayProperties properties;

    public OdsaySubwayScheduleClient(RestClient.Builder restClientBuilder, OdsayProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public LastDeparture findLastDeparture(String stationId, String direction, LocalDate serviceDate, DayType dayType) {
        List<LocalDateTime> times = fetchScheduleTimes(stationId, direction, serviceDate, dayType);
        return times.stream()
                .max(Comparator.naturalOrder())
                .map(LastDeparture::found)
                .orElseGet(LastDeparture::noService);
    }

    @Override
    public Optional<LocalDateTime> findLatestDepartureAtOrBefore(
            String stationId, String direction, LocalDateTime deadline, DayType dayType) {
        return fetchScheduleTimes(stationId, direction, deadline.toLocalDate(), dayType).stream()
                .filter(time -> !time.isAfter(deadline))
                .max(Comparator.naturalOrder());
    }

    private List<LocalDateTime> fetchScheduleTimes(
            String stationId, String direction, LocalDate serviceDate, DayType dayType) {
        OdsaySubwayScheduleResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(SEARCH_SUBWAY_SCHEDULE_PATH)
                        .queryParam("apiKey", properties.apiKey())
                        .queryParam("lang", 0)
                        .queryParam("output", "json")
                        .queryParam("stationID", stationId)
                        .queryParam("wayCode", direction)
                        .build())
                .retrieve()
                .body(OdsaySubwayScheduleResponse.class);

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "ODsay searchSubwaySchedule 응답이 비어있습니다 (stationId=" + stationId
                            + ") — 미검증 API라 정상 응답 형태를 아직 확정 못 함, 실제 응답을 보고 예외 처리를 다시 설계할 것.");
        }

        List<OdsaySubwayScheduleResponse.ScheduleEntry> entries = switch (dayType) {
            case WEEKDAY -> response.result().weekdaySchedule();
            case SATURDAY -> response.result().saturdaySchedule();
            case SUNDAY_OR_HOLIDAY -> response.result().holidaySchedule();
        };
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<LocalTime> orderedTimes = entries.stream()
                .map(entry -> LocalTime.parse(entry.time()))
                .toList();
        return OvernightTimeResolver.resolveScheduleSequence(serviceDate, orderedTimes);
    }
}
