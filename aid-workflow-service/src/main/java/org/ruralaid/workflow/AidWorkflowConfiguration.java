package org.ruralaid.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;


public final class AidWorkflowConfiguration extends Configuration{

    @NotBlank
    @JsonProperty
    private String serviceName;

    @Valid
    @NotNull
    @JsonProperty("database")
    private DataSourceFactory database = new DataSourceFactory();

    @NotNull
    @JsonProperty
    private URI logisticsBaseUri;

    @Min(1)
    @JsonProperty
    private long logisticsConnectTimeoutMillis = 1000;

    @Min(1)
    @JsonProperty
    private long logisticsRequestTimeoutMillis = 3000;

    public String getServiceName() {
        return this.serviceName;
    }

    public DataSourceFactory getDataSourceFactory() {
        return this.database;
    }

    public URI getLogisticsBaseUri() {
        return this.logisticsBaseUri;
    }

    public long getLogisticsConnectTimeoutMillis() {
        return this.logisticsConnectTimeoutMillis;
    }

    public long getLogisticsRequestTimeoutMillis() {
        return this.logisticsRequestTimeoutMillis;
    }
}
