package com.banking.payment.dto;

import com.banking.payment.model.Payment;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class PaymentDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InitiateRequest {

        @NotBlank(message = "Sender account ID is required")
        private String senderAccountId;

        @NotBlank(message = "Sender name is required")
        private String senderName;

        @NotBlank(message = "Receiver account ID is required")
        private String receiverAccountId;

        @NotBlank(message = "Receiver name is required")
        private String receiverName;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000.00", message = "Amount exceeds maximum limit")
        private BigDecimal amount;

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters (e.g. USD)")
        private String currency;

        @NotNull(message = "Payment type is required")
        private Payment.PaymentType type;

        private String description;

        @NotBlank(message = "Idempotency key is required")
        private String idempotencyKey;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID id;
        private String referenceNumber;
        private String senderAccountId;
        private String senderName;
        private String receiverAccountId;
        private String receiverName;
        private BigDecimal amount;
        private String currency;
        private Payment.PaymentStatus status;
        private Payment.PaymentType type;
        private String description;
        private String failureReason;
        private boolean fraudChecked;
        private LocalDateTime processedAt;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PageResponse {
        private java.util.List<Response> payments;
        private long totalElements;
        private int totalPages;
        private int currentPage;
        private int pageSize;
    }
}
