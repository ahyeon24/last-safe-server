package com.lastsafe.external.tago;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.bus.BusStopLastDepartureEstimator;
import com.lastsafe.domain.bus.RouteStop;
import com.lastsafe.domain.calendar.OvernightTimeResolver;
import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.external.BusLastDepartureClient;
import com.lastsafe.external.DayType;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * TAGO(국토교통부) 버스노선정보조회 서비스 {@code getRouteInfoIem} +
 * {@code getRouteAcctoThrghSttnList} 연동 — spec.md 5.2절 "서울 외 지역 정류소별 막차 시각
 * 1단계 추정"의 실제 구현체. 기점 막차 출발시각(문서로 확인된 노선 전체 기준값)에 기점부터
 * 목표 정류소까지 하버사인 거리 ÷ 평균속도로 누적한 시간을 더해 추정한다
 * ({@link BusStopLastDepartureEstimator}에 위임 — 순수 계산 자체는 이미 구현·테스트 완료돼있어
 * 여기서 새로 만들 필요 없음, 이 클래스는 TAGO 응답을 그 계산기 입력으로 바꾸는 역할만 함).
 *
 * ⚠️ 실제 키로 호출해 동작 확인은 아직 안 함 — 문서와 실제 응답이 다를 가능성은 항상 있음.
 *
 * **dayType은 이 구현에서 쓰지 않는다** — spec.md 5.2절이 명시한 대로, `endvehicletime`
 * 자체는 요일 무관 고정값으로 가정하기 때문(배차간격만 요일별로 다름, 아직 미사용). 이건
 * "1단계(즉시 출시용) 계산식"의 알려진 단순화이지 실수가 아니다 — 5.3절 관측 데이터로
 * 대체되기 전까지의 임시 추정치.
 *
 * **findLatestDepartureAtOrBefore도 5.2절 특성상 근사치**: 이 API는 "그날의 배차 목록"이
 * 아니라 "노선의 막차 시각 1건"만 주므로, 진짜로 deadline 이하 중 가장 늦은 배차를 찾는 게
 * 아니라 추정 막차 시각이 deadline 이하인지만 확인한다 — 5.3절 `BUS_NODE_PASSAGE` 기반
 * 실제 구현체가 준비되면 그쪽이 이 근사를 대체한다(AssemblyTripSnapper의 busClient 슬롯은
 * 하나뿐이라, 지역/관측 여부별로 어떤 구현체를 쓸지 고르는 리졸버는 그 실제 구현체가 붙을 때
 * 함께 도입할 것 — spec.md 4절 "벤더 분기는 조립 계층 한 곳에만" 원칙).
 */
@Service
public class TagoBusLastDepartureClient implements BusLastDepartureClient {

    private static final String BASE_URL = "http://apis.data.go.kr/1613000/BusRouteInfoInqireService";
    private static final int PAGE_SIZE = 200; // 활용가이드 예시 totalCount=80 — 노선 하나의 정류소 수보다 넉넉하게 잡음

    private final RestClient restClient;
    private final DataGoKrProperties properties;
    private final BusStopLastDepartureEstimator estimator;

    public TagoBusLastDepartureClient(RestClient.Builder restClientBuilder, DataGoKrProperties properties,
                                       BusStopLastDepartureEstimator estimator) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.properties = properties;
        this.estimator = estimator;
    }

    @Override
    public LastDeparture findLastDeparture(
            String stationId, String routeId, String cityCode, LocalDate serviceDate, DayType dayType) {
        return estimateStopLastDeparture(stationId, routeId, cityCode, serviceDate)
                .map(LastDeparture::found)
                .orElseGet(LastDeparture::noService);
    }

    @Override
    public Optional<LocalDateTime> findLatestDepartureAtOrBefore(
            String stationId, String routeId, String cityCode, LocalDateTime deadline, DayType dayType) {
        return estimateStopLastDeparture(stationId, routeId, cityCode, deadline.toLocalDate())
                .filter(time -> !time.isAfter(deadline));
    }

    private Optional<LocalDateTime> estimateStopLastDeparture(
            String stationId, String routeId, String cityCode, LocalDate serviceDate) {
        TagoRouteInfoResponse.Item routeInfo = fetchRouteInfo(cityCode, routeId);
        List<TagoRouteStopsResponse.Item> stopItems = fetchAllStopPages(cityCode, routeId);

        Optional<TagoRouteStopsResponse.Item> targetStop = stopItems.stream()
                .filter(item -> item.nodeid().equals(stationId))
                .findFirst();
        if (targetStop.isEmpty()) {
            throw new IllegalStateException(
                    "TAGO 노선(routeId=" + routeId + ")에서 정류소(stationId=" + stationId + ")를 찾을 수 없습니다"
                            + " — 조립 계층이 잘못된 조합을 넘겼을 가능성.");
        }

        LocalDateTime originLastDeparture = OvernightTimeResolver.resolveLastDeparture(
                serviceDate, parseHhmm(routeInfo.startvehicletime()), parseHhmm(routeInfo.endvehicletime()));

        List<RouteStop> stops = stopItems.stream()
                .map(item -> new RouteStop(item.nodeord(), item.gpslati(), item.gpslong()))
                .toList();

        return Optional.of(estimator.estimate(originLastDeparture, stops, targetStop.get().nodeord()));
    }

    private TagoRouteInfoResponse.Item fetchRouteInfo(String cityCode, String routeId) {
        TagoRouteInfoResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getRouteInfoIem")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("_type", "json")
                        .queryParam("cityCode", cityCode)
                        .queryParam("routeId", routeId)
                        .build())
                .retrieve()
                .body(TagoRouteInfoResponse.class);

        if (response == null || response.response() == null || response.response().header() == null
                || !"00".equals(response.response().header().resultCode())
                || response.response().body() == null || response.response().body().items() == null
                || response.response().body().items().item() == null
                || response.response().body().items().item().isEmpty()) {
            throw new IllegalStateException("TAGO getRouteInfoIem 호출 실패 또는 결과 없음 (routeId=" + routeId + ")");
        }
        return response.response().body().items().item().get(0);
    }

    private List<TagoRouteStopsResponse.Item> fetchAllStopPages(String cityCode, String routeId) {
        List<TagoRouteStopsResponse.Item> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            TagoRouteStopsResponse.Body body = requestStopPage(cityCode, routeId, pageNo);
            List<TagoRouteStopsResponse.Item> items =
                    (body.items() == null || body.items().item() == null) ? List.of() : body.items().item();
            all.addAll(items);
            if (items.isEmpty() || all.size() >= body.totalCount()) {
                break;
            }
            pageNo++;
        }
        return all;
    }

    private TagoRouteStopsResponse.Body requestStopPage(String cityCode, String routeId, int pageNo) {
        TagoRouteStopsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getRouteAcctoThrghSttnList")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("_type", "json")
                        .queryParam("cityCode", cityCode)
                        .queryParam("routeId", routeId)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .body(TagoRouteStopsResponse.class);

        if (response == null || response.response() == null || response.response().header() == null
                || !"00".equals(response.response().header().resultCode())) {
            throw new IllegalStateException(
                    "TAGO getRouteAcctoThrghSttnList 호출 실패 (routeId=" + routeId + ")");
        }
        return response.response().body();
    }

    /** "HHMM" 4자리(예: "2230" = 22:30) — TAGO 활용가이드 항목설명 기준. */
    private static LocalTime parseHhmm(String hhmm) {
        int hour = Integer.parseInt(hhmm.substring(0, 2));
        int minute = Integer.parseInt(hhmm.substring(2, 4));
        return LocalTime.of(hour, minute);
    }
}
