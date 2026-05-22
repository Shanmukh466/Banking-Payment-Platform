package com.banking.payment.repository;

import com.banking.payment.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReferenceNumber(String referenceNumber);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findBySenderAccountId(String senderAccountId, Pageable pageable);

    Page<Payment> findByReceiverAccountId(String receiverAccountId, Pageable pageable);

    Page<Payment> findByStatus(Payment.PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.senderAccountId = :accountId OR p.receiverAccountId = :accountId")
    Page<Payment> findAllByAccountId(@Param("accountId") String accountId, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :start AND :end AND p.status = :status")
    List<Payment> findByDateRangeAndStatus(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("status") Payment.PaymentStatus status
    );

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.senderAccountId = :accountId AND p.status = 'COMPLETED' AND p.createdAt >= :since")
    BigDecimal sumCompletedPaymentsSince(
        @Param("accountId") String accountId,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.senderAccountId = :accountId AND p.createdAt >= :since")
    long countPaymentsSince(
        @Param("accountId") String accountId,
        @Param("since") LocalDateTime since
    );

    boolean existsByIdempotencyKey(String idempotencyKey);
}
