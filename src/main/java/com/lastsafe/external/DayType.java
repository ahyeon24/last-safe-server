package com.lastsafe.external;

/**
 * 지하철/버스 배차는 요일에 따라 다르므로(평일/토요일/일요일·공휴일) 시각 조회 시 반드시 필요.
 * 국내 대중교통 시간표 관행상 월~금은 하나의 "평일" 스케줄로 동일하고, 일요일과 공휴일도
 * 하나의 스케줄로 동일하게 취급한다 — 그래서 요일별 7종이 아니라 3종.
 * 실제 ODsay/서울시/국토부 API의 요일 구분 값과 매핑은 각 구현체 책임.
 */
public enum DayType {
    WEEKDAY, SATURDAY, SUNDAY_OR_HOLIDAY
}
