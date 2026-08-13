-- =====================================================================
-- 관리자 서버 전용 테이블. 애플리케이션 기동 시 자동으로 실행된다.
-- (spring.sql.init.schema-locations 로 지정, DataSource 초기화 직후 · JPA 검증 이전)
--
-- IF NOT EXISTS 라서 몇 번을 실행해도 안전하다.
--
-- 백엔드가 소유한 테이블(account, project, ...)의 데이터 구조는 백엔드가 정한다.
-- 유일한 예외가 맨 아래 account.suspended_at 이며, 그 자리에 이유를 적어 두었다.
--
-- 이 파일을 앱이 실행하지 못하는 환경이라면(= DB 계정에 CREATE 권한이 없다면)
-- db/init/05-create-admin-user-table.sql 을 DBA가 미리 실행하고,
-- application.yaml 의 spring.sql.init.mode 를 never 로 내리면 된다.
-- =====================================================================

CREATE TABLE IF NOT EXISTS "admin_user" (
    "id"               BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "username"         VARCHAR(50)  NOT NULL,
    "password_hash"    VARCHAR(60)  NOT NULL,
    "login_fail_count" INTEGER      DEFAULT 0 NOT NULL,
    "locked_at"        TIMESTAMP,
    "last_login_at"    TIMESTAMP,
    "created_at"       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at"       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 로그인 아이디는 유일해야 한다.
-- ALTER TABLE ... ADD CONSTRAINT 는 재실행하면 오류가 나므로 인덱스로 만든다.
CREATE UNIQUE INDEX IF NOT EXISTS "uk_admin_user_username" ON "admin_user" ("username");


-- =====================================================================
-- account.suspended_at — 백엔드 소유 테이블에 손대는 유일한 예외.
--
-- 정지 여부의 판정 기준은 Redis 의 SUSPEND:{accountId} 키다. 백엔드가 그 키만 보고
-- 로그인을 막기 때문에(AccountSuspensionPort) 그쪽이 원본이고, 이 컬럼은 사본이다.
--
-- 그런데 Redis 키로는 "정지 회원만 보기" 필터도, 요약 카드의 정지 건수도 만들 수 없다.
-- SQL 로 세려면 관계형 쪽에도 표시가 있어야 해서 이 컬럼을 함께 쓴다.
-- 상태를 account.status = 'SUSPENDED' 로 표현하지 않는 이유는, 백엔드의 AccountStatus
-- enum 에 그 값이 없어서 백엔드가 해당 계정을 읽는 순간 IllegalArgumentException 이 나기 때문이다.
--
-- 컬럼 정의의 단일 소스는 백엔드의 db/init/02-create-schema.sql 이다. 다만 그 파일은
-- 컨테이너 최초 기동 때만 실행되므로, 이미 떠 있는 DB 에는 아래 ALTER 가 채워 넣는다.
-- 양쪽 정의가 같아야 하며, 백엔드에서 바뀌면 여기도 맞춘다.
-- =====================================================================

ALTER TABLE "account" ADD COLUMN IF NOT EXISTS "suspended_at" TIMESTAMP;

COMMENT ON COLUMN "account"."suspended_at" IS '관리자 정지 시각. NULL 이 아니면 정지 상태(관리자 서버 전용 · 로그인 차단 판정은 Redis SUSPEND:{id})';

-- 정지 회원은 전체의 극히 일부다. 부분 인덱스로 두면 인덱스가 그만큼만 커진다.
CREATE INDEX IF NOT EXISTS "idx_account_suspended" ON "account" ("suspended_at") WHERE "suspended_at" IS NOT NULL;
