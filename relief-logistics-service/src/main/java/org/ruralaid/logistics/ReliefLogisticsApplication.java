package org.ruralaid.logistics;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.migrations.MigrationsBundle;

import java.time.Clock;
import java.time.Duration;

import org.jdbi.v3.core.Jdbi;

import org.ruralaid.logistics.api.InventoryItemResource;
import org.ruralaid.logistics.api.InventoryReservationResource;
import org.ruralaid.logistics.api.exception.InventoryUnavailableExceptionMapper;
import org.ruralaid.logistics.api.exception.ReservationIdConflictExceptionMapper;
import org.ruralaid.logistics.application.InventoryItemQueryService;
import org.ruralaid.logistics.application.InventoryReservationService;
import org.ruralaid.logistics.application.port.InventoryCacheCoherenceRepository;
import org.ruralaid.logistics.application.port.InventoryItemQueryRepository;
import org.ruralaid.logistics.application.port.InventoryReservationRepository;
import org.ruralaid.logistics.cache.InventoryCacheCoherenceManager;
import org.ruralaid.logistics.cache.LocalInventoryItemCache;
import org.ruralaid.logistics.health.InventoryCacheReadinessHealthCheck;
import org.ruralaid.logistics.health.ReliefLogisticsHealthCheck;
import org.ruralaid.logistics.persistence.JdbiInventoryCacheCoherenceRepository;
import org.ruralaid.logistics.persistence.JdbiInventoryItemQueryRepository;
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

        InventoryCacheConfiguration cacheConfiguration =
                configuration.getInventoryCache();

        LocalInventoryItemCache inventoryCache =
                new LocalInventoryItemCache(
                        cacheConfiguration.getMaximumEntries(),
                        Duration.ofMillis(
                                cacheConfiguration.getPositiveTtlMillis()
                        ),
                        Duration.ofMillis(
                                cacheConfiguration.getNegativeTtlMillis()
                        ),
                        Clock.systemUTC(),
                        environment.metrics()
                );

        InventoryCacheCoherenceRepository coherenceRepository =
                new JdbiInventoryCacheCoherenceRepository(jdbi);

        InventoryCacheCoherenceManager coherenceManager =
                new InventoryCacheCoherenceManager(
                        coherenceRepository,
                        inventoryCache,
                        resolveCacheConsumerId(cacheConfiguration),
                        cacheConfiguration.getMaximumEntries(),
                        cacheConfiguration.getEventBatchSize(),
                        cacheConfiguration.getMaximumCatchupBatches(),
                        cacheConfiguration.getPollIntervalMillis(),
                        cacheConfiguration
                                .getMaximumPollStalenessMillis(),
                        Clock.systemUTC(),
                        environment.metrics()
                );

        environment.lifecycle().manage(coherenceManager);

        InventoryReservationRepository reservationRepository =
                new JdbiInventoryReservationRepository(jdbi);

        InventoryReservationService reservationService =
                new InventoryReservationService(
                        reservationRepository,
                        inventoryCache
                );

        InventoryReservationResource reservationResource =
                new InventoryReservationResource(
                        reservationService
                );

        InventoryItemQueryRepository inventoryItemQueryRepository =
                new JdbiInventoryItemQueryRepository(jdbi);

        InventoryItemQueryService inventoryItemQueryService =
                new InventoryItemQueryService(
                        inventoryItemQueryRepository,
                        inventoryCache,
                        coherenceManager::isCacheSafe
                );

        environment.jersey().register(reservationResource);

        environment.jersey().register(
                new InventoryItemResource(inventoryItemQueryService)
        );

        environment.jersey().register(new InventoryUnavailableExceptionMapper());

        environment.jersey().register(new ReservationIdConflictExceptionMapper());

        environment.healthChecks().register(
                "relief-logistics-service",
                new ReliefLogisticsHealthCheck(
                        configuration.getServiceName()
                )
        );

        environment.healthChecks().register(
                "inventory-cache-readiness",
                new InventoryCacheReadinessHealthCheck(coherenceManager)
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

    private String resolveCacheConsumerId(
            InventoryCacheConfiguration configuration
    ) {
        String environmentConsumerId =
                System.getenv("CACHE_CONSUMER_ID");

        if (environmentConsumerId != null
                && !environmentConsumerId.isBlank()) {
            return environmentConsumerId;
        }

        String hostname = System.getenv("HOSTNAME");

        if (hostname != null && !hostname.isBlank()) {
            return "relief-logistics-" + hostname;
        }

        return configuration.getConsumerId();
    }
}
