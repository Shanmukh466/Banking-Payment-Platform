package com.banking.payment.service;

import com.banking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Rule-based fraud detection service.
 *
 * Fraud rules:
 * 1. Velocity check — more than 10 payments in last hour
 * 2. Amount threshold — single payment over $50,000
 * 3. Daily limit check — total over $100,000 in 24 hours
 * 4. Suspicious amount patterns — round numbers over $10,000
 *
 * In production this would integrate with ML models (e.g. AWS Fraud Detector)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final BigDecimal SINGLE_PAYMENT_LIMIT = new BigDecimal("50000.00");
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("100000.00");
    private static final int SUSPICIOUS_VELOCITY_LIMIT = 5;
    private static final String FRAUD_SCORE_PREFIX = "fraud:score:";

    public String checkFraud(String accountId, BigDecimal amount, String currency) {
        int riskScore = 0;

        // Rule 1: Amount threshold
        if (amount.compareTo(SINGLE_PAYMENT_LIMIT) > 0) {
            riskScore += 40;
            log.warn("High amount payment detected for account: {} amount: {}", accountId, amount);
        }

        // Rule 2: Velocity check — too many payments in short time
        long recentPayments = paymentRepository.countPaymentsSince(
            accountId, LocalDateTime.now().minusHours(1)
        );
        if (recentPayments > VELOCITY_LIMIT) {
            riskScore += 35;
            log.warn("High velocity payment detected for account: {} count: {}", accountId, recentPayments);
        }

        // Rule 3: Daily limit check
        BigDecimal dailyTotal = paymentRepository.sumCompletedPaymentsSince(
            accountId, LocalDateTime.now().minusHours(24)
        );
        if (dailyTotal != null && dailyTotal.compareTo(DAILY_LIMIT) > 0) {
            riskScore += 25;
            log.warn("Daily limit exceeded for account: {} total: {}", accountId, dailyTotal);
        }

        // Rule 4: Suspicious round number pattern
        if (amount.compareTo(new BigDecimal("10000")) > 0 &&
            amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0) {
            riskScore += 10;
        }

        String fraudScore = String.valueOf(riskScore);

        // Cache fraud score
        redisTemplate.opsForValue().set(
            FRAUD_SCORE_PREFIX + accountId,
            fraudScore,
            5, TimeUnit.MINUTES
        );

        log.info("Fraud score for account {}: {}", accountId, fraudScore);
        return fraudScore;
    }

    public boolean isFraudulent(String fraudScore) {
        try {
            return Integer.parseInt(fraudScore) >= 70;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
