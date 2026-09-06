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

Phase 2 - HTTP Trace ID

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
