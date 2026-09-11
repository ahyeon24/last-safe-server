package com.lastsafe.external.tago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import com.lastsafe.domain.route.SubwayStationCandidate;
import com.lastsafe.external.datagokr.DataGoKrProperties;

/**
 * 픽스처는 **2026-09-11 "시청" 키워드로 실제 호출한 응답 그대로**다
 * ({@code src/test/resources/tago/get-kwrd-fnd-subway-sttn-list-sichung-real-sample.json}) —
 * 전국의 "OO시청"류 역 + 서울/대구의 정확히 "시청"인 역들이 섞여서 온다는 실제 특성을
 * 그대로 검증한다.
 */
class TagoSubwayStationLookupClientTest {

    private MockRestServiceServer server;
    private TagoSubwayStationLookupClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DataGoKrProperties properties = new DataGoKrProperties("test-key");
        client = new TagoSubwayStationLookupClient(builder, properties);
    }

    private static String readRealSampleFixture() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("tago/get-kwrd-fnd-subway-sttn-list-sichung-real-sample.json").getInputStream(),
                StandardCharsets.UTF_8);
    }

    @Test
    void 부분일치로_섞여온_다른_역들은_걸러내고_정확히_일치하는_역만_남긴다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/GetKwrdFndSubwaySttnList")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        List<SubwayStationCandidate> result = client.findByExactStationName("시청");

        // 실제 응답엔 15건이 오지만("김해시청", "부천시청" 등 포함), 정확히 "시청"인 건
        // 대구 1호선, 서울 1호선, 서울 2호선 3건뿐이다.
        assertThat(result).hasSize(3);
        assertThat(result).extracting(SubwayStationCandidate::subwayStationId)
                .containsExactlyInAnyOrder("MTRDJ10011", "MTRS11151", "MTRS12201");
        assertThat(result).extracting(SubwayStationCandidate::stationName)
                .allMatch("시청"::equals);
    }
}
