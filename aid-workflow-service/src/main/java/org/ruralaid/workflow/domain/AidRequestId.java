package org.ruralaid.workflow.domain;

public record AidRequestId(String id) {

        public AidRequestId {
                if (id == null || id.isBlank()) {
                        throw new IllegalArgumentException("Aid request must not be blank");
                }
        }
}
