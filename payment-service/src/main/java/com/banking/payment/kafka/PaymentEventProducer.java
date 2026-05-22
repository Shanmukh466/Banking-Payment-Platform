package com.banking.payment.kafka;

import com.banking.payment.dto.PaymentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes payment domain events to Kafka topics.
 * Uses idempotent producer configuration to prevent duplicate messages.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payment-initiated}")
    private String paymentInitiatedTopic;

    @Value("${kafka.topics.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${kafka.topics.fraud-check}")
    private String fraudCheckTopic;

    public void publishPaymentInitiated(PaymentDto.Response payment) {
        publish(paymentInitiatedTopic, payment.getReferenceNumber(), payment);
        log.info("Published payment initiated: {}", payment.getReferenceNumber());
    }

    public void publishPaymentCompleted(PaymentDto.Response payment) {
        publish(paymentCompletedTopic, payment.getReferenceNumber(), payment);
        log.info("Published payment completed: {}", payment.getReferenceNumber());
    }

    public void publishPaymentFailed(PaymentDto.Response payment) {
        publish(paymentFailedTopic, payment.getReferenceNumber(), payment);
        log.warn("Published payment failed: {} reason: {}", payment.getReferenceNumber(), payment.getFailureReason());
    }

    public void publishFraudDetected(PaymentDto.Response payment) {
        publish(fraudCheckTopic, payment.getReferenceNumber(), payment);
        log.warn("Published fraud detected: {}", payment.getReferenceNumber());
    }

    private void publish(String topic, String key, Object payload) {
        CompletableFuture future = kafkaTemplate.send(topic, key, payload);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to topic {}: {}", topic, ex.getMessage());
            }
        });
    }
}
