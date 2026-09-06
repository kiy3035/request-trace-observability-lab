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

## 테스트

통합 테스트는 Testcontainers로 실제 PostgreSQL 컨테이너를 실행합니다.

```bash
./gradlew clean test
./gradlew build
```

