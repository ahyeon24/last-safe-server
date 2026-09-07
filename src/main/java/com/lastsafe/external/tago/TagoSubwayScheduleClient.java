package com.lastsafe.external.tago;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.calendar.OvernightTimeResolver;
import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.external.DayType;
import com.lastsafe.external.SubwayScheduleClient;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * TAGO(국토교통부) 지하철정보 서비스 {@code GetSubwaySttnAcctoSchdulList} 연동 (spec.md 3절).
 *
 * [2026-09-07] ODsay → 서울교통공사 API → 이 TAGO API로 두 번 더 갈아탄 끝에 채택.
 * 실제 활용가이드 문서(오픈API활용가이드_국토교통부(TAGO)_지하철정보v1.1)로 파라미터/응답
 * 필드를 확인했다는 점에서 앞서 두 시도(공식 문서 요약 추정, UI 라벨 추정)보다 근거가 훨씬
 * 탄탄하다 — 그래도 **실제 키로 호출해 이 코드가 실제로 동작하는지는 아직 확인 못 했다**,
 * 문서와 실제 응답이 다를 가능성은 항상 있으니 키 받으면 꼭 재확인할 것.
 *
 * upDownTypeCode(U/D)를 {@link com.lastsafe.domain.lasttrain.TransitSegment#routeOrDirectionId}
 * 로 그대로 받는다 — 조립 계층이 "U" 또는 "D" 문자열을 넣어줘야 한다.
 *
 * 자정을 넘긴 시각표는 {@link OvernightTimeResolver#resolveUnorderedDailySchedule}로 처리한다
 * — TAGO가 정렬 순서를 보장한다는 문서 근거가 없어서, 순서에 의존하지 않고 심야 운행 공백을
 * 찾아 자정 넘김을 판별하는 방식을 쓴다.
 */
@Service
public class TagoSubwayScheduleClient implements SubwayScheduleClient {

    private static final String SCHEDULE_PATH = "/GetSubwaySttnAcctoSchdulList";
    // TAGO 활용가이드 예시의 totalCount가 257이었다 — 역 하나의 하루 배차 수보다 넉넉하게 잡아서
    // 대부분 한 페이지로 끝나게 함(페이지 초과 시 fetchAllPages가 이어서 더 가져옴).
    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final DataGoKrProperties properties;

    public TagoSubwayScheduleClient(RestClient.Builder restClientBuilder, DataGoKrProperties properties) {
        this.restClient = restClientBuilder.baseUrl("http://apis.data.go.kr/1613000/SubwayInfo").build();
        this.properties = properties;
    }

    @Override
    public LastDeparture findLastDeparture(String stationId, String direction, LocalDate serviceDate, DayType dayType) {
        return fetchScheduleTimes(stationId, direction, serviceDate, dayType).stream()
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
        String dailyTypeCode = toDailyTypeCode(dayType);
        List<LocalTime> times = fetchAllPages(stationId, direction, dailyTypeCode).stream()
                .map(item -> parseTagoTime(item.depTime()))
                .distinct()
                .toList();
        return OvernightTimeResolver.resolveUnorderedDailySchedule(serviceDate, times);
    }

    private List<TagoSubwayScheduleResponse.Item> fetchAllPages(
            String stationId, String direction, String dailyTypeCode) {
        List<TagoSubwayScheduleResponse.Item> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            TagoSubwayScheduleResponse.Body body = requestPage(stationId, direction, dailyTypeCode, pageNo);
            List<TagoSubwayScheduleResponse.Item> items =
                    (body.items() == null || body.items().item() == null) ? List.of() : body.items().item();
            all.addAll(items);
            if (items.isEmpty() || all.size() >= body.totalCount()) {
                break;
            }
            pageNo++;
        }
        return all;
    }

    private TagoSubwayScheduleResponse.Body requestPage(
            String stationId, String direction, String dailyTypeCode, int pageNo) {
        TagoSubwayScheduleResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(SCHEDULE_PATH)
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("_type", "json")
                        .queryParam("subwayStationId", stationId)
                        .queryParam("dailyTypeCode", dailyTypeCode)
                        .queryParam("upDownTypeCode", direction)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .body(TagoSubwayScheduleResponse.class);

        if (response == null || response.response() == null || response.response().header() == null
                || !"00".equals(response.response().header().resultCode())) {
            String resultMsg = (response == null || response.response() == null || response.response().header() == null)
                    ? "응답 없음" : response.response().header().resultMsg();
            throw new IllegalStateException(
                    "TAGO GetSubwaySttnAcctoSchdulList 호출 실패 (stationId=" + stationId
                            + ", resultMsg=" + resultMsg + ")");
        }
        return response.response().body();
    }

    private static String toDailyTypeCode(DayType dayType) {
        return switch (dayType) {
            case WEEKDAY -> "01";
            case SATURDAY -> "02";
            case SUNDAY_OR_HOLIDAY -> "03";
        };
    }

    /** "HHMISS" 6자리(예: "051930" = 05:19:30) — TAGO 활용가이드 항목설명 기준. */
    private static LocalTime parseTagoTime(String hhmiss) {
        int hour = Integer.parseInt(hhmiss.substring(0, 2));
        int minute = Integer.parseInt(hhmiss.substring(2, 4));
        int second = Integer.parseInt(hhmiss.substring(4, 6));
        return LocalTime.of(hour, minute, second);
    }
}
