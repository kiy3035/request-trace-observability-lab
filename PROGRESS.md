# PROGRESS.md

## 기록 규칙

Codex 작업이 끝날 때마다 이 파일을 갱신한다.

실제로 실행하지 않은 테스트나 Benchmark는 성공했다고 작성하지 않는다.

상태값:

- PASS
- FAIL
- BLOCKED
- NOT RUN

---

## 현재 단계

Phase 8 - 문서화 및 실험 결과 정리

상태: PASS

---

## 작업 이력

### Phase 1 - 기본 애플리케이션

상태: PASS

목표:

Spring Boot + PostgreSQL + JPA 기반 Order CRUD 구성

변경 파일:

- `build.gradle`, `settings.gradle`, Gradle Wrapper
- `compose.yaml`, `application.yml`
- Order Entity/Repository/Service/Controller/DTO
- 공통 400/404 Error Handling
- `OrderApiIntegrationTest`
- `README.md`, `.gitignore`

실행한 명령:

```powershell
.\gradlew.bat --no-daemon clean test
.\gradlew.bat --no-daemon build
docker compose -p request-trace-phase1-check up -d --wait postgres
java -jar build\libs\request-trace-observability-lab-0.0.1-SNAPSHOT.jar
Invoke-WebRequest로 POST/GET/PUT/DELETE /api/orders 호출
docker compose -p request-trace-phase1-check down -v --remove-orphans
```

검증 결과:

- build: PASS (`BUILD SUCCESSFUL`, 2026-09-06)
- test: PASS (6 tests, failures 0, errors 0, skipped 0; 실제 PostgreSQL Testcontainers)
- application start: PASS (bootJar + PostgreSQL Compose, HTTP 18080)
- CRUD scenario: PASS (POST 201, GET 200, PUT 200, DELETE 204, 삭제 후 GET 404)

확인된 결과:

- 생성된 Order id: `1`
- 수정 결과: `productName=mechanical keyboard`, `quantity=2`
- 수동 검증 PostgreSQL 동적 호스트 포트: `32768`

문제점:

- 첫 수동 검증에서 고정 호스트 포트 `55432`가 Windows 포트 바인딩 거부로 실패했다. 전용 Compose 리소스를 정리하고 동적 포트로 재실행해 PASS했다.

다음 단계:

- Phase 2 - HTTP Trace ID

---

### Phase 2 - HTTP Trace ID

상태: PASS

목표:

요청별 UUID Trace ID를 MDC와 응답 Header에 연결하고 요청 종료 후 정리

변경 파일:

- `TraceIdFilter.java`
- `TraceIdFilterTest.java`
- `README.md`

실행한 명령:

```powershell
.\gradlew.bat --no-daemon test --tests dev.requesttrace.observability.web.TraceIdFilterTest
.\gradlew.bat --no-daemon clean build
docker compose -p request-trace-phase2-check up -d --wait postgres
java -jar build\libs\request-trace-observability-lab-0.0.1-SNAPSHOT.jar
Invoke-WebRequest로 traceId 누락/정상/잘못된 Header 요청
docker compose -p request-trace-phase2-check down -v --remove-orphans
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (TraceIdFilter 6 + Order API 6, failures 0)
- runtime scenario: PASS (누락 시 생성, 정상 UUID 재사용, 잘못된 값 교체, 404 응답에도 Header 포함)

실제 관측 결과:

- 누락 Header 생성 traceId: `ab1a110d-c6ed-49c4-ba0f-4c7f37d7ef0e`
- 정상 Header 재사용 traceId: `550e8400-e29b-41d4-a716-446655440000`
- 잘못된 Header 교체 traceId: `be487aa0-3a9c-449b-b022-c355eff97ac4`
- event: 요청마다 `HTTP request started`, `HTTP request completed` 로그 확인
- Grafana/Loki 확인: NOT RUN (Phase 6 범위)

비고:

- MDC 정리는 정상 흐름과 ServletException 흐름을 모두 테스트했다.

Blocker:

- 없음

다음 권장 단계:

- Phase 3 - AOP 실행시간 측정

---

### Phase 3 - AOP 실행시간 측정

상태: PASS

목표:

Controller와 Service 실행시간을 측정하고 설정값으로 Slow Service/Request 판별

변경 파일:

- `ExecutionTimingAspect.java`, `ExecutionTimingAspectTest.java`
- `SlowThresholdProperties.java`
- `DemoController.java`, `DemoService.java`
- `TraceIdFilter.java`, `application.yml`
- `build.gradle`, `README.md`

실행한 명령:

```powershell
.\gradlew.bat --no-daemon test --tests dev.requesttrace.observability.aop.ExecutionTimingAspectTest --tests dev.requesttrace.observability.web.TraceIdFilterTest
.\gradlew.bat --no-daemon clean build
docker compose -p request-trace-phase3-check up -d --wait postgres
java -jar build\libs\request-trace-observability-lab-0.0.1-SNAPSHOT.jar
Invoke-WebRequest http://localhost:18080/api/demo/slow-service?delayMs=700
docker compose -p request-trace-phase3-check down -v --remove-orphans
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (전체 15 tests, failures 0)
- runtime scenario: PASS (700ms Slow Service와 Slow Request 재현)

실제 관측 결과:

- traceId: `9c644e13-f47b-4b8a-b3f5-a265f1a36d5f`
- `SERVICE_END`: 713ms, `slow=true`
- `SLOW_SERVICE`: 713ms
- `CONTROLLER_END`: 745ms
- `REQUEST_END`: 757ms, `slow=true`, HTTP 200
- `SLOW_REQUEST`: 757ms
- Grafana/Loki 확인: NOT RUN (Phase 6 범위)

비고:

- 첫 AOP 테스트 실행은 테스트 로그 Map 헬퍼가 null 값을 처리하지 못해 2건 FAIL했다. 헬퍼 수정 후 대상 테스트와 전체 빌드를 재실행해 PASS했다.
- AOP는 traceId를 만들지 않고 Filter가 설정한 MDC를 사용한다.

Blocker:

- 없음

다음 권장 단계:

- Phase 4 - JPA/JDBC SQL 실행시간 측정

---

### Phase 4 - JPA/JDBC SQL 실행시간 측정

상태: PASS

목표:

datasource-proxy로 실제 JDBC 완료시간을 측정하고 Slow SQL 판별

변경 파일:

- `DataSourceProxyConfiguration.java`
- `SqlQueryLoggingListener.java`, `SqlQueryLoggingListenerTest.java`
- `SlowSqlDemoRepository.java`
- `DemoController.java`, `DemoService.java`
- `OrderApiIntegrationTest.java`
- `build.gradle`, `application.yml`, `README.md`

실행한 명령:

```powershell
.\gradlew.bat --no-daemon test --tests dev.requesttrace.observability.config.SqlQueryLoggingListenerTest
.\gradlew.bat --no-daemon clean build
docker compose -p request-trace-phase4-check up -d --wait postgres
java -jar build\libs\request-trace-observability-lab-0.0.1-SNAPSHOT.jar
Invoke-WebRequest http://localhost:18080/api/demo/slow-sql?delayMs=300
docker compose -p request-trace-phase4-check down -v --remove-orphans
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (전체 20 tests, failures 0)
- runtime scenario: PASS (`pg_sleep(?)` 실제 JDBC 실행)

실제 관측 결과:

- traceId: `a907d75e-cd37-4330-a338-97da1e1ed5e2`
- `SQL_EXECUTION`: `SELECT`, SQL `select pg_sleep(?)`, 302ms, `slow=true`, `success=true`
- `SLOW_SQL`: 302ms
- `SERVICE_END`: 323ms, `slow=false`
- `REQUEST_END`: 392ms, HTTP 200, `slow=false`
- Bind Parameter: 로그에 출력되지 않음
- Grafana/Loki 확인: NOT RUN (Phase 6 범위)

비고:

- 첫 전체 테스트는 수동 DataSource 구성에 Testcontainers `@ServiceConnection` 정보가 적용되지 않아 7건 FAIL했다. Dynamic Property로 실제 컨테이너 연결 정보를 주입하도록 수정한 뒤 전체 20건을 재실행해 PASS했다.
- SQL 문자열은 공백 정규화 후 2,000자로 제한한다.

Blocker:

- 없음

다음 권장 단계:

- Phase 5 - Async MDC 유실 및 해결

---

### Phase 5 - Async MDC 유실 및 해결

상태: PASS

목표:

Thread Pool 전환 시 MDC 유실을 재현하고 TaskDecorator로 안전하게 전파

변경 파일:

- `AsyncExecutorConfiguration.java`
- `MdcTaskDecorator.java`, `MdcTaskDecoratorTest.java`
- `AsyncDemoService.java`, `AsyncDemoServiceTest.java`
- `DemoController.java`, `README.md`

실행한 명령:

```powershell
.\gradlew.bat --no-daemon test --tests dev.requesttrace.observability.config.MdcTaskDecoratorTest --tests dev.requesttrace.observability.demo.AsyncDemoServiceTest
.\gradlew.bat --no-daemon clean build
docker compose -p request-trace-phase5-check up -d --wait postgres
java -jar build\libs\request-trace-observability-lab-0.0.1-SNAPSHOT.jar
Invoke-WebRequest -Method POST /api/demo/async/lost
Invoke-WebRequest -Method POST /api/demo/async/propagated
docker compose -p request-trace-phase5-check down -v --remove-orphans
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (전체 24 tests, failures 0)
- runtime scenario: PASS (MDC 유실/전파 응답과 worker 로그 확인)

실제 관측 결과:

- lost caller traceId: `11111111-1111-4111-8111-111111111111`
- lost worker traceId: `null`, thread `async-no-mdc-1`
- propagated caller/worker traceId: `22222222-2222-4222-8222-222222222222`
- propagated worker thread: `async-mdc-1`
- event: 두 경로 모두 `ASYNC_CALLER`, `ASYNC_WORKER`
- Grafana/Loki 확인: NOT RUN (Phase 6 범위)

비고:

- TaskDecorator는 실행 후 worker의 기존 MDC를 복원하며, 기존 값이 없으면 비운다. 두 동작 모두 테스트했다.

Blocker:

- 없음

다음 권장 단계:

- Phase 6 - JSON Log + Alloy + Loki + Grafana

---

### Phase 6 - JSON Log + Alloy + Loki + Grafana

상태: PASS

목표:

JSON 로그 파일을 Alloy로 수집해 Loki에 저장하고 Grafana Datasource로 조회

변경 파일:

- `logback-spring.xml`, `application.yml`
- `Dockerfile`, `.dockerignore`, `compose.yaml`
- `docker/alloy/config.alloy`
- `docker/loki/loki-config.yaml`
- `docker/grafana/provisioning/datasources/loki.yaml`
- Error Demo 및 `APPLICATION_ERROR` 처리
- `build.gradle`, `README.md`, `OrderApiIntegrationTest.java`

실행한 명령:

```powershell
.\gradlew.bat --no-daemon clean build
.\gradlew.bat --no-daemon bootJar
docker compose config --quiet
docker compose -p request-trace-phase6-check up -d --build --wait
Invoke-WebRequest로 Order 생성, Slow SQL, Error 호출
Invoke-RestMethod로 Loki query_range/labels 및 Grafana Datasource health 호출
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (전체 25 tests, failures 0)
- compose config: PASS
- runtime scenario: PASS (5개 서비스 모두 running, healthcheck가 있는 app/postgres는 healthy)
- Loki ingestion/query: PASS
- Grafana datasource: PASS (`status=OK`)

실제 관측 결과:

- 정상 요청 traceId: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`
- 정상 Flow: `REQUEST_START → SQL_EXECUTION(3ms) → SERVICE_END(68ms) → CONTROLLER_END(140ms) → REQUEST_END(188ms, HTTP 201)`
- Slow SQL traceId: `77777777-7777-4777-8777-777777777777`, `SLOW_SQL`, 301ms, `slow=true`
- Error traceId: `88888888-8888-4888-8888-888888888888`, `APPLICATION_ERROR`, `IntentionalDemoException`, HTTP 500
- JSON `environment` 필드 중복 수: 1
- Loki Labels: `application`, `environment`, `level`
- traceId Label: 없음
- Grafana: database `ok`, version `13.2.0`, Loki Datasource `OK`

비고:

- 최초 이미지 다운로드가 중단되며 전용 Compose 컨테이너 이름 충돌이 발생했다. `request-trace-phase6-check` 리소스만 정리한 뒤 재기동했다.
- 최초 정상 POST 한 건은 Windows 호스트 연결 reset으로 실패했으며 새 traceId로 재실행해 HTTP 201과 Loki Flow를 확인했다.
- Alloy의 `filename` Label은 drop하고 Loki의 자동 `service_name` 발견은 비활성화했다.

Blocker:

- 없음

다음 권장 단계:

- Phase 7 - Grafana Troubleshooting Dashboard

---

### Phase 7 - Grafana Troubleshooting Dashboard

상태: PASS

목표:

Loki 로그만으로 운영 Troubleshooting Dashboard 구성

변경 파일:

- `docker/grafana/provisioning/dashboards/dashboards.yaml`
- `docker/grafana/provisioning/dashboards/json/request-trace-troubleshooting.json`
- `README.md`

실행한 명령:

```powershell
ConvertFrom-Json으로 Dashboard JSON 검증
docker compose config --quiet
docker compose -p request-trace-phase6-check restart grafana
Invoke-RestMethod /api/dashboards/uid/request-trace-troubleshooting
Invoke-RestMethod /loki/api/v1/query 로 6개 Dashboard LogQL 실행
.\gradlew.bat --no-daemon clean build
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (전체 25 tests, failures 0)
- dashboard provisioning: PASS (folder `Observability Lab`, uid `request-trace-troubleshooting`)
- panel count: 8
- template variables: 3 (`application`, `environment`, `traceId`)
- Dashboard LogQL: PASS (6개 지표/TOP query 모두 Loki API 실행 성공)

실제 관측 결과:

- 전체 요청 count query: vector 1 series
- Error count query: vector 1 series
- Slow Request count query: vector 0 series (재기동 후 30분 범위에 Slow Request 없음)
- Slow SQL count query: vector 1 series
- 느린 요청 TOP query: vector 10 series
- 느린 SQL TOP query: vector 4 series
- 제공 화면: 전체 HTTP 요청, Error, Slow Request, Slow SQL, 최근 Error+traceId, 요청 TOP 10, SQL TOP 10, traceId 입력, 선택 trace 시간순 Flow

비고:

- TOP 목록의 traceId/uri/sql은 `| json` 이후 query-time label로만 사용하며 Loki 저장 Label로 승격하지 않는다.

Blocker:

- 없음

다음 권장 단계:

- Phase 8 - 문서화 및 실험 결과 정리

---

### Phase 8 - 문서화 및 실험 결과 정리

상태: PASS

목표:

아키텍처, 설계 선택, 재현 절차, 실제 측정값과 Grafana 결과 화면 정리

변경 파일:

- `README.md`
- `docs/architecture.md`
- `docs/experiment-guide.md`
- `docs/measurement-results.md`
- `docs/images/grafana-dashboard.png`
- `docs/images/grafana-trace-flow.png`
- `PROGRESS.md`

실행한 명령:

```powershell
ConvertFrom-Json으로 Dashboard JSON 검증
Test-Path로 문서와 이미지 링크 대상 검증
docker compose config --quiet
docker compose -p request-trace-phase6-check ps --format json
.\gradlew.bat --no-daemon clean build
Test XML 6개를 파싱해 전체 test 결과 합산
git diff --check
docker compose -p request-trace-phase6-check down -v --remove-orphans
```

검증:

- build: PASS (`BUILD SUCCESSFUL`)
- tests: PASS (전체 25 tests, failures 0, errors 0, skipped 0)
- compose config: PASS
- runtime stack: PASS (5개 서비스 running, app/postgres healthy)
- dashboard JSON: PASS (`Request Trace Troubleshooting`, 8 panels, 3 variables)
- 문서 및 이미지 링크 대상: PASS
- Grafana 실제 화면 캡처: PASS

실제 관측 결과:

- 전체 Dashboard 캡처: `docs/images/grafana-dashboard.png` (29,398 bytes)
- 선택 trace flow 캡처: `docs/images/grafana-trace-flow.png` (56,096 bytes)
- 캡처 시점 Dashboard: 전체 HTTP 요청 151, Error 2, Slow Request `No data`
- 선택 traceId: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`
- 선택 Flow: `REQUEST_START → SQL_EXECUTION → SERVICE_END → CONTROLLER_END → REQUEST_END`
- Compose 상태: alloy/grafana/loki running, app/postgres healthy

비고:

- 전체 HTTP 요청에는 5초 주기의 `/actuator/health` 컨테이너 healthcheck가 포함된다.
- Grafana 캡처와 Phase 7의 Loki API 조회 시점에 최근 30분 Slow Request가 없어서 `No data`/0 series로 표시됐다.
- 측정 문서는 Phase 1~7에서 직접 실행한 값만 옮겼다.
- 검증 후 `request-trace-phase6-check` 전용 컨테이너 5개, volume 5개, network 1개를 정리했다.

Blocker:

- 없음

다음 권장 단계:

- 전체 Phase 완료

---

## 작업 완료 후 작성 형식

### Phase N - 작업명

상태:

PASS / FAIL / BLOCKED / NOT RUN

목표:

한 줄 설명

변경 파일:

- 파일명
- 파일명

실행한 명령:

```bash
실제로 실행한 명령
```

검증:

- build:
- tests:
- runtime scenario:

실제 관측 결과:

- traceId:
- elapsedMs:
- event:
- Grafana/Loki 확인:

비고:

- 실제 사실만 기록

Blocker:

- 없음 또는 실제 문제

다음 권장 단계:

- Phase N+1
