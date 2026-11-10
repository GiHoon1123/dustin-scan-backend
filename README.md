# Dustin Scan Backend — Kotlin

Dustin Chain의 블록·거래·컨트랙트·토큰 전송을 수집하고 조회하는 Spring Boot 탐색기다. 원본 NestJS의 API, indexer, sync 실행 경계를 유지한다.

- API: 조회·계정·컨트랙트 ABI·스테이킹·스테이블코인 프록시
- Indexer: 블록·거래·영수증·ERC-20 전송·배포 컨트랙트 원자 저장
- Sync: 마지막 저장 블록 다음 높이를 체인에서 읽고 indexer 성공 후 전진
- PostgreSQL: 기존 TypeORM의 테이블·camelCase 컬럼과 bigint/numeric 문자열 계약 유지

## 기술

Kotlin 2.3.21, JDK 21, Spring Boot 4.1.2, Spring MVC, JDBC, PostgreSQL, web3j utils 4.12.3, JUnit 5, MockK, ktlint, Gradle 9.8.2. 기본 버전은 `dex-simulator-kotlin`과 같다.

## 실행

```sh
./gradlew build --no-daemon --max-workers=2
# 새 로컬 DB를 한 번 초기화 (DB에는 schema.sql의 테이블이 생성된다)
psql "$POSTGRES_URL" -f src/main/resources/schema.sql

export DB_HOST=localhost DB_PORT=5432 DB_USERNAME=postgres
export DB_PASSWORD=postgres DB_DATABASE=dustin_scan_dev
export CHAIN_URL=http://localhost:3000

# 각각 별도 터미널에서 실행
SCAN_ROLE=api ./gradlew bootRun
SCAN_ROLE=indexer ./gradlew bootRun
SCAN_ROLE=sync ./gradlew bootRun
```

기본 포트는 API 4000, indexer 4001, sync 4002다. `SERVER_PORT`로 덮어쓸 수 있다. indexer 주소는 `INDEXER_URL`(기본 `http://localhost:4001`)로 설정한다. Swagger UI는 `/api`, 명세는 `/api-docs`, 모든 역할의 DB 상태 확인은 `/health`다.

기존 DB를 사용하면 `DB_INIT_MODE=never`(기본)를 유지한다. 새 DB에서 단일 프로세스로 `DB_INIT_MODE=always`를 사용해 초기화할 수도 있으나, 여러 프로세스의 동시 DDL 실행은 피한다. 운영 DB를 자동 변경하거나 기존 데이터를 지우지 않는다.

## 테스트

```sh
# 단위·HTTP 경계 테스트. PostgreSQL 통합 테스트는 환경변수가 없으면 명시적으로 skip된다.
./gradlew test --no-daemon --max-workers=2

# 전용 로컬 PostgreSQL 사용 (개발/운영 DB로 실행하지 말 것)
docker run --name scan-test-postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=dustin_scan_test -p 127.0.0.1:5546:5432 -d postgres:17-alpine
SCAN_TEST_DATABASE_URL=jdbc:postgresql://localhost:5546/dustin_scan_test \
  ./gradlew build --no-daemon --max-workers=2
```

통합 테스트는 지정 DB의 탐색기 테이블을 초기화한다. GitHub Actions도 전용 PostgreSQL 서비스로 통합 테스트를 실행한다.

## 이전 범위·차이

- [원본 커밋·경로 목록](docs/source-inventory.md)
- [판단·명시적 동작 차이](docs/decisions.md)
- [검증 결과·남은 확인](docs/migration-status.md)

원본의 API 경로와 JSON 형태, 16진수·정수 문자열, DSTN 표시 절삭, 영수증 대기·pending 의미를 유지한다. 원본에 없는 자동 reorg 롤백을 새로 구현한 것으로 표현하지 않는다. 23시 전체 테이블 삭제는 명시적 `SCAN_CLEANUP_ENABLED=true`에서만 작동한다. 로컬 교육용 개인키 전달 API를 그대로 제공하므로 공개 운영용 커스터디로 사용하지 않는다.
