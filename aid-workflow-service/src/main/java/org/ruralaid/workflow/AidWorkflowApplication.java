package org.ruralaid.workflow;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.migrations.MigrationsBundle;

import io.dropwizard.jdbi3.JdbiFactory;
import org.jdbi.v3.core.Jdbi;

import org.ruralaid.workflow.application.port.AidRequestRepository;
import org.ruralaid.workflow.persistence.JdbiAidRequestRepository;
import org.ruralaid.workflow.api.AidRequestResource;
import org.ruralaid.workflow.api.exception.AidRequestNotFoundExceptionMapper;
import org.ruralaid.workflow.api.exception.AidRequestStateConflictExceptionMapper;
import org.ruralaid.workflow.api.exception.AidRequestVersionConflictExceptionMapper;
import org.ruralaid.workflow.api.exception.InvalidDomainInputExceptionMapper;
import org.ruralaid.workflow.application.AidRequestApplicationService;


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
        Jdbi jdbi = new JdbiFactory().build(
                environment,
                configuration.getDataSourceFactory(),
                "aid-workflow-database"
        );

        AidRequestRepository repository = new JdbiAidRequestRepository(jdbi);

        AidRequestApplicationService applicationService = new AidRequestApplicationService(repository);

        environment.jersey().register(
                new AidRequestResource(applicationService)
        );

        environment.jersey().register(
                new AidRequestNotFoundExceptionMapper()
        );

        environment.jersey().register(
                new AidRequestVersionConflictExceptionMapper()
        );

        environment.jersey().register(
                new InvalidDomainInputExceptionMapper()
        );

        environment.jersey().register(
                new AidRequestStateConflictExceptionMapper()
        );

        environment.healthChecks().register(
                "aid-workflow-service",
                new AidWorkflowHealthCheck(
                        configuration.getServiceName()
                )
        );
    }

    @Override
    public void initialize(
            Bootstrap<AidWorkflowConfiguration> bootstrap
    ) {
        bootstrap.addBundle(
                new MigrationsBundle<AidWorkflowConfiguration>() {
                    @Override
                    public DataSourceFactory getDataSourceFactory(
                            AidWorkflowConfiguration configuration
                    ) {
                        return configuration.getDataSourceFactory();
                    }
                }
        );
    }
}
