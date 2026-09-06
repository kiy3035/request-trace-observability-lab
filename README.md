# Request Trace Observability Lab

HTTP 요청 하나가 Controller, Service, 실제 JDBC SQL을 거쳐 응답되기까지 같은 `traceId`로 추적하고, Alloy, Loki, Grafana에서 지연과 오류를 분석하는 로컬 실험 프로젝트입니다.

## Phase 1 실행

요구 사항:

- Java 21
- Docker Desktop

PostgreSQL과 애플리케이션을 실행합니다.

```bash
docker compose up -d postgres
./gradlew bootRun
```

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용할 수 있습니다. 기본 DB 연결 값은 로컬 실험 전용이며 환경 변수 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`로 덮어쓸 수 있습니다.

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productName":"keyboard","quantity":1}'
curl -i http://localhost:8080/api/orders/1
curl -i -X PUT http://localhost:8080/api/orders/1 \
  -H "Content-Type: application/json" \
  -d '{"productName":"mechanical keyboard","quantity":2}'
curl -i -X DELETE http://localhost:8080/api/orders/1
```

Hibernate의 `ddl-auto=update`를 사용합니다. 마이그레이션 이력보다 관측 흐름 재현에 집중하는 로컬 Lab이며, 별도 마이그레이션 도구 없이 스택을 바로 실행하기 위한 선택입니다.

모든 HTTP 응답에는 `X-Trace-Id`가 포함됩니다. 요청 헤더에 canonical UUID를 보내면 그대로 재사용하고, 없거나 잘못된 값이면 새 UUID를 발급합니다.

```bash
curl -i http://localhost:8080/api/orders/1 \
  -H "X-Trace-Id: 550e8400-e29b-41d4-a716-446655440000"
```

Controller와 Service 실행시간은 AOP가 기록합니다. 기본 Slow HTTP/Service 기준은 500ms이며 `SLOW_HTTP_MS`, `SLOW_SERVICE_MS`로 바꿀 수 있습니다.

```bash
curl -i "http://localhost:8080/api/demo/slow-service?delayMs=700"
```

실제 JDBC 실행시간은 `datasource-proxy`가 측정합니다. 기본 Slow SQL 기준은 200ms이며 `SLOW_SQL_MS`로 변경할 수 있습니다. SQL은 공백을 정규화하고 최대 2,000자로 제한하며 Bind Parameter 값은 출력하지 않습니다.

```bash
curl -i "http://localhost:8080/api/demo/slow-sql?delayMs=300"
```

MDC가 일반 Thread Pool에서 유실되는 경우와 `TaskDecorator`로 전파되는 경우를 비교합니다. 응답의 `callerTraceId`, `workerTraceId`, `workerThread`로 차이를 바로 확인할 수 있습니다.

```bash
curl -i -X POST http://localhost:8080/api/demo/async/lost
curl -i -X POST http://localhost:8080/api/demo/async/propagated
```

## 테스트

통합 테스트는 Testcontainers로 실제 PostgreSQL 컨테이너를 실행합니다.

```bash
./gradlew clean test
./gradlew build
```

