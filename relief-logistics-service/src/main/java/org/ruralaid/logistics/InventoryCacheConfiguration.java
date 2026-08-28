package org.ruralaid.logistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class InventoryCacheConfiguration {

    @Min(1)
    @JsonProperty
    private int maximumEntries = 1_000;

    @Min(1)
    @JsonProperty
    private long positiveTtlMillis = 30_000;

    @Min(1)
    @JsonProperty
    private long negativeTtlMillis = 5_000;

    @NotBlank
    @JsonProperty
    private String consumerId = "relief-logistics-local";

    @Min(1)
    @JsonProperty
    private long pollIntervalMillis = 250;

    @Min(1)
    @JsonProperty
    private long maximumPollStalenessMillis = 2_000;

    @Min(1)
    @JsonProperty
    private int eventBatchSize = 100;

    @Min(1)
    @JsonProperty
    private int maximumCatchupBatches = 10;

    public int getMaximumEntries() {
        return maximumEntries;
    }

    public long getPositiveTtlMillis() {
        return positiveTtlMillis;
    }

    public long getNegativeTtlMillis() {
        return negativeTtlMillis;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public long getMaximumPollStalenessMillis() {
        return maximumPollStalenessMillis;
    }

    public int getEventBatchSize() {
        return eventBatchSize;
    }

    public int getMaximumCatchupBatches() {
        return maximumCatchupBatches;
    }
}
