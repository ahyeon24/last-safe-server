package com.lastsafe.external.tago;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.bus.VehiclePositionObservation;
import com.lastsafe.external.BusPositionClient;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * TAGO(국토교통부) 버스위치정보조회 서비스 {@code getRouteAcctoBusLcList} 연동 (spec.md 5.3절).
 *
 * 실제 활용가이드 문서(오픈API활용가이드_국토교통부(TAGO)_버스위치정보v1.0)로 파라미터/응답
 * 필드를 확인함 — {@code cityCode}가 필수 파라미터라는 점 주의(노선ID만으로는 조회 불가,
 * `getCtyCodeList`로 도시코드를 먼저 알아야 함 — 이 클라이언트는 호출자가 이미 안다고 가정).
 * ⚠️ 실제 키로 호출해 동작 확인은 아직 안 함 — 문서와 실제 응답이 다를 가능성은 항상 있음.
 *
 * 이 API는 실시간(10~20초 갱신) 스냅샷 하나만 준다 — "언제 관측했는지"는 TAGO가 안 주므로,
 * 이 클라이언트가 호출 시점의 시각을 그 폴링 전체(응답에 담긴 모든 차량)에 동일하게
 * 붙인다({@code com.lastsafe.entity.BusPollAttempt#collectedAt}과 같은 개념).
 */
@Service
public class TagoBusPositionClient implements BusPositionClient {

    private static final String PATH = "/getRouteAcctoBusLcList";
    private static final int PAGE_SIZE = 100; // 활용가이드 예시 totalCount=5 — 노선 하나의 동시 운행 차량 수보다 넉넉하게 잡음

    private final RestClient restClient;
    private final DataGoKrProperties properties;

    public TagoBusPositionClient(RestClient.Builder restClientBuilder, DataGoKrProperties properties) {
        this.restClient = restClientBuilder.baseUrl("http://apis.data.go.kr/1613000/BusLcInfoInqireService").build();
        this.properties = properties;
    }

    @Override
    public List<VehiclePositionObservation> findVehiclePositions(String cityCode, String routeId) {
        LocalDateTime observedAt = LocalDateTime.now();
        return fetchAllPages(cityCode, routeId).stream()
                .map(item -> new VehiclePositionObservation(item.vehicleno(), item.nodeord(), observedAt))
                .toList();
    }

    private List<TagoBusPositionResponse.Item> fetchAllPages(String cityCode, String routeId) {
        List<TagoBusPositionResponse.Item> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            TagoBusPositionResponse.Body body = requestPage(cityCode, routeId, pageNo);
            List<TagoBusPositionResponse.Item> items =
                    (body.items() == null || body.items().item() == null) ? List.of() : body.items().item();
            all.addAll(items);
            if (items.isEmpty() || all.size() >= body.totalCount()) {
                break;
            }
            pageNo++;
        }
        return all;
    }

    private TagoBusPositionResponse.Body requestPage(String cityCode, String routeId, int pageNo) {
        TagoBusPositionResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(PATH)
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("_type", "json")
                        .queryParam("cityCode", cityCode)
                        .queryParam("routeId", routeId)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .body(TagoBusPositionResponse.class);

        if (response == null || response.response() == null || response.response().header() == null
                || !"00".equals(response.response().header().resultCode())) {
            String resultMsg = (response == null || response.response() == null || response.response().header() == null)
                    ? "응답 없음" : response.response().header().resultMsg();
            throw new IllegalStateException(
                    "TAGO getRouteAcctoBusLcList 호출 실패 (routeId=" + routeId + ", resultMsg=" + resultMsg + ")");
        }
        return response.response().body();
    }
}
