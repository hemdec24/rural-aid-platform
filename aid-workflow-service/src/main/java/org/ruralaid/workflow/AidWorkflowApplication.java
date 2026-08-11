package org.ruralaid.workflow;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import org.ruralaid.workflow.health.AidWorkflowHealthCheck;

public class AidWorkflowApplication extends Application<AidWorkflowConfiguration> {

    public static void main(String[] args) throws Exception {
        new AidWorkflowApplication().run(args);
    }

    @Override
    public String getName() {
        return "aid-workflow-service";
    }

    @Override
    public void run(
            AidWorkflowConfiguration configuration,
            Environment environment
    ) {
        environment.healthChecks().register(
                "aid-workflow-service",
                new AidWorkflowHealthCheck(configuration.getServiceName())
        );
    }
}
