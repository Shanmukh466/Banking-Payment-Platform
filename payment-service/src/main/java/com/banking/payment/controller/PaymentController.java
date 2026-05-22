package com.banking.payment.controller;

import com.banking.payment.dto.PaymentDto;
import com.banking.payment.model.Payment;
import com.banking.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Payment processing API")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate a new payment with idempotency guarantee")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'BANKER')")
    public ResponseEntity<PaymentDto.Response> initiatePayment(
        @Valid @RequestBody PaymentDto.InitiateRequest request
    ) {
        log.info("Payment initiation request from account: {}", request.getSenderAccountId());
        PaymentDto.Response response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Mark payment as completed")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANKER')")
    public ResponseEntity<PaymentDto.Response> completePayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.completePayment(id));
    }

    @PutMapping("/{id}/fail")
    @Operation(summary = "Mark payment as failed with reason")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANKER')")
    public ResponseEntity<PaymentDto.Response> failPayment(
        @PathVariable UUID id,
        @RequestParam String reason
    ) {
        return ResponseEntity.ok(paymentService.failPayment(id, reason));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID (cached)")
    public ResponseEntity<PaymentDto.Response> getPaymentById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/reference/{referenceNumber}")
    @Operation(summary = "Get payment by reference number")
    public ResponseEntity<PaymentDto.Response> getPaymentByReference(
        @PathVariable String referenceNumber
    ) {
        return ResponseEntity.ok(paymentService.getPaymentByReference(referenceNumber));
    }

    @GetMapping
    @Operation(summary = "Get all payments with pagination")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANKER')")
    public ResponseEntity<Page<PaymentDto.Response>> getAllPayments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String direction
    ) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        return ResponseEntity.ok(paymentService.getAllPayments(PageRequest.of(page, size, sort)));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get payments by account ID")
    public ResponseEntity<Page<PaymentDto.Response>> getPaymentsByAccount(
        @PathVariable String accountId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByAccount(accountId, pageable));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get payments by status")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANKER')")
    public ResponseEntity<Page<PaymentDto.Response>> getPaymentsByStatus(
        @PathVariable Payment.PaymentStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByStatus(status, pageable));
    }
}
