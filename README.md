# Banking Payment Platform

A production-grade, high-throughput payment processing platform built with Java 17, Spring Boot 3.x, Apache Kafka, Redis, and PostgreSQL. Designed to handle millions of transactions per day with fraud detection, idempotency guarantees, and real-time event streaming.

## Architecture

```
                         ┌──────────────────────────────────────┐
                         │         API Gateway (Port 8080)       │
                         │      Rate Limiting | Auth | Routing   │
                         └──────────────┬───────────────────────┘
                                        │
              ┌─────────────────────────┼──────────────────────────┐
              │                         │                          │
              ▼                         ▼                          ▼
   ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
   │ Payment Service │       │ Account Service │       │  Notification   │
   │   (Port 8081)   │       │   (Port 8082)   │       │    Service      │
   └────────┬────────┘       └────────┬────────┘       └────────┬────────┘
            │                         │                          │
            ▼                         ▼                          │
   ┌─────────────────┐       ┌─────────────────┐                │
   │   PostgreSQL    │       │   PostgreSQL    │                │
   │   (paymentsdb)  │       │  (accountsdb)   │                │
   └─────────────────┘       └─────────────────┘                │
            │                                                    │
            ▼                                                    │
   ┌─────────────────┐                                          │
   │      Redis      │◀────────────────────────────────────────┘
   │  Cache + Rate   │
   │    Limiting     │
   └─────────────────┘
            │
            ▼
   ┌─────────────────────────────────────────┐
   │              Apache Kafka               │
   │  payment-initiated | payment-completed  │
   │  payment-failed | fraud-check           │
   └─────────────────────────────────────────┘
```

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Primary language |
| Spring Boot | 3.2.0 | Application framework |
| Spring Security | 6.x | JWT Auth + RBAC |
| Apache Kafka | 3.x | Event streaming |
| Redis | 7.x | Caching + Idempotency + Rate limiting |
| PostgreSQL | 15 | Primary database |
| Docker | Latest | Containerization |
| Prometheus + Grafana | Latest | Observability |
| Swagger/OpenAPI | 3.x | API documentation |

## Key Features

### Payment Processing
- **Idempotent payments** — Redis-backed deduplication prevents duplicate transactions
- **Multiple payment types** — Domestic transfer, international wire, ACH, bill payment, P2P
- **Multi-currency support** — USD, EUR, GBP and more
- **Full audit trail** — Every state change logged with timestamp

### Fraud Detection
Rule-based fraud engine with configurable thresholds:
- **Velocity check** — Flags accounts with 10+ payments in one hour
- **Amount threshold** — Single payment over $50,000 triggers review
- **Daily limit** — Total over $100,000 in 24 hours flagged
- **Pattern detection** — Suspicious round-number patterns

### Event-Driven Architecture
Every payment state change publishes to Kafka:
| Topic | Trigger |
|-------|---------|
| `payment-initiated` | New payment submitted |
| `payment-completed` | Payment successfully processed |
| `payment-failed` | Payment processing failed |
| `fraud-check` | Fraud detected on payment |

### Performance
- Redis cache-aside pattern on payment reads
- Database indexes on reference number, account ID, status, and created date
- Pagination on all list endpoints
- Async Kafka publishing

## Quick Start

### Prerequisites
- Java 17+
- Docker and Docker Compose

### Start Everything

```bash
git clone https://github.com/YOUR_USERNAME/banking-payment-platform.git
cd banking-payment-platform

# Start all services
docker-compose up -d

# Check logs
docker-compose logs -f payment-service
```

Services available at:
- **Payment API**: http://localhost:8081
- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **Kafka UI**: http://localhost:8090
- **Redis**: localhost:6379

## API Usage

### 1. Register and Login
```bash
# Register
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123","email":"john@bank.com","role":"USER"}'

# Login — get JWT token
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}'
```

### 2. Initiate a Payment
```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "senderAccountId": "ACC-001",
    "senderName": "John Doe",
    "receiverAccountId": "ACC-002",
    "receiverName": "Jane Smith",
    "amount": 500.00,
    "currency": "USD",
    "type": "DOMESTIC_TRANSFER",
    "description": "Rent payment",
    "idempotencyKey": "unique-key-12345"
  }'
```

### 3. Check Payment Status
```bash
curl http://localhost:8081/api/v1/payments/reference/PAY-XXXXXXX-XXXXXXXX \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 4. Get Account Payment History
```bash
curl "http://localhost:8081/api/v1/payments/account/ACC-001?page=0&size=10" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Payment Status Flow

```
INITIATED ──▶ PROCESSING ──▶ COMPLETED
    │                             
    ├──▶ FRAUD_DETECTED           
    │                             
    └──▶ FAILED                   
              │
              └──▶ REVERSED
```

## User Roles

| Role | Permissions |
|------|-------------|
| ADMIN | Full access |
| BANKER | Process, complete, fail payments |
| USER | Initiate payments, view own transactions |

## Running Tests

```bash
cd payment-service
mvn test
```

## Project Structure

```
banking-payment-platform/
├── payment-service/
│   ├── src/main/java/com/banking/payment/
│   │   ├── PaymentServiceApplication.java
│   │   ├── controller/     # REST endpoints
│   │   ├── service/        # Business logic + fraud detection
│   │   ├── repository/     # JPA repositories
│   │   ├── model/          # JPA entities
│   │   ├── dto/            # Request/Response objects
│   │   ├── kafka/          # Event producers/consumers
│   │   ├── config/         # Security, Redis config
│   │   └── exception/      # Error handling
│   └── src/test/           # Unit tests
├── docker-compose.yml
└── README.md
```

## Why This Architecture

- **Microservices** — Each service owns its domain and can scale independently
- **Idempotency** — Prevents double-charging on retries — critical for payments
- **Event sourcing** — Kafka topics create a full audit trail of every payment event
- **Redis caching** — Hot payment reads served from memory, not DB
- **Fraud detection** — Rules engine runs before every payment is persisted

## Author

**Shanmukha Sai Ram Tummuri**
- LinkedIn: [linkedin.com/in/shanmukh-t-26a2aa400](https://www.linkedin.com/in/shanmukh-t-26a2aa400/)
- Email: shanmukhsairam84@gmail.com
