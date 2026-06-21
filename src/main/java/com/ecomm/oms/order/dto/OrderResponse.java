package com.ecomm.oms.order.dto;

import com.ecomm.oms.order.Order;
import com.ecomm.oms.order.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal shippingTotal,
        BigDecimal grandTotal,
        String couponCode,
        Instant placedAt,
        List<OrderItemResponse> items) {

    public record OrderItemResponse(
            Long id,
            Long productId,
            String sku,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineDiscount,
            BigDecimal lineTax,
            BigDecimal lineTotal,
            Long allocatedWarehouseId) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductSku(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineDiscount(),
                    item.getLineTax(),
                    item.getLineTotal(),
                    item.getAllocatedWarehouseId());
        }
    }

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTaxTotal(),
                order.getShippingTotal(),
                order.getGrandTotal(),
                order.getCouponCode(),
                order.getPlacedAt(),
                items);
    }
}
