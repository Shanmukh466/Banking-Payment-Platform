package com.banking.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Banking Payment Platform - Payment Service
 *
 * High-throughput payment processing microservice featuring:
 * - Idempotent payment processing with Redis-backed deduplication
 * - Real-time fraud detection with configurable risk rules
 * - Kafka event streaming for payment lifecycle events
 * - JWT-based authentication with RBAC
 * - Redis caching for high-performance reads
 * - Full audit trail for compliance
 */
@SpringBootApplication
@EnableAsync
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
