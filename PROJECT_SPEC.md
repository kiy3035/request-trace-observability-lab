# PROJECT_SPEC.md

## 1. 프로젝트명

`request-trace-observability-lab`

## 2. 한 줄 소개

Spring Boot 요청에 Trace ID를 발급하고 MDC로 Controller·Service·JPA/JDBC SQL 로그를 연결한 뒤, Slow Request·Slow SQL·Error·Async MDC 유실을 재현하고 Alloy·Loki·Grafana에서 요청 전체 흐름을 추적하는 로컬 Observability 실험 프로젝트.

---

## 3. 문제 정의

단순 로그만 남기면 아래 질문에 빠르게 답하기 어렵다.

- 특정 HTTP 요청이 어떤 Controller와 Service를 거쳤는가?
- 어떤 SQL이 실행되었는가?
- 요청이 느린 이유가 Service인지 SQL인지 어떻게 확인할 것인가?
- Error가 발생하기 전 동일 요청에서 어떤 일이 있었는가?
- Async 실행으로 Thread가 바뀌었을 때 traceId가 유지되는가?
- 여러 로그를 중앙 저장소에서 요청 단위로 어떻게 조회할 것인가?

이 프로젝트는 위 문제를 최소한의 Spring Boot 애플리케이션으로 직접 재현하고 해결한다.

---

## 4. 전체 구조

```text
Client
  |
  v
Spring Boot
  |
  +--> TraceIdFilter
  |      - UUID 검증/생성
  |      - MDC.put(traceId)
  |      - X-Trace-Id 응답 Header
  |
  +--> Controller
  |      - AOP 실행시간 측정
  |
  +--> Service
  |      - AOP 실행시간 측정
  |      - Slow Service 판별
  |
  +--> Spring Data JPA
  |
  +--> Proxy DataSource
         - datasource-proxy
         - 실제 JDBC 실행시간 측정
         - Slow SQL 판별
  |
  v
PostgreSQL

JSON Log
  |
  v
Grafana Alloy
  |
  v
Loki
  |
  v
Grafana
```

WAS는 1대만 사용한다.

---

## 5. 핵심 기술 결정

### 5.1 traceId 기준

HTTP 요청 1개당 traceId 1개를 사용한다.

기본 형식:

- UUID v4

Header:

- Request: `X-Trace-Id`
- Response: `X-Trace-Id`

처리 방식:

- 정상 UUID Header가 있으면 재사용
- 없거나 형식이 잘못되면 새 UUID 생성

### 5.2 MDC

traceId를 MDC에 저장한다.

같은 Request Thread에서 발생하는 Controller, Service, SQL 로그가 자동으로 동일한 traceId를 공유하도록 한다.

요청 종료 시 반드시 MDC를 정리한다.

### 5.3 AOP

Spring AOP로 Controller와 Service의 실행시간을 측정한다.

AOP에서 traceId를 생성하지 않는다.

로그 예시:

- CONTROLLER_END
- SERVICE_END
- SLOW_SERVICE

주요 필드:

- layer
- class
- method
- elapsedMs
- slow
- traceId

### 5.4 SQL 실행시간

JPA 아래 DataSource를 datasource-proxy로 감싼다.

목적:

- 실제 JDBC 실행시간 측정
- SQL Operation 구분
- Slow SQL 판별

`StatementInspector`를 SQL 실행시간 측정 도구라고 설명하지 않는다.

로그 예시:

```json
{
  "traceId": "0b4...",
  "layer": "SQL",
  "event": "SQL_EXECUTION",
  "sqlOperation": "INSERT",
  "elapsedMs": 18,
  "slow": false,
  "success": true
}
```

### 5.5 Slow 기준

기본값:

```yaml
observability:
  slow:
    service-ms: 500
    sql-ms: 200
```

설정값으로 변경 가능해야 한다.

### 5.6 Async 실험

두 개의 Executor 또는 두 경로를 분리한다.

1. MDC 전파 없음
2. `TaskDecorator`로 MDC 전파

목적:

MDC가 ThreadLocal 기반임을 직접 확인하고, Thread가 바뀔 때 Context가 유실되는 문제를 재현한다.

### 5.7 Loki Cardinality

Loki Label 권장:

- application
- environment

선택:

- level

Label로 사용 금지:

- traceId
- orderId
- SQL
- path variable이 포함된 URI

traceId는 JSON Field로 유지한다.

---

## 6. 최소 도메인

Entity:

`Order`

권장 스키마:

```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

검증:

- productName: 빈 문자열 금지
- quantity: 1 이상

API:

- POST `/api/orders`
- GET `/api/orders/{id}`
- PUT `/api/orders/{id}`
- DELETE `/api/orders/{id}`

이 기능은 실제 서비스 기능을 만들기 위한 것이 아니라 Controller → Service → JPA → SQL 흐름을 만들기 위한 최소 기능이다.

---

## 7. Demo API

Demo 기능은 `/api/demo` 아래에 둔다.

### Slow Service

예:

`GET /api/demo/slow-service?delayMs=700`

목적:

- Service Threshold 초과
- SLOW_SERVICE 로그 발생
- Grafana Slow Request 화면 확인

delay 값에는 최대 제한을 둔다.

### Slow SQL

예:

`GET /api/demo/slow-sql?delayMs=300`

PostgreSQL `pg_sleep` 기반 Native Query 사용 가능.

목적:

- 실제 JDBC 실행시간 측정
- Slow SQL 로그 생성

### Error

예:

`GET /api/demo/error`

목적:

- 의도적인 예외 발생
- Error 이전 로그와 Error 로그의 traceId 연결 확인

### Async MDC 유실

예:

`POST /api/demo/async/lost`

기대 결과:

- Request Thread에는 traceId 있음
- Worker Thread에는 traceId 없음

### Async MDC 전파

예:

`POST /api/demo/async/propagated`

기대 결과:

- Request Thread와 Worker Thread의 traceId 동일

---

## 8. 구조화 로그 Event

권장 Event:

- REQUEST_START
- REQUEST_END
- CONTROLLER_END
- SERVICE_END
- SLOW_SERVICE
- SQL_EXECUTION
- SLOW_SQL
- APPLICATION_ERROR
- ASYNC_CALLER
- ASYNC_WORKER

공통 필드:

```text
timestamp
level
application
environment
traceId
layer
event
thread
elapsedMs
slow
```

HTTP 관련:

- httpMethod
- uri
- status

Method 관련:

- class
- method

SQL 관련:

- sqlOperation
- sql
- success

Error 관련:

- exception
- errorMessage

---

## 9. 단계별 작업 계획

### Phase 1 - 기본 애플리케이션

목표:

Spring Boot + PostgreSQL + JPA 기반 Order CRUD를 만든다.

결과물:

- Gradle 프로젝트
- Java 21
- Order Entity
- Repository
- Service
- Controller
- Validation
- Error Handling
- PostgreSQL 설정
- 기본 테스트

관측 기능은 아직 넣지 않는다.

---

### Phase 2 - HTTP Trace ID

목표:

HTTP 요청 단위 traceId를 추가한다.

결과물:

- TraceIdFilter
- UUID 검증 및 생성
- MDC 저장
- X-Trace-Id 응답 Header
- REQUEST_START / REQUEST_END 로그
- MDC Cleanup
- 테스트

---

### Phase 3 - AOP 실행시간 측정

목표:

Controller와 Service 실행시간을 측정한다.

결과물:

- AOP Pointcut
- elapsedMs
- Slow Service Threshold
- SERVICE_END
- SLOW_SERVICE
- 테스트

---

### Phase 4 - JPA/JDBC SQL 실행시간 측정

목표:

실제 JDBC SQL 실행시간을 측정한다.

결과물:

- datasource-proxy
- Query Execution Listener
- sqlOperation
- elapsedMs
- Slow SQL Threshold
- Slow SQL Demo
- 테스트

---

### Phase 5 - Async MDC 유실 및 해결

목표:

Thread 전환 시 MDC 유실을 재현하고 해결한다.

결과물:

- MDC 전파 없는 Executor
- TaskDecorator
- MDC 전파 Executor
- Demo API 2개
- 테스트

---

### Phase 6 - JSON Log + Alloy + Loki + Grafana

목표:

애플리케이션 로그를 중앙 저장소로 보낸다.

결과물:

- JSON Logback 설정
- Dockerfile
- Docker Compose
- Alloy 설정
- Loki 설정
- Grafana Datasource 설정

검증:

- Spring Log가 Loki에 저장
- JSON Field 파싱 가능
- traceId가 Label이 아님

---

### Phase 7 - Grafana Troubleshooting Dashboard

목표:

실제 서비스 운영 화면처럼 육안으로 상태를 확인한다.

필수 항목:

- 전체 요청 수
- Error 수
- Slow Request 수
- Slow SQL 수
- 최근 Error
- 느린 요청 TOP
- 느린 SQL TOP
- traceId 검색
- 시간순 요청 Flow 조회

---

### Phase 8 - 문서화 및 실험 결과 정리

목표:

포트폴리오와 블로그에 활용할 수 있게 정리한다.

포함 내용:

- 전체 아키텍처
- Filter와 AOP 역할 차이
- MDC 동작 원리
- datasource-proxy 선택 이유
- traceId를 Loki Label로 만들지 않은 이유
- Async MDC 유실 원인
- TaskDecorator 해결 방식
- 실제 실행 방법
- 실제 측정 결과
- Grafana 결과 화면

실행하지 않은 수치는 작성하지 않는다.

---

## 10. 하지 않을 것

추가 요구가 없다면 구현하지 않는다.

- WAS 2대
- Nginx
- Redis
- Kafka
- RabbitMQ
- Kubernetes
- Prometheus
- OpenTelemetry
- Tempo
- Jaeger
- 인증/인가
- 복잡한 도메인
- 별도 Frontend

프로젝트의 주인공은 로그 추적과 관측이다.
