package com.ecomm.oms.returns.dto;

import com.ecomm.oms.returns.Refund;
import com.ecomm.oms.returns.ReturnItem;
import com.ecomm.oms.returns.ReturnRequest;

import java.math.BigDecimal;
import java.util.List;

public record ReturnResponse(
        Long id,
        Long orderId,
        String status,
        String reason,
        List<ReturnLineResponse> items,
        RefundResponse refund) {

    public record ReturnLineResponse(Long orderItemId, String productSku, int quantity, String reason) {
        static ReturnLineResponse from(ReturnItem item) {
            return new ReturnLineResponse(
                    item.getOrderItem().getId(),
                    item.getOrderItem().getProductSku(),
                    item.getQuantity(),
                    item.getReason());
        }
    }

    public record RefundResponse(Long id, BigDecimal amount, String status, String gatewayRef) {
        static RefundResponse from(Refund refund) {
            return new RefundResponse(refund.getId(), refund.getAmount(),
                    refund.getStatus(), refund.getGatewayRef());
        }
    }

    public static ReturnResponse from(ReturnRequest request, Refund refund) {
        List<ReturnLineResponse> lines = request.getItems().stream()
                .map(ReturnLineResponse::from)
                .toList();
        return new ReturnResponse(
                request.getId(),
                request.getOrderId(),
                request.getStatus().name(),
                request.getReason(),
                lines,
                refund == null ? null : RefundResponse.from(refund));
    }
}
