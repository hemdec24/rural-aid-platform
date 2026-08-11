package org.ruralaid.logistics;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import org.ruralaid.logistics.health.ReliefLogisticsHealthCheck;

public final class ReliefLogisticsApplication
        extends Application<ReliefLogisticsConfiguration> {

    public static void main(String[] args) throws Exception {
        new ReliefLogisticsApplication().run(args);
    }

    @Override
    public String getName() {
        return "relief-logistics-service";
    }

    @Override
    public void run(
            ReliefLogisticsConfiguration configuration,
            Environment environment
    ) {
        environment.healthChecks().register(
                "relief-logistics-service",
                new ReliefLogisticsHealthCheck(configuration.getServiceName())
        );
    }
}