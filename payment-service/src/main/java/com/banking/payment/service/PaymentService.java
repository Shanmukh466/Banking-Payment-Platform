package com.banking.payment.service;

import com.banking.payment.dto.PaymentDto;
import com.banking.payment.exception.DuplicatePaymentException;
import com.banking.payment.exception.PaymentNotFoundException;
import com.banking.payment.kafka.PaymentEventProducer;
import com.banking.payment.model.Payment;
import com.banking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core payment processing service.
 *
 * Key design decisions:
 * - Idempotency: prevents duplicate payments using idempotency keys cached in Redis
 * - Optimistic locking: prevents race conditions on concurrent payment requests
 * - Kafka events: every state change published for downstream processing
 * - Redis caching: frequently accessed payments cached to reduce DB load
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FraudDetectionService fraudDetectionService;

    private static final String PAYMENT_CACHE_PREFIX = "payment:";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final int CACHE_TTL_SECONDS = 300;

    /**
     * Initiate a new payment with idempotency guarantee.
     * If the same idempotency key is submitted twice, returns the original payment.
     */
    public PaymentDto.Response initiatePayment(PaymentDto.InitiateRequest request) {
        log.info("Initiating payment from {} to {} amount: {} {}",
            request.getSenderAccountId(),
            request.getReceiverAccountId(),
            request.getAmount(),
            request.getCurrency());

        // Idempotency check — prevent duplicate payments
        String idempotencyKey = IDEMPOTENCY_PREFIX + request.getIdempotencyKey();
        Object cachedPaymentId = redisTemplate.opsForValue().get(idempotencyKey);

        if (cachedPaymentId != null) {
            log.warn("Duplicate payment attempt detected for idempotency key: {}", request.getIdempotencyKey());
            return paymentRepository.findById(UUID.fromString(cachedPaymentId.toString()))
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));
        }

        // Check DB for idempotency key
        if (paymentRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new DuplicatePaymentException("Payment with this idempotency key already exists");
        }

        // Fraud check before processing
        String fraudScore = fraudDetectionService.checkFraud(
            request.getSenderAccountId(),
            request.getAmount(),
            request.getCurrency()
        );

        Payment payment = Payment.builder()
            .referenceNumber(generateReferenceNumber())
            .senderAccountId(request.getSenderAccountId())
            .senderName(request.getSenderName())
            .receiverAccountId(request.getReceiverAccountId())
            .receiverName(request.getReceiverName())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .type(request.getType())
            .description(request.getDescription())
            .idempotencyKey(request.getIdempotencyKey())
            .fraudChecked(true)
            .fraudScore(fraudScore)
            .status(fraudDetectionService.isFraudulent(fraudScore)
                ? Payment.PaymentStatus.FRAUD_DETECTED
                : Payment.PaymentStatus.INITIATED)
            .build();

        Payment saved = paymentRepository.save(payment);

        // Cache idempotency key with TTL
        redisTemplate.opsForValue().set(idempotencyKey, saved.getId().toString(), 24, TimeUnit.HOURS);

        // Cache payment response
        redisTemplate.opsForValue().set(
            PAYMENT_CACHE_PREFIX + saved.getId(),
            toResponse(saved),
            CACHE_TTL_SECONDS,
            TimeUnit.SECONDS
        );

        PaymentDto.Response response = toResponse(saved);

        // Publish event to Kafka
        if (saved.getStatus() == Payment.PaymentStatus.FRAUD_DETECTED) {
            eventProducer.publishFraudDetected(response);
            log.warn("Fraud detected for payment: {}", saved.getReferenceNumber());
        } else {
            eventProducer.publishPaymentInitiated(response);
        }

        return response;
    }

    /**
     * Complete a payment — called after account balance verification.
     */
    public PaymentDto.Response completePayment(UUID paymentId) {
        Payment payment = getPaymentEntity(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.INITIATED &&
            payment.getStatus() != Payment.PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment cannot be completed in status: " + payment.getStatus());
        }

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setProcessedAt(LocalDateTime.now());

        Payment updated = paymentRepository.save(payment);

        // Invalidate cache
        redisTemplate.delete(PAYMENT_CACHE_PREFIX + paymentId);

        PaymentDto.Response response = toResponse(updated);
        eventProducer.publishPaymentCompleted(response);

        log.info("Payment completed: {}", updated.getReferenceNumber());
        return response;
    }

    /**
     * Fail a payment with a reason.
     */
    public PaymentDto.Response failPayment(UUID paymentId, String reason) {
        Payment payment = getPaymentEntity(paymentId);

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setProcessedAt(LocalDateTime.now());

        Payment updated = paymentRepository.save(payment);
        redisTemplate.delete(PAYMENT_CACHE_PREFIX + paymentId);

        PaymentDto.Response response = toResponse(updated);
        eventProducer.publishPaymentFailed(response);

        log.warn("Payment failed: {} reason: {}", updated.getReferenceNumber(), reason);
        return response;
    }

    @Transactional(readOnly = true)
    public PaymentDto.Response getPaymentById(UUID id) {
        // Check cache first
        String cacheKey = PAYMENT_CACHE_PREFIX + id;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            log.debug("Cache hit for payment: {}", id);
            return (PaymentDto.Response) cached;
        }

        Payment payment = getPaymentEntity(id);
        PaymentDto.Response response = toResponse(payment);

        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        return response;
    }

    @Transactional(readOnly = true)
    public PaymentDto.Response getPaymentByReference(String referenceNumber) {
        return paymentRepository.findByReferenceNumber(referenceNumber)
            .map(this::toResponse)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + referenceNumber));
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getPaymentsByAccount(String accountId, Pageable pageable) {
        return paymentRepository.findAllByAccountId(accountId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getPaymentsByStatus(Payment.PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(this::toResponse);
    }

    private Payment getPaymentEntity(UUID id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
    }

    private String generateReferenceNumber() {
        return "PAY-" + System.currentTimeMillis() + "-"
            + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private PaymentDto.Response toResponse(Payment payment) {
        return PaymentDto.Response.builder()
            .id(payment.getId())
            .referenceNumber(payment.getReferenceNumber())
            .senderAccountId(payment.getSenderAccountId())
            .senderName(payment.getSenderName())
            .receiverAccountId(payment.getReceiverAccountId())
            .receiverName(payment.getReceiverName())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .type(payment.getType())
            .description(payment.getDescription())
            .failureReason(payment.getFailureReason())
            .fraudChecked(payment.isFraudChecked())
            .processedAt(payment.getProcessedAt())
            .createdAt(payment.getCreatedAt())
            .build();
    }
}
