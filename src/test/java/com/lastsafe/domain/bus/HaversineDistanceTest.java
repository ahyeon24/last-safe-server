package com.lastsafe.domain.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class HaversineDistanceTest {

    @Test
    void 같은_지점의_거리는_0이다() {
        assertThat(HaversineDistance.meters(37.5, 127.0, 37.5, 127.0)).isEqualTo(0.0);
    }

    @Test
    void 위도가_1도_차이나면_지구_둘레의_360분의1과_같다() {
        // 경도가 같으면 하버사인 공식이 중심각 = 위도차(라디안)로 단순화되므로
        // R * (2*PI/360)과 정확히 같아야 한다 (지구 둘레/360).
        double distance = HaversineDistance.meters(37.0, 127.0, 38.0, 127.0);

        assertThat(distance).isCloseTo(111_194.9, within(1.0));
    }
}
