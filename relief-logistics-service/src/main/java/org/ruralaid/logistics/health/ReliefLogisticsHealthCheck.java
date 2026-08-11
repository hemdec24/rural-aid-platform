package org.ruralaid.logistics.health;

import com.codahale.metrics.health.HealthCheck;

public final class ReliefLogisticsHealthCheck extends HealthCheck {

    private final String serviceName;

    public ReliefLogisticsHealthCheck(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected Result check() {
        if (serviceName == null || serviceName.isBlank()) {
            return Result.unhealthy("Service name is missing");
        }

        return Result.healthy("%s initialized", serviceName);
    }
}