package com.banking.payment;

import com.banking.payment.dto.PaymentDto;
import com.banking.payment.exception.DuplicatePaymentException;
import com.banking.payment.kafka.PaymentEventProducer;
import com.banking.payment.model.Payment;
import com.banking.payment.repository.PaymentRepository;
import com.banking.payment.service.FraudDetectionService;
import com.banking.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentEventProducer eventProducer;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private FraudDetectionService fraudDetectionService;
    @Mock private ValueOperations<String, Object> valueOperations;
    @InjectMocks private PaymentService paymentService;

    private PaymentDto.InitiateRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = PaymentDto.InitiateRequest.builder()
            .senderAccountId("ACC-001")
            .senderName("John Doe")
            .receiverAccountId("ACC-002")
            .receiverName("Jane Smith")
            .amount(new BigDecimal("500.00"))
            .currency("USD")
            .type(Payment.PaymentType.DOMESTIC_TRANSFER)
            .description("Rent payment")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    void initiatePayment_shouldCreatePaymentWithInitiatedStatus() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(fraudDetectionService.checkFraud(any(), any(), any())).thenReturn("10");
        when(fraudDetectionService.isFraudulent("10")).thenReturn(false);

        Payment savedPayment = buildPayment(Payment.PaymentStatus.INITIATED);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        PaymentDto.Response response = paymentService.initiatePayment(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.INITIATED);
        assertThat(response.getAmount()).isEqualTo(new BigDecimal("500.00"));
        verify(eventProducer).publishPaymentInitiated(any());
    }

    @Test
    void initiatePayment_shouldDetectFraudAndFlagPayment() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(fraudDetectionService.checkFraud(any(), any(), any())).thenReturn("75");
        when(fraudDetectionService.isFraudulent("75")).thenReturn(true);

        Payment fraudPayment = buildPayment(Payment.PaymentStatus.FRAUD_DETECTED);
        when(paymentRepository.save(any(Payment.class))).thenReturn(fraudPayment);

        PaymentDto.Response response = paymentService.initiatePayment(validRequest);

        assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.FRAUD_DETECTED);
        verify(eventProducer).publishFraudDetected(any());
        verify(eventProducer, never()).publishPaymentInitiated(any());
    }

    @Test
    void initiatePayment_shouldThrowOnDuplicateIdempotencyKey() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
            .isInstanceOf(DuplicatePaymentException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiatePayment_shouldPublishKafkaEvent() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(fraudDetectionService.checkFraud(any(), any(), any())).thenReturn("5");
        when(fraudDetectionService.isFraudulent("5")).thenReturn(false);
        when(paymentRepository.save(any())).thenReturn(buildPayment(Payment.PaymentStatus.INITIATED));

        paymentService.initiatePayment(validRequest);

        verify(eventProducer, times(1)).publishPaymentInitiated(any(PaymentDto.Response.class));
    }

    private Payment buildPayment(Payment.PaymentStatus status) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .referenceNumber("PAY-TEST-001")
            .senderAccountId("ACC-001")
            .senderName("John Doe")
            .receiverAccountId("ACC-002")
            .receiverName("Jane Smith")
            .amount(new BigDecimal("500.00"))
            .currency("USD")
            .type(Payment.PaymentType.DOMESTIC_TRANSFER)
            .status(status)
            .fraudChecked(true)
            .idempotencyKey(validRequest.getIdempotencyKey())
            .build();
    }
}
