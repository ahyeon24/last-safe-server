package com.lastsafe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lastsafe.domain.bus.BusStopLastDepartureEstimator;

@Configuration
public class BusEstimateConfig {

    @Bean
    public BusStopLastDepartureEstimator busStopLastDepartureEstimator(BusEstimateProperties properties) {
        return new BusStopLastDepartureEstimator(properties.averageSpeedMetersPerSecond());
    }
}
