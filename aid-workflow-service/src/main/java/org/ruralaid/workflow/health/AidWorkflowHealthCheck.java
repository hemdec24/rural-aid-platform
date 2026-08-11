package org.ruralaid.workflow.health;

import com.codahale.metrics.health.HealthCheck;

public class AidWorkflowHealthCheck extends HealthCheck {

    private final String serviceName;

    public AidWorkflowHealthCheck(String serviceName) {
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
