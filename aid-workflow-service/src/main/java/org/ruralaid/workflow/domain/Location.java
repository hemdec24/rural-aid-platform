package org.ruralaid.workflow.domain;

public record Location(double latitude, double longitude) {

    public Location {
        if (!Double.isFinite(latitude)
                || latitude < -90.0
                || latitude > 90.0) {
            throw new IllegalArgumentException(
                    "Latitude must be a finite value between -90 and 90 degrees"
            );
        }

        if (!Double.isFinite(longitude)
                || longitude < -180.0
                || longitude > 180.0) {
            throw new IllegalArgumentException(
                    "Longitude must be a finite value between -180 and 180 degrees"
            );
        }
    }
}
