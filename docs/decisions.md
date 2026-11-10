# 마이그레이션 결정

- API/indexer/sync는 `SCAN_ROLE=api|indexer|sync`로 원본 프로세스 경계를 보존한다. API 포트 4000, indexer 4001, sync 4002다.
- JDBC와 명시적 SQL로 TypeORM의 기존 camelCase 컬럼과 numeric(78,0), jsonb, bigint 계약을 유지한다. 기존 DB에 자동 파괴적 스키마 변경을 하지 않는다. 새 로컬 DB는 schema.sql로 초기화한다.
- HTTP 경계는 JDK HttpClient를 사용하고 원본의 10초 조회·30초 작업 타임아웃을 유지한다.
- ABI 인코딩/디코딩은 web3j ABI 4.12.3을 사용한다. EVM 실행기는 체인 서비스의 책임이며 탐색기에 복제하지 않는다.
- 잔액 표시의 소수점 4자리 절삭, bigint 문자열 응답, hex 변환을 유지한다.
- 원본의 23시 전체 데이터 삭제 작업은 위험하므로 명시적 `SCAN_CLEANUP_ENABLED=true`일 때만 활성화한다. 시간대와 삭제 테이블은 유지한다.
- 원본의 null receipt 허용과 거래 조회 fallback 의미를 유지한다. 오류별 처리의 차이는 검증 중 명시한다.
