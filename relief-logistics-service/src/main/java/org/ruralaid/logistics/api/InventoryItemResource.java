package org.ruralaid.logistics.api;

import java.util.Objects;
import java.util.UUID;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.ruralaid.logistics.api.model.ApiErrorResponse;
import org.ruralaid.logistics.api.model.InventoryItemResponse;
import org.ruralaid.logistics.application.InventoryItemQueryService;

@Path("/inventory-items")
@Produces(MediaType.APPLICATION_JSON)
public final class InventoryItemResource {

    private final InventoryItemQueryService queryService;

    public InventoryItemResource(
            InventoryItemQueryService queryService
    ) {
        this.queryService = Objects.requireNonNull(
                queryService,
                "Inventory query service is required"
        );
    }

    @GET
    @Path("/{inventoryItemId}")
    public Response getInventoryItem(
            @PathParam("inventoryItemId") UUID inventoryItemId
    ) {
        return queryService.findById(inventoryItemId)
                .map(snapshot -> Response.ok(
                        new InventoryItemResponse(
                                snapshot.inventoryItemId(),
                                snapshot.resourceType(),
                                snapshot.availableQuantity(),
                                snapshot.version()
                        )
                ).build())
                .orElseGet(() -> Response.status(
                                Response.Status.NOT_FOUND
                        )
                        .entity(new ApiErrorResponse(
                                "INVENTORY_ITEM_NOT_FOUND",
                                "Inventory item does not exist"
                        ))
                        .build());
    }
}
