package com.lastsafe.domain.lasttrain;

import java.time.LocalDateTime;

public record LastDeparture(LastDepartureStatus status, LocalDateTime time) {

    public static LastDeparture found(LocalDateTime time) {
        return new LastDeparture(LastDepartureStatus.FOUND, time);
    }

    public static LastDeparture noService() {
        return new LastDeparture(LastDepartureStatus.NO_SERVICE, null);
    }

    public static LastDeparture fetchError() {
        return new LastDeparture(LastDepartureStatus.FETCH_ERROR, null);
    }
}
