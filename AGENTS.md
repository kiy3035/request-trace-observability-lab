# AGENTS.md

## 1. 저장소 목적

이 저장소는 기능이 많은 서비스를 만드는 프로젝트가 아니라, 백엔드 요청 추적과 로그 관측 흐름을 재현하는 작은 실험 프로젝트다.

최종적으로 아래 흐름을 직접 확인할 수 있어야 한다.

```text
HTTP Request
→ Filter
→ MDC
→ Controller
→ AOP
→ Service
→ Spring Data JPA
→ 실제 SQL 실행
→ JSON Log
→ Grafana Alloy
→ Loki
→ Grafana
```

핵심 목표는 하나의 HTTP 요청이 어떤 Controller, Service, SQL을 거쳤는지 동일한 `traceId`로 추적하고, 느린 구간과 오류를 Grafana에서 육안으로 확인하는 것이다.

---

## 2. 사용 기술

무료 또는 오픈소스만 사용한다.

- Java 21
- Spring Boot 3.x
- Gradle Wrapper
- Spring Web
- Spring AOP
- Spring Data JPA
- PostgreSQL
- SLF4J + Logback
- JSON 로그 인코더
- datasource-proxy (`net.ttddyy:datasource-proxy`)
- Grafana Alloy
- Grafana Loki
- Grafana
- Docker Compose
- JUnit 5 / Spring Boot Test

유료 SaaS, 유료 모니터링 제품은 사용하지 않는다.

---

## 3. 프로젝트 범위

비즈니스 기능은 최대한 작게 유지한다.

`Order` 도메인 하나만 사용한다.

API:

- `POST /api/orders`
- `GET /api/orders/{id}`
- `PUT /api/orders/{id}`
- `DELETE /api/orders/{id}`

권장 컬럼:

- id
- product_name
- quantity
- created_at
- updated_at

관측 실험을 위해서만 아래 Demo API를 추가할 수 있다.

- Slow Request 재현
- Slow SQL 재현
- Error 재현
- Async MDC 유실 재현
- Async MDC 전파 재현

아래 기술은 명시적인 추가 요구가 없는 한 넣지 않는다.

- Redis
- Kafka
- RabbitMQ
- Kubernetes
- 인증/인가
- 복잡한 프론트엔드
- Microservice
- OpenTelemetry
- Tempo
- Jaeger

---

## 4. 설계 규칙

### 4.1 HTTP 요청 Trace ID

HTTP 요청의 시작과 끝은 Servlet Filter에서 관리한다.

가능하면 `OncePerRequestFilter`를 사용한다.

요청 진입 시:

1. `X-Trace-Id` 헤더가 있으면 확인한다.
2. UUID 형식이 정상이라면 재사용한다.
3. 없거나 올바르지 않으면 새로운 UUID를 생성한다.
4. MDC에 `traceId` 키로 저장한다.
5. 응답 Header에도 `X-Trace-Id`를 넣는다.

요청 종료 시 반드시 `finally`에서 MDC를 정리한다.

Servlet Thread는 재사용될 수 있으므로 MDC 값이 다음 요청으로 누수되면 안 된다.

### 4.2 AOP 역할

AOP는 traceId를 생성하지 않는다.

AOP 역할:

- Controller 실행시간 측정
- Service 실행시간 측정
- 느린 Service 자동 판별
- 공통 실행 로그 기록

로그에는 가능한 범위에서 아래 필드를 포함한다.

- traceId
- layer
- event
- class
- method
- elapsedMs
- slow
- thread

Pointcut은 필요한 Controller/Service 패키지에만 좁게 적용한다.

### 4.3 SQL 실행시간 측정

Hibernate `StatementInspector`를 SQL 실행시간 측정 용도로 사용하지 않는다.

이유:
`StatementInspector`는 SQL 실행 전에 SQL 문자열을 확인하거나 변경하는 용도이며, 실제 JDBC 실행 완료 시간을 정확히 측정하는 도구가 아니다.

실제 SQL 실행시간은 `datasource-proxy`를 사용해 DataSource를 감싸서 측정한다.

SQL 로그에는 가능한 범위에서 아래 정보를 남긴다.

- traceId
- layer=SQL
- event=SQL_EXECUTION
- sqlOperation
- sql
- elapsedMs
- slow
- success
- exception

민감정보, 비밀번호, 토큰, DB Credential은 로그에 남기지 않는다.

Bind Parameter 전체 출력은 기본값으로 사용하지 않는다.

### 4.4 Slow 기준

Threshold는 코드에 하드코딩하지 않고 설정값으로 관리한다.

기본값 예시:

- Slow Service / HTTP: 500ms
- Slow SQL: 200ms

### 4.5 Async MDC 전파

MDC는 ThreadLocal 기반이므로 Thread Pool을 넘어가면 자동 전파되지 않는다.

프로젝트에서는 반드시 아래 두 경우를 모두 재현한다.

1. MDC 전파가 없는 Executor
2. `TaskDecorator`로 MDC를 전파하는 Executor

잘못된 상태도 일부러 남겨서 차이를 확인할 수 있어야 한다.

Worker Thread에서 실행이 끝난 뒤 MDC를 안전하게 복원하거나 제거해야 한다.

### 4.6 로그 형식

관측용 애플리케이션 로그는 JSON으로 남긴다.

권장 필드:

- timestamp
- level
- application
- environment
- traceId
- layer
- event
- logger
- thread
- elapsedMs
- slow
- status
- exception

### 4.7 Loki Label 규칙

`traceId`를 Loki Label로 만들지 않는다.

이유:
요청마다 값이 달라지는 high-cardinality 데이터이기 때문이다.

Loki Label은 낮은 cardinality만 사용한다.

예:

- application
- environment
- level

다음 값은 JSON Field로 유지한다.

- traceId
- uri
- method
- elapsedMs
- sql
- businessKey

Grafana/LogQL에서 JSON을 파싱해서 검색한다.

---

## 5. 반드시 재현할 시나리오

### Scenario A - 정상 요청

Order API를 호출한다.

동일한 traceId가 아래 로그에 모두 존재해야 한다.

- REQUEST_START
- Controller
- Service
- SQL
- REQUEST_END

### Scenario B - Slow Service / Slow Request

의도적으로 느린 Service를 호출한다.

기대 결과:

- 설정된 Threshold 초과
- `slow=true`
- Slow Event 로그 발생
- Grafana에서 느린 요청 목록 확인 가능

### Scenario C - Slow SQL

PostgreSQL에서 의도적으로 느린 SQL을 실행한다.

`pg_sleep` 기반 Demo Query 사용 가능.

기대 결과:

- datasource-proxy가 실제 JDBC 실행시간 측정
- `layer=SQL`
- `slow=true`
- 동일한 HTTP traceId 유지

### Scenario D - Error 추적

의도적인 예외를 발생시킨다.

기대 결과:

- Error 이전 로그와 Error 로그에 동일한 traceId 존재
- traceId 하나로 해당 요청 전체 흐름 조회 가능

### Scenario E - Async MDC 유실과 해결

전파 없는 Async:

- 호출 Thread에는 traceId 존재
- Worker Thread에는 traceId 없음

TaskDecorator 적용 Async:

- 호출 Thread와 Worker Thread의 traceId 동일

---

## 6. Grafana 최종 목표

일반적인 로그 나열 화면이 아니라 Troubleshooting Dashboard를 만든다.

필수 화면:

1. 전체 HTTP 요청 수
2. Error 건수
3. Slow Request 건수
4. Slow SQL 건수
5. 최근 Error + traceId
6. 가장 느린 요청
7. 가장 느린 SQL
8. traceId 직접 검색
9. 선택한 traceId의 시간순 요청 흐름

예:

```text
REQUEST_START
→ CONTROLLER
→ SERVICE
→ SQL_EXECUTION
→ SERVICE_END
→ CONTROLLER_END
→ REQUEST_END
```

이번 프로젝트에서는 Prometheus를 넣지 않는다.

가능하면 Loki Log 기반으로 Dashboard를 구성한다.

---

## 7. Docker / Alloy / Loki 규칙

최종 Docker Compose 구성:

- app
- postgres
- alloy
- loki
- grafana

Spring Boot WAS는 1대만 사용한다.

Nginx, Load Balancer, 두 번째 WAS는 넣지 않는다.

권장 로그 수집 흐름:

```text
Spring Boot
→ JSON Log File
→ Alloy
→ Loki
→ Grafana
```

Grafana Datasource와 Dashboard Provisioning 설정도 가능한 범위에서 Git에 포함한다.

---

## 8. 코딩 규칙

- Constructor Injection 사용
- Field Injection 금지
- 불필요한 Interface 계층 금지
- 과도한 DDD 구조 금지
- Demo 코드는 일반 Order 기능과 구분
- 예외를 삼키지 않는다
- 로그를 위해 원래 예외 흐름을 바꾸지 않는다
- 비밀번호, Token, Authorization Header를 로그에 남기지 않는다
- SQL 로그 크기를 무제한으로 키우지 않는다
- UTF-8 사용
- 주석은 설계 이유가 필요한 곳에만 작성

---

## 9. 테스트 규칙

컴파일 성공만으로 완료 처리하지 않는다.

최소 검증 대상:

- traceId가 없을 때 생성
- 정상 UUID Header 재사용
- 잘못된 traceId 교체
- 응답 Header에 X-Trace-Id 존재
- 요청 종료 후 MDC 정리
- AOP 실행시간 측정
- Slow Threshold 판별
- Slow SQL 판별
- Async MDC 전파
- Async 실행 후 Worker MDC 정리

환경상 실행하지 못한 테스트는 성공했다고 작성하지 않는다.

실행하지 못했다면 `NOT RUN` 또는 `BLOCKED`로 기록한다.

---

## 10. 측정 결과 규칙

이 저장소는 실험 프로젝트다.

각 주요 시나리오마다 실제 결과를 남긴다.

- 요청
- 기대 로그
- 실제 로그
- traceId
- elapsedMs
- 성공 여부

직접 측정하지 않은 숫자는 작성하지 않는다.

실행하지 않은 항목은 `not measured`로 표시한다.

---

## 11. Codex 작업 방식

작업 전 반드시 아래 파일을 순서대로 읽는다.

1. `AGENTS.md`
2. `PROJECT_SPEC.md`
3. `PROGRESS.md`

사용자가 요청한 Phase만 구현한다.

한 번에 전체 프로젝트를 구현하지 않는다.

작업 후:

1. 관련 테스트 실행
2. Build 실행
3. 가능한 경우 실제 Scenario 실행
4. 변경 파일 확인
5. `PROGRESS.md`에 실제 결과 기록
6. 다음 Phase를 구현하지 않고 종료

---

## 12. 완료 조건

최종적으로 사용자가 아래 작업을 할 수 있어야 한다.

1. 로컬 Stack 실행
2. HTTP API 호출
3. 응답의 `X-Trace-Id` 확인
4. Grafana 접속
5. 해당 traceId 검색
6. Controller → Service → SQL → Response 흐름 확인
7. Slow Service 재현
8. Slow SQL 재현
9. Error 재현
10. Async MDC 유실과 TaskDecorator 해결 결과 비교

프로젝트는 관측 설계가 주인공이어야 하며, 비즈니스 기능이 프로젝트를 덮어서는 안 된다.
