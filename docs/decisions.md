# 마이그레이션 결정

- API/indexer/sync는 `SCAN_ROLE=api|indexer|sync`로 원본 프로세스 경계를 보존한다. API 포트 4000, indexer 4001, sync 4002다.
- JDBC와 명시적 SQL로 TypeORM의 기존 camelCase 컬럼과 numeric(78,0), jsonb, bigint 계약을 유지한다. 기존 DB에 자동 파괴적 스키마 변경을 하지 않는다. 새 로컬 DB는 schema.sql로 초기화한다.
- HTTP 경계는 JDK HttpClient를 사용하고 원본의 10초 조회·30초 작업 타임아웃을 유지한다.
- ABI 인코딩/디코딩은 Kotlin의 정수·명시적 offset 계산으로 구현하고 Keccak 해시에 web3j utils 4.12.3을 사용한다. 단순 타입뿐 아니라 tuple·정적/동적 배열의 중첩을 ethers 6.15 기준 벡터와 대조한다. EVM 실행기는 체인 서비스의 책임이다.
- 잔액 표시의 소수점 4자리 절삭, bigint 문자열 응답, hex 변환을 유지한다.
- 원본의 23시 전체 데이터 삭제 작업은 위험하므로 명시적 `SCAN_CLEANUP_ENABLED=true`일 때만 활성화한다. 시간대와 삭제 테이블은 유지한다.
- 원본의 null receipt 허용과 거래 조회 fallback 의미를 유지한다. 오류별 처리의 차이는 검증 중 명시한다.

## 검증 중 확정한 차이와 보존 기준

- ABI uint/int 디코딩 결과는 JSON에서 손실 없는 10진 문자열로 반환한다. 원본 ethers의 BigInt 직접 반환은 Express JSON 직렬화 오류를 일으킬 수 있어 명시적으로 문자열로 고정했다.
- 계정 조회의 주소 형식을 RPC 호출 전에 검증한다. 원본은 임의 경로 문자열도 체인에 전달해 잘못된 계정을 생성할 수 있었으며, 실제 통합 중 체인의 상태 루트 계산 실패로 확인했다. Kotlin 체인과 탐색기 모두 경계에서 거절한다.
- 블록·거래 목록의 page/limit는 1 이상으로 제한한다. 원본 일부 경로는 음수 입력을 DB까지 전달하지만 유효한 페이지 계약은 유지한다.
- 토큰 잔액 집계는 원본의 CASE 순서와 0이 아닌 잔액 조건을 유지한다. self-transfer에서 수신 CASE를 먼저 선택하는 원본 의미도 임의 변경하지 않는다.
- 기존 부모만 있고 거래가 누락된 블록을 원본은 다시 insert하다 유니크 충돌할 수 있었다. Kotlin은 부모 hash conflict를 무시하고 하위 레코드를 같은 트랜잭션으로 복구한다.
- 블록/거래 DB timestamp는 원본의 초 단위 저장을 유지한다. 거래 RPC fallback에서만 원본처럼 밀리초 문자열과 ISO 시각으로 변환한다.
