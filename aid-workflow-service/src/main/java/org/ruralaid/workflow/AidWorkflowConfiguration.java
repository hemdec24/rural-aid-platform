package org.ruralaid.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AidWorkflowConfiguration extends Configuration{

    @NotBlank
    @JsonProperty
    private String serviceName;

    @Valid
    @NotNull
    @JsonProperty("database")
    private DataSourceFactory database = new DataSourceFactory();

    public String getServiceName() {
        return this.serviceName;
    }

    public DataSourceFactory getDataSourceFactory() {
        return this.database;
    }
}
