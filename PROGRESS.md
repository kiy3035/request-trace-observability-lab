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

Phase 1 - 기본 애플리케이션

상태: NOT STARTED

---

## 작업 이력

### Phase 1 - 기본 애플리케이션

상태: NOT STARTED

목표:

Spring Boot + PostgreSQL + JPA 기반 Order CRUD 구성

변경 파일:

- 없음

실행한 명령:

- 없음

검증 결과:

- build: NOT RUN
- test: NOT RUN
- application start: NOT RUN
- CRUD scenario: NOT RUN

확인된 결과:

- 없음

문제점:

- 없음

다음 단계:

- Phase 2 - HTTP Trace ID

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
