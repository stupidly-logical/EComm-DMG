package com.ecomm.oms.inventory;

import com.ecomm.oms.inventory.dto.StockAdjustmentRequest;
import com.ecomm.oms.inventory.dto.StockLevelResponse;
import com.ecomm.oms.inventory.dto.WarehouseRequest;
import com.ecomm.oms.inventory.dto.WarehouseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Warehouses", description = "Warehouse and stock administration (admin only)")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @Operation(summary = "List warehouses by allocation priority")
    public List<WarehouseResponse> list() {
        return warehouseService.list().stream().map(WarehouseResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a warehouse by id")
    public WarehouseResponse get(@PathVariable Long id) {
        return WarehouseResponse.from(warehouseService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a warehouse")
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return WarehouseResponse.from(warehouseService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a warehouse")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return WarehouseResponse.from(warehouseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a warehouse")
    public void delete(@PathVariable Long id) {
        warehouseService.delete(id);
    }

    @PostMapping("/{id}/stock")
    @Operation(summary = "Set the on-hand quantity of a product at this warehouse")
    public StockLevelResponse adjustStock(@PathVariable Long id,
                                          @Valid @RequestBody StockAdjustmentRequest request) {
        return StockLevelResponse.from(warehouseService.adjustStock(id, request));
    }
}
