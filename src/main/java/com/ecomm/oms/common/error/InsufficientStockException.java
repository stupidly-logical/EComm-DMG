package com.ecomm.oms.common.error;

/** Raised when available stock cannot satisfy a requested quantity. Maps to HTTP 409. */
public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String message) {
        super(message, "INSUFFICIENT_STOCK");
    }
}
