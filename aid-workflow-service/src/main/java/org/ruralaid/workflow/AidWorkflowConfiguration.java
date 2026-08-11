package org.ruralaid.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import jakarta.validation.constraints.NotBlank;

public final class AidWorkflowConfiguration extends Configuration{

    @NotBlank
    @JsonProperty
    private String serviceName;

    public String getServiceName() {
        return this.serviceName;
    }
}
