package org.ruralaid.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import jakarta.validation.constraints.NotBlank;

public final class ReliefLogisticsConfiguration extends Configuration {

    @NotBlank
    @JsonProperty
    private String serviceName;

    public String getServiceName() {
        return serviceName;
    }
}