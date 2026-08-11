-- =====================================================================
-- 관리자 계정 테이블 생성  [PostgreSQL]
--
-- 관리자 계정은 회원(account) 테이블과 <완전히 분리>된 admin_user 테이블을 쓴다.
-- 관리자 서버만 이 테이블을 읽고 쓴다. 백엔드(Pairing-backend)는 이 테이블을 모른다.
--
-- ---------------------------------------------------------------------
-- 보통은 이 파일을 직접 실행할 필요가 없다.
--   관리자 서버가 기동할 때 src/main/resources/db/schema-admin.sql 을 자동으로 실행해
--   테이블이 없으면 만든다. (같은 내용이고, 여러 번 실행해도 안전하다)
--
-- 이 파일이 필요한 경우.
--   - 앱이 쓰는 DB 계정에 CREATE 권한을 주고 싶지 않을 때 (DBA가 미리 만들어 두는 경우)
--   - 운영 DB 변경을 사람이 직접 통제하고 싶을 때
--   이때는 이 파일을 실행한 뒤 SQL_INIT_MODE=never 로 앱을 띄운다.
--
-- 실행:
--   pairing 데이터베이스에 접속해서 실행한다. (백엔드와 같은 DB, 다른 테이블)
--   psql -U pairing -d pairing -v ON_ERROR_STOP=1 -f db/init/05-create-admin-user-table.sql
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 테이블
--    필요한 것은 아이디와 비밀번호뿐이다. 나머지는 잠금·감사에 쓰는 최소한의 컬럼이다.
--    created_at / updated_at 은 애플리케이션이 채운다. (account 와 달리 트리거를 쓰지 않는다)
-- ---------------------------------------------------------------------
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

CREATE UNIQUE INDEX IF NOT EXISTS "uk_admin_user_username" ON "admin_user" ("username");

COMMENT ON TABLE  "admin_user"                    IS '관리자 계정 (회원 account 와 무관한 별도 테이블)';
COMMENT ON COLUMN "admin_user"."username"         IS '로그인 아이디';
COMMENT ON COLUMN "admin_user"."password_hash"    IS 'BCrypt 해시 (60자)';
COMMENT ON COLUMN "admin_user"."login_fail_count" IS '비밀번호 연속 실패 횟수. 성공하면 0으로 초기화';
COMMENT ON COLUMN "admin_user"."locked_at"        IS '잠긴 시각. NULL 이면 정상';


-- ---------------------------------------------------------------------
-- 2. 계정 만들기
--
--    권장: 애플리케이션에게 맡긴다. BCrypt 해시를 손으로 만들 필요가 없다.
--
--      ADMIN_BOOTSTRAP_ENABLED=true
--      ADMIN_BOOTSTRAP_USERNAME=admin
--      ADMIN_BOOTSTRAP_PASSWORD=원하는비밀번호
--
--      이렇게 한 번 띄우면 계정이 생긴다. 그 다음 이 변수들을 지우고 재배포한다.
--
--    SQL 로 직접 넣으려면 아래 주석을 풀고 password_hash 에 BCrypt 해시를 채운다.
--    평문을 넣으면 인코더가 해시로 인식하지 못해 로그인이 항상 실패한다.
-- ---------------------------------------------------------------------
-- INSERT INTO "admin_user" ("username", "password_hash")
-- VALUES ('admin', '__여기에_BCRYPT_해시__')   -- $2a$10$... 형태, 60자
-- ON CONFLICT ("username") DO NOTHING;


-- ---------------------------------------------------------------------
-- 3. 확인 (password_hash 가 $2a$ 로 시작하고 길이가 60이어야 한다)
-- ---------------------------------------------------------------------
SELECT id,
       username,
       LEFT(password_hash, 4) AS hash_prefix,
       LENGTH(password_hash)  AS hash_length,
       login_fail_count,
       locked_at,
       last_login_at,
       created_at
FROM "admin_user"
ORDER BY id;


-- ---------------------------------------------------------------------
-- 4. 잠금 해제 (비밀번호를 5회 틀려 잠겼을 때)
-- ---------------------------------------------------------------------
-- UPDATE "admin_user" SET locked_at = NULL, login_fail_count = 0 WHERE username = 'admin';
