package com.lastsafe.external.odsay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.lastsafe.domain.route.PathCandidate;
import com.lastsafe.domain.route.PathLeg;
import com.lastsafe.entity.TransportType;

/**
 * 이 테스트는 **2026-09-09 실제 ODsay 호출로 받은 응답을 그대로**(passStopList 등 이 클라이언트가
 * 안 쓰는 항목만 제거) 픽스처로 쓴다({@code src/test/resources/odsay/search-pub-trans-path-t-real-sample.json},
 * 강남역→시청역 조회 결과 중 2건). 다른 TAGO 테스트들과 달리 "우리가 추정한 형태"가 아니라
 * "실제로 받은 형태"를 검증하는 것.
 */
class OdsayRouteSearchClientTest {

    private MockRestServiceServer server;
    private OdsayRouteSearchClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        OdsayProperties properties = new OdsayProperties("test-key", null);
        client = new OdsayRouteSearchClient(builder, properties);
    }

    private static String readRealSampleFixture() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("odsay/search-pub-trans-path-t-real-sample.json").getInputStream(),
                StandardCharsets.UTF_8);
    }

    @Test
    void 실제_응답_샘플을_경로_후보_리스트로_변환한다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andExpect(queryParam("SX", "127.027583"))
                .andExpect(queryParam("SY", "37.49791"))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        List<PathCandidate> result = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528);

        assertThat(result).hasSize(2);
    }

    @Test
    void 버스_구간은_버스번호와_ArsID를_담는다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        PathCandidate first = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528).get(0);

        // 첫 경로: 도보 → 버스(402번) → 도보
        assertThat(first.legs()).hasSize(3);
        PathLeg busLeg = first.legs().get(1);
        assertThat(busLeg.type()).isEqualTo(TransportType.BUS);
        assertThat(busLeg.lineName()).isEqualTo("402");
        assertThat(busLeg.busStopArsId()).isEqualTo("22012");
        assertThat(busLeg.startName()).isEqualTo("지하철2호선강남역");
        assertThat(busLeg.durationSec()).isEqualTo(36 * 60);
    }

    @Test
    void 도보_구간은_지명과_노선정보가_없다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        PathCandidate first = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528).get(0);

        PathLeg walkLeg = first.legs().get(0);
        assertThat(walkLeg.type()).isEqualTo(TransportType.WALK);
        assertThat(walkLeg.startName()).isNull();
        assertThat(walkLeg.lineName()).isNull();
        assertThat(walkLeg.busStopArsId()).isNull();
        assertThat(walkLeg.durationSec()).isEqualTo(5 * 60);
    }

    @Test
    void 지하철_구간은_노선명과_방향을_담는다() throws Exception {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess(readRealSampleFixture(), MediaType.APPLICATION_JSON));

        PathCandidate second = client.searchPaths(127.027583, 37.497910, 126.977041, 37.566528).get(1);

        // 두 번째 경로: 도보 → 버스 → 도보 → 지하철(수도권 1호선) → 도보
        PathLeg subwayLeg = second.legs().get(3);
        assertThat(subwayLeg.type()).isEqualTo(TransportType.SUBWAY);
        assertThat(subwayLeg.lineName()).isEqualTo("수도권 1호선");
        assertThat(subwayLeg.direction()).isEqualTo("시청");
        assertThat(subwayLeg.startName()).isEqualTo("종로3가");
        assertThat(subwayLeg.endName()).isEqualTo("시청");
        assertThat(subwayLeg.busStopArsId()).isNull();
    }

    @Test
    void 결과가_없으면_예외를_던진다() {
        setUp();
        server.expect(requestTo(containsString("/searchPubTransPathT")))
                .andRespond(withSuccess("{\"error\":[{\"code\":\"500\",\"message\":\"test\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchPaths(127.0, 37.0, 126.9, 37.5))
                .isInstanceOf(IllegalStateException.class);
    }
}
