package org.ruralaid.logistics.application.model;

import org.ruralaid.logistics.domain.ReserveInventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class ReservationRequestFingerprint {

    private ReservationRequestFingerprint() {
    }

    public static String from(ReserveInventory command) {
        Objects.requireNonNull(
                command,
                "Reservation command is required"
        );

        String canonicalRequest = String.join(
                "\n",
                "reservation-request-v1",
                "aidRequestId=" + command.aidRequestId(),
                "inventoryItemId=" + command.inventoryItemId(),
                "quantity=" + command.quantity()
        );

        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            canonicalRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Required fingerprint algorithm is unavailable",
                    exception
            );
        }
    }
}