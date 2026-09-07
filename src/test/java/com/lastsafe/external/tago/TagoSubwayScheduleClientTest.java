package com.lastsafe.external.tago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.lasttrain.LastDeparture;
import com.lastsafe.external.DayType;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * ⚠️ 이 테스트는 "TAGO 활용가이드 문서에 적힌 형태를 우리 DTO가 올바르게 파싱하는가"를
 * 검증한다. 문서와 실제 응답이 다를 가능성은 남아있음 — {@link TagoSubwayScheduleClient}
 * 상단 주석 참고.
 *
 * 시각표는 낮 동안 2시간 간격(TAGO 예시처럼 촘촘한 실제 배차를 흉내)으로 배치해서, 01:00~05:30
 * 심야 공백이 다른 어떤 배차 간격보다 뚜렷하게 크도록 구성했다 — {@code OvernightTimeResolver
 * #resolveUnorderedDailySchedule}의 "가장 긴 공백" 판별이 우연이 아니라 실제로 유효하게.
 */
class TagoSubwayScheduleClientTest {

    private static final String OVERNIGHT_SCHEDULE_JSON = """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                "body": {
                  "items": {"item": [
                    {"depTime": "053000", "arrTime": "053030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "073000", "arrTime": "073030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "093000", "arrTime": "093030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "113000", "arrTime": "113030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "133000", "arrTime": "133030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "153000", "arrTime": "153030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "173000", "arrTime": "173030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "193000", "arrTime": "193030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "213000", "arrTime": "213030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "235000", "arrTime": "235030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "001000", "arrTime": "001030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                    {"depTime": "010000", "arrTime": "010030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"}
                  ]},
                  "numOfRows": 500,
                  "pageNo": 1,
                  "totalCount": 12
                }
              }
            }
            """;

    private MockRestServiceServer server;
    private TagoSubwayScheduleClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DataGoKrProperties properties = new DataGoKrProperties("test-key");
        client = new TagoSubwayScheduleClient(builder, properties);
    }

    @Test
    void 자정_넘긴_시각표에서_막차를_찾는다() {
        setUp();
        server.expect(requestTo(containsString("/GetSubwaySttnAcctoSchdulList")))
                .andExpect(queryParam("subwayStationId", "MTRS11133"))
                .andExpect(queryParam("dailyTypeCode", "01"))
                .andExpect(queryParam("upDownTypeCode", "D"))
                .andRespond(withSuccess(OVERNIGHT_SCHEDULE_JSON, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "MTRS11133", "D", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        assertThat(result).isEqualTo(LastDeparture.found(LocalDateTime.of(2026, 9, 8, 1, 0, 0)));
    }

    @Test
    void 데드라인_이하_배차_중_가장_늦은_시각을_찾는다() {
        setUp();
        server.expect(requestTo(containsString("/GetSubwaySttnAcctoSchdulList")))
                .andExpect(queryParam("subwayStationId", "MTRS11133"))
                .andRespond(withSuccess(OVERNIGHT_SCHEDULE_JSON, MediaType.APPLICATION_JSON));

        Optional<LocalDateTime> result = client.findLatestDepartureAtOrBefore(
                "MTRS11133", "D", LocalDateTime.of(2026, 9, 7, 23, 55), DayType.WEEKDAY);

        assertThat(result).contains(LocalDateTime.of(2026, 9, 7, 23, 50, 0));
    }

    @Test
    void 결과가_없으면_NO_SERVICE를_반환한다() {
        setUp();
        String emptyJson = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {"items": null, "numOfRows": 500, "pageNo": 1, "totalCount": 0}
                  }
                }
                """;
        server.expect(requestTo(containsString("/GetSubwaySttnAcctoSchdulList")))
                .andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "MTRS99999", "D", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        assertThat(result).isEqualTo(LastDeparture.noService());
    }

    @Test
    void 결과가_한건이면_배열이_아니라_단일_객체로_와도_처리한다() {
        setUp();
        // 공공데이터포털 API의 흔한 특성 — 결과 1건일 때 item이 배열이 아니라 객체로 옴
        String singleItemJson = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": {"depTime": "051930", "arrTime": "052000", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"}},
                      "numOfRows": 500,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        server.expect(requestTo(containsString("/GetSubwaySttnAcctoSchdulList")))
                .andRespond(withSuccess(singleItemJson, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "MTRS11133", "D", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        assertThat(result).isEqualTo(LastDeparture.found(LocalDateTime.of(2026, 9, 7, 5, 19, 30)));
    }

    @Test
    void 페이지가_나뉘어도_totalCount까지_전부_모아서_처리한다() {
        setUp();
        String page1Json = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": [
                        {"depTime": "053000", "arrTime": "053030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "073000", "arrTime": "073030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "093000", "arrTime": "093030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "113000", "arrTime": "113030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "133000", "arrTime": "133030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "153000", "arrTime": "153030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"}
                      ]},
                      "numOfRows": 6,
                      "pageNo": 1,
                      "totalCount": 12
                    }
                  }
                }
                """;
        String page2Json = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": [
                        {"depTime": "173000", "arrTime": "173030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "193000", "arrTime": "193030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "213000", "arrTime": "213030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "235000", "arrTime": "235030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "001000", "arrTime": "001030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"},
                        {"depTime": "010000", "arrTime": "010030", "dailyTypeCode": "01", "upDownTypeCode": "D", "subwayStationId": "MTRS11133", "subwayRouteId": "MTRS11"}
                      ]},
                      "numOfRows": 6,
                      "pageNo": 2,
                      "totalCount": 12
                    }
                  }
                }
                """;
        server.expect(requestTo(containsString("/GetSubwaySttnAcctoSchdulList")))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/GetSubwaySttnAcctoSchdulList")))
                .andExpect(queryParam("pageNo", "2"))
                .andRespond(withSuccess(page2Json, MediaType.APPLICATION_JSON));

        LastDeparture result = client.findLastDeparture(
                "MTRS11133", "D", LocalDate.of(2026, 9, 7), DayType.WEEKDAY);

        assertThat(result).isEqualTo(LastDeparture.found(LocalDateTime.of(2026, 9, 8, 1, 0, 0)));
    }
}
