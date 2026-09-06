# Experiment Guide

이 문서는 로컬 Stack에서 필수 관측 시나리오를 순서대로 재현하는 절차다. 명령 예시는 기본 포트를 사용한다.

## 1. Stack 실행

요구 사항은 Java 21과 Docker Desktop이다.

```bash
./gradlew clean build
docker compose up -d --build --wait
docker compose ps
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용할 수 있다. 기본 접속 주소는 다음과 같다.

- App: <http://localhost:8080>
- Grafana: <http://localhost:3000>
- Loki ready: <http://localhost:3100/ready>
- Alloy UI: <http://localhost:12345>

Grafana에 로그인한 뒤 `Observability Lab / Request Trace Troubleshooting` Dashboard를 연다.

## 2. Scenario A - 정상 요청

고정 UUID를 사용하면 모든 계층의 로그를 같은 값으로 바로 찾을 수 있다.

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" \
  -d '{"productName":"keyboard","quantity":1}'
```

확인 항목:

- 응답 상태가 `201`이고 `X-Trace-Id`가 요청 UUID와 같다.
- 선택한 trace flow에 `REQUEST_START → SQL_EXECUTION → SERVICE_END → CONTROLLER_END → REQUEST_END`가 시간순으로 보인다.
- 요청 헤더를 생략하면 서버가 UUID를 발급하고, 잘못된 UUID를 보내면 새 값으로 교체한다.

## 3. Scenario B - Slow Service / Slow Request

기본 HTTP/Service threshold는 500ms다.

```bash
curl -i "http://localhost:8080/api/demo/slow-service?delayMs=700" \
  -H "X-Trace-Id: 66666666-6666-4666-8666-666666666666"
```

확인 항목:

- Service와 HTTP elapsed가 threshold를 넘는다.
- `SLOW_SERVICE`, `SLOW_REQUEST` 이벤트가 `slow=true`로 기록된다.
- Dashboard의 Slow Request와 느린 요청 TOP 목록에서 찾을 수 있다.

## 4. Scenario C - Slow SQL

기본 SQL threshold는 200ms다. Demo Query는 PostgreSQL `pg_sleep`을 JDBC로 실행한다.

```bash
curl -i "http://localhost:8080/api/demo/slow-sql?delayMs=300" \
  -H "X-Trace-Id: 77777777-7777-4777-8777-777777777777"
```

확인 항목:

- `SQL_EXECUTION`과 `SLOW_SQL`에 같은 traceId가 있다.
- `elapsedMs`가 실제 JDBC 실행 완료 시간을 나타내며 `slow=true`다.
- SQL에 Bind Parameter 값이 출력되지 않는다.

## 5. Scenario D - Error 추적

```bash
curl -i http://localhost:8080/api/demo/error \
  -H "X-Trace-Id: 88888888-8888-4888-8888-888888888888"
```

확인 항목:

- HTTP 상태가 `500`이다.
- `APPLICATION_ERROR`와 앞뒤 실행 로그의 traceId가 같다.
- 최근 Error에서 traceId를 찾고 선택한 trace flow로 요청 전체를 조회할 수 있다.

## 6. Scenario E - Async MDC 유실과 전파

```bash
curl -i -X POST http://localhost:8080/api/demo/async/lost \
  -H "X-Trace-Id: 11111111-1111-4111-8111-111111111111"

curl -i -X POST http://localhost:8080/api/demo/async/propagated \
  -H "X-Trace-Id: 22222222-2222-4222-8222-222222222222"
```

확인 항목:

- `lost`: `callerTraceId`에는 값이 있고 `workerTraceId`는 `null`이다.
- `propagated`: `callerTraceId`와 `workerTraceId`가 같다.
- 반복 테스트에서 Worker 작업 종료 후 MDC가 정리되어 이전 traceId가 남지 않는다.

## 7. Loki에서 직접 조회

Grafana Explore 또는 Dashboard의 query에서 JSON field를 파싱한다.

```logql
{application="request-trace-observability-lab", environment="local"}
| json
| traceId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
```

Slow SQL만 찾는 예시는 다음과 같다.

```logql
{application="request-trace-observability-lab", environment="local"}
| json
| event="SLOW_SQL"
| slow="true"
```

Loki Label browser에는 `application`, `environment`, `level`만 있어야 한다. `traceId`는 Label이 아니라 JSON field다.

## 8. Threshold 변경

Compose 실행 전에 환경 변수로 바꿀 수 있다.

```bash
SLOW_HTTP_MS=300 SLOW_SERVICE_MS=300 SLOW_SQL_MS=100 docker compose up -d --build --wait
```

기본값은 HTTP 500ms, Service 500ms, SQL 200ms다.

## 9. 종료

```bash
docker compose down
```

DB와 Loki 데이터를 포함한 volume까지 지우려면 실험 데이터가 삭제된다는 점을 확인한 뒤 `docker compose down -v`를 사용한다.
