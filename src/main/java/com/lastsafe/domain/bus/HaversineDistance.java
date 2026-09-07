package com.lastsafe.domain.bus;

/**
 * 위경도 두 점 사이의 직선거리(대권거리)를 구한다. TAGO 노선별경유정류소목록조회 응답엔
 * 정류소 간 거리·소요시간 필드가 없어서(spec.md 5.2절), 좌표로 직접 계산하는 용도다.
 */
public final class HaversineDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private HaversineDistance() {
    }

    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * centralAngle;
    }
}
