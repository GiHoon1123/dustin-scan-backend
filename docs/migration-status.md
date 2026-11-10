# 마이그레이션 상태와 검증

## 구현

- 공통: 버전·Gradle·CI, hex/큰 정수/DSTN 변환, 체인 REST 클라이언트, 공통 응답·페이지 형식.
- 데이터: 원본 7개 테이블과 기존 컬럼명·타입을 보존하는 JDBC 계층.
- 조회: 블록 목록/상세, 거래 목록/상세/주소별 조회와 DB 미적재 거래의 체인 fallback, 계정·토큰 잔액·전송 내역.
- 컨트랙트: 목록·배포자 필터·상세·메타/ABI 수정, ABI 함수 인코딩·호출·결과 디코딩, 배포 프록시.
- 프록시: 스테이킹 6개, 스테이블코인 9개 경로의 금액 변환·원본 응답 포맷.
- Indexer: 블록과 모든 하위 결과를 하나의 DB 트랜잭션으로 저장. 재실행 중복 방어·기존 블록의 거래/영수증 누락 복구.
- Sync: 1초 주기, 단일 프로세스 중첩 방지, 마지막 저장 높이 이후 순차 전달, 실패 높이 재시도.

## 검증 기록

2026-11-10 로컬 JDK 21·PostgreSQL 17에서 `SCAN_TEST_DATABASE_URL=jdbc:postgresql://localhost:5546/dustin_scan_test ./gradlew build --no-daemon --max-workers=2` 통과.

- 단위·경계 15개: 원본 금액 절삭, bigint 정밀도, pagination, 주소·요청 검증, ABI transfer·tuple/tuple 배열·중첩 배열·signed 정수·고정 bytes의 ethers 6.15 기준 벡터 6개, 체인 404 구분, pending/실패/성공 대기, sync 실패 재시도, indexer 실패 응답.
- 실제 PostgreSQL 통합 7개: 블록·거래·영수증·ERC20·배포 저장과 재처리, 중간 파싱 실패 전체 롤백, 부분 부모 복구, 실제 HTTP 조회·응답 형식·로그, 체인 fallback, 스테이킹·스테이블코인 변환, DB health·404.
- 원본 TypeScript 금액 유틸리티를 Node 22의 strip-types로 실행한 5개 입력 결과를 Kotlin 테스트와 대조했다.
- 별도 Java 프로세스 API(4140)·indexer(4141)·sync(4142)를 실제 Kotlin Dustin Chain(3100)·PostgreSQL에 연결하여 모든 health 200과 블록 0부터 연속 적재를 확인했다.

## 아직 구분할 사항

- 실제 Kotlin 체인과 네이티브 전송 → 영수증 확정 → DB 적재 → 거래/블록 API 조회 → 수신 잔액을 검증했다. 전송 hash `0x1086aef556dc2474beb31b97ce5b669d0be1e0602023fd49089cc8ae9e726e45`, 블록 10, status confirmed, DB 목록 적재 확인. 재현 스크립트는 `scripts/live-native-smoke.py`다.
- Kotlin 체인과 실제 EVM 배포 → PostgreSQL 자동 인덱싱 → ABI 업로드 → setValue(42) 실행·이벤트 → getter decodedResult=42까지 확인했다. 스테이킹·스테이블코인 프록시의 HTTP/금액 변환 검증과 체인 자체의 실제 계약 검증은 구분하며, 전체 임의 계약의 호환성을 주장하지 않는다.
- 원본의 모든 Jest 테스트를 일대일 이전한 것은 아니다. 인프라·프레임워크 모듈 mock 테스트 대신 Kotlin의 실제 HTTP·PostgreSQL 경계를 검증했다.
- ABI 복합 tuple·tuple 배열·중첩 배열·함수 선택자를 ethers 기준과 대조했다. 동명 overload는 full signature로 선택하며 bare name이 모호하면 거절한다. 임의의 외부 ABI 전체에 대한 호환성 보장은 아니다.
- 원본 sync 코드에는 자동 reorg 롤백·DB 손실 복구의 다중 노드 소유권 조정이 없다. README가 암시하던 reorg 기능을 구현 완료로 확대하지 않는다.
- 원본은 블록의 거래·영수증이 모두 존재하면 건너뛰므로 그 하위의 토큰 전송만 누락된 과거 데이터까지 복구하지 않는다. 같은 정책을 유지한다.
- 단일 프로세스별 동기화 잠금은 여러 sync 인스턴스의 동시 실행을 방어하지 않는다.
- 공개 실행 경로의 검증 후 Kotlin 저장소는 dustin-scan-backend, 원본은 dustin-scan-backend-nestjs로 보존했다. 위 동기화·임의 ABI·운영 데이터 검증 한계는 남는다.
