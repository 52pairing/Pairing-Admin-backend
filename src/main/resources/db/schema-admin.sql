-- =====================================================================
-- 관리자 서버 전용 테이블. 애플리케이션 기동 시 자동으로 실행된다.
-- (spring.sql.init.schema-locations 로 지정, DataSource 초기화 직후 · JPA 검증 이전)
--
-- IF NOT EXISTS 라서 몇 번을 실행해도 안전하다.
-- 백엔드가 소유한 테이블(account, project, ...)은 절대 건드리지 않는다.
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
