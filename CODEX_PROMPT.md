# CODEX_PROMPT.md

이 저장소는 일반 CRUD 서비스가 아니라 **HTTP 요청 단위 로그 추적과 장애/지연 분석을 직접 재현하는 Backend Observability Lab**이다.

이번 실행에서는 전체 프로젝트를 한 번에 만들지 말고 **Phase 1만 구현하고 검증한 뒤 반드시 멈춰라.**

---

## 작업 전에 반드시 읽을 문서

저장소 Root의 아래 파일을 순서대로 읽어라.

1. `AGENTS.md`
2. `PROJECT_SPEC.md`
3. `PROGRESS.md`

위 문서의 규칙을 우선한다.

현재 코드가 이미 존재한다면 전체를 갈아엎지 말고 먼저 구조를 파악한 뒤 최소 변경으로 요구사항을 만족시켜라.

---

## 프로젝트 최종 목표

최종적으로 아래 흐름을 실제로 관측할 수 있어야 한다.

```text
HTTP Request
→ TraceIdFilter
→ MDC
→ Controller
→ AOP
→ Service
→ Spring Data JPA
→ datasource-proxy
→ PostgreSQL
→ JSON Log
→ Grafana Alloy
→ Loki
→ Grafana
```

후속 단계에서 아래 세 가지를 핵심 실험으로 구현할 예정이다.

1. Controller / Service / SQL 구간별 실행시간 측정
2. Slow Request / Slow SQL 자동 탐지
3. Async 환경에서 MDC 유실 재현 후 `TaskDecorator`로 해결

하지만 이번 Phase 1에서는 위 Observability 기능을 미리 구현하지 않는다.

이번 단계의 목적은 비교 가능한 정상 Baseline 애플리케이션을 만드는 것이다.

---

# 이번 작업 범위

## Phase 1 - 기본 애플리케이션

### 목표

Java 21 + Spring Boot + Spring Data JPA + PostgreSQL 기반의 아주 작은 Order CRUD 애플리케이션을 만든다.

비즈니스 기능은 이후 Observability 실험용 트래픽을 만들기 위한 최소 수준만 구현한다.

---

## 1. 기본 프로젝트

사용:

- Java 21
- Spring Boot 3.x
- Gradle Wrapper
- Spring Web
- Spring Data JPA
- Validation
- PostgreSQL Driver
- Spring Boot Test

서로 호환되는 안정 버전을 사용한다.

불필요한 라이브러리를 추가하지 않는다.

---

## 2. Order 도메인

필드:

- id
- productName
- quantity
- createdAt
- updatedAt

Validation:

- productName은 blank 금지
- quantity는 1 이상

Repository:

- Spring Data JPA

Service:

- CRUD 처리
- 필요한 Transaction Boundary 명시

Controller:

- REST API

Entity를 API Request/Response DTO로 직접 사용하지 않는다.

---

## 3. API

아래 API를 구현한다.

- `POST /api/orders`
- `GET /api/orders/{id}`
- `PUT /api/orders/{id}`
- `DELETE /api/orders/{id}`

존재하지 않는 id:

- 일관된 404 응답

Validation 실패:

- 일관된 400 응답

과도한 공통 Response Wrapper는 만들지 않는다.

---

## 4. PostgreSQL

로컬 실행을 위한 최소 Docker Compose를 만들어도 된다.

단, 이번 Phase에서는 아래를 넣지 않는다.

- Alloy
- Loki
- Grafana

DB Schema 생성 방식은 로컬 Lab에 적합한 단순한 방식을 선택한다.

선택 이유를 README 또는 설정 주석에 짧게 기록한다.

---

## 5. 테스트

최소 테스트:

- Order 생성
- Order 조회
- Order 수정
- Order 삭제
- Validation 실패
- 없는 Order 조회

가능하면 Spring Integration Test를 사용한다.

Docker/PostgreSQL 실행이 불가능하면 성공했다고 꾸미지 말고 `PROGRESS.md`에 `BLOCKED` 또는 `NOT RUN`으로 기록한다.

H2로 PostgreSQL 검증을 성공한 것처럼 대체하지 않는다.

---

# 이번 Phase에서 구현 금지

아래는 후속 Phase이므로 지금 구현하지 않는다.

- TraceIdFilter
- MDC
- AOP 실행시간 측정
- Slow Threshold
- datasource-proxy
- SQL Timing Listener
- Async Executor
- TaskDecorator
- JSON Logging
- Grafana Alloy
- Loki
- Grafana
- Nginx
- 두 번째 WAS
- Redis
- Kafka
- Prometheus
- OpenTelemetry

"나중을 위해 미리 넣어두겠다"는 이유로 선행 구현하지 않는다.

---

# 코드 규칙

- Constructor Injection 사용
- Field Injection 금지
- 불필요한 Interface 계층 금지
- Repository / Service / Controller 역할을 단순하게 유지
- 과도한 DDD 구조 금지
- 예외를 삼키지 않는다
- Credential 하드코딩 금지
- UTF-8
- 테스트 없는 임의 리팩터링 금지

---

# 완료 전 검증

가능한 범위에서 실제 명령을 실행한다.

예:

```bash
./gradlew clean test
./gradlew build
```

Docker 사용이 가능하면 PostgreSQL 실행 후 애플리케이션 기동과 CRUD 동작도 확인한다.

실제로 실행하지 않은 것은 성공했다고 작성하지 않는다.

---

# 작업 완료 후

`PROGRESS.md`의 Phase 1 내용을 실제 결과로 갱신한다.

반드시 기록:

- 변경한 주요 파일
- 실행한 명령
- Build 결과
- Test 결과
- 애플리케이션 실행 여부
- CRUD 검증 여부
- Blocker
- 다음 단계가 `Phase 2 - HTTP Trace ID`라는 사실

그리고 **Phase 2는 구현하지 말고 종료한다.**
