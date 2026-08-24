package org.ruralaid.logistics;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.migrations.MigrationsBundle;

import org.jdbi.v3.core.Jdbi;

import org.ruralaid.logistics.api.InventoryReservationResource;
import org.ruralaid.logistics.api.exception.InventoryUnavailableExceptionMapper;
import org.ruralaid.logistics.api.exception.ReservationIdConflictExceptionMapper;
import org.ruralaid.logistics.application.InventoryReservationService;
import org.ruralaid.logistics.application.port.InventoryReservationRepository;
import org.ruralaid.logistics.health.ReliefLogisticsHealthCheck;
import org.ruralaid.logistics.persistence.JdbiInventoryReservationRepository;

public final class ReliefLogisticsApplication extends Application<ReliefLogisticsConfiguration> {

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
        JdbiFactory jdbiFactory = new JdbiFactory();

        Jdbi jdbi = jdbiFactory.build(
                environment,
                configuration.getDataSourceFactory(),
                "relief-logistics-database"
        );

        InventoryReservationRepository reservationRepository =
                new JdbiInventoryReservationRepository(jdbi);

        InventoryReservationService reservationService =
                new InventoryReservationService(
                        reservationRepository
                );

        InventoryReservationResource reservationResource =
                new InventoryReservationResource(
                        reservationService
                );

        environment.jersey().register(reservationResource);

        environment.jersey().register(new InventoryUnavailableExceptionMapper());

        environment.jersey().register(new ReservationIdConflictExceptionMapper());

        environment.healthChecks().register(
                "relief-logistics-service",
                new ReliefLogisticsHealthCheck(
                        configuration.getServiceName()
                )
        );
    }

    @Override
    public void initialize(Bootstrap<ReliefLogisticsConfiguration> bootstrap) {
        bootstrap.addBundle(
                new MigrationsBundle<ReliefLogisticsConfiguration>() {
                    @Override
                    public DataSourceFactory getDataSourceFactory(
                            ReliefLogisticsConfiguration configuration
                    ) {
                        return configuration.getDataSourceFactory();
                    }
                }
        );
    }
}