package com.banking.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core payment entity representing a financial transaction.
 * Supports domestic and international transfers with full audit trail.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_reference", columnList = "referenceNumber"),
    @Index(name = "idx_payment_sender", columnList = "senderAccountId"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String referenceNumber;

    @Column(nullable = false)
    private String senderAccountId;

    @Column(nullable = false)
    private String senderName;

    @Column(nullable = false)
    private String receiverAccountId;

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    private String description;

    private String failureReason;

    @Column(nullable = false)
    private boolean fraudChecked = false;

    private String fraudScore;

    @Column(nullable = false)
    private String idempotencyKey;

    private LocalDateTime processedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        INITIATED, PROCESSING, COMPLETED, FAILED, REVERSED, FRAUD_DETECTED
    }

    public enum PaymentType {
        DOMESTIC_TRANSFER, INTERNATIONAL_WIRE, ACH, BILL_PAYMENT, PEER_TO_PEER
    }
}
