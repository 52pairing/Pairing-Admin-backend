# Pairing-admin

페어링 **관리자 전용 서버**. 백엔드(`Pairing-backend`)에서 관리자 기능만 떼어내 별도 EC2로 운영한다.

- 백엔드와 **같은 RDS(PostgreSQL)** 를 바라본다. 회원·프로젝트 등 서비스 데이터는 하나뿐이다.
- 백엔드와 **같은 S3 버킷**을 쓴다. object key 규칙도 동일하다.
- 인증만 다르다. 백엔드는 **JWT(무상태)**, 여기는 **세션 로그인(쿠키)** 이다.
- **관리자 계정은 `admin_user` 라는 전용 테이블**을 쓴다. 회원(`account`)과 완전히 분리되어 있다.

### 관리자 계정을 왜 분리했나

회원 테이블에 `role='ADMIN'` 으로 섞어 두면 이런 일이 생긴다.

- 회원 관리 화면의 실수 하나가 관리자 계정까지 건드릴 수 있다.
- 회원가입·소셜로그인·탈퇴·휴면 같은 서비스 로직이 관리자 행에도 흘러든다.
- 백엔드가 `account` 스키마를 바꿀 때마다 관리자 로그인이 영향을 받는다.
- 관리자에게 필요 없는 컬럼(이메일 인증, 가입 경로, 재가입 제한…)을 억지로 채워야 한다.

관리자는 사람 수도 적고 수명도 다르다. 테이블을 나누는 편이 서로를 지켜 준다.
그래서 `admin_user` 에는 **아이디와 비밀번호**, 그리고 잠금에 필요한 최소 컬럼만 둔다.

| 테이블 | 소유자 | 용도 |
|---|---|---|
| `admin_user` | **관리자 서버** | 관리자 로그인 계정. 백엔드는 이 테이블을 모른다 |
| `account`, `project`, … | 백엔드 | 서비스 데이터. 관리자 서버는 **읽기와 상태 변경만** 한다 |

---

## 1. 왜 세션인가

| | 백엔드 (JWT) | 관리자 (세션) |
|---|---|---|
| 대상 | 불특정 다수 사용자 | 소수의 내부 관리자 |
| 확장 | 서버 늘려도 상태 공유 불필요 | 인스턴스 1대 전제 (아래 주의 참고) |
| 강제 로그아웃 | 만료 전까지 불가 (블랙리스트 필요) | 세션 무효화로 **즉시 가능** |
| 토큰 저장 | 브라우저 저장소 → XSS 노출면 | HttpOnly 쿠키 → JS가 못 읽음 |
| 주의점 | — | 쿠키 기반이라 **CSRF 방어 필요** |

관리자 화면은 권한이 세고 사용자가 적다. "지금 당장 저 계정을 끊어야 한다"가 가능한 쪽이 맞다고 보고 세션을 택했다.

> **인스턴스를 2대 이상으로 늘린다면** 세션이 메모리에 있어서 로그인이 풀린다.
> 그때는 `spring-session-data-redis` 의존성을 추가하고 `spring.session.store-type=redis` 만 켜면 된다.
> 코드는 바꿀 필요가 없다. (백엔드가 쓰는 Redis를 그대로 공유하면 된다)

---

## 2. 빠르게 띄우기

### 2-1. 사전 조건

백엔드의 `db/init/01`, `02`, `03` 이 이미 실행되어 있어야 한다. 회원 관리 화면이 `account` 등
백엔드 테이블을 읽기 때문이다. 이 서버는 **백엔드 테이블의 스키마를 만들지 않는다**
(`ddl-auto: validate`). 테이블이 없으면 기동 단계에서 실패한다 — 의도한 동작이다.

`admin_user` 는 예외다. 이 테이블은 관리자 서버가 소유하므로 **기동할 때 자동으로 만든다.**
([src/main/resources/db/schema-admin.sql](src/main/resources/db/schema-admin.sql), `CREATE TABLE IF NOT EXISTS`)
DB 계정에 `CREATE` 권한을 주고 싶지 않다면
[db/init/05-create-admin-user-table.sql](db/init/05-create-admin-user-table.sql) 을 사람이 먼저 실행하고
`SQL_INIT_MODE=never` 로 띄운다.

### 2-2. 최초 관리자 계정 만들기

`admin_user` 가 비어 있으면 로그인할 수 없다. 아래 환경변수를 주고 **한 번만** 띄운다.
BCrypt 해시를 손으로 만들 필요가 없다는 것이 장점이다.

```bash
ADMIN_BOOTSTRAP_ENABLED=true ADMIN_BOOTSTRAP_USERNAME=admin ADMIN_BOOTSTRAP_PASSWORD=Admin!2345 ./gradlew bootRun
```

계정이 생기면 로그에 안내가 찍힌다. 그 뒤에는 `ADMIN_BOOTSTRAP_*` 을 **모두 지우고** 다시 띄운다.
평문 비밀번호를 환경에 남겨둘 이유가 없다. (이미 같은 아이디가 있으면 이 기능은 아무 것도 하지 않는다)

SQL로 직접 넣고 싶으면
[db/init/05-create-admin-user-table.sql](db/init/05-create-admin-user-table.sql) 하단의 INSERT 를 참고한다.
`password_hash` 에는 반드시 BCrypt 해시(`$2a$` 로 시작, 60자)가 들어가야 한다.

### 2-3. 실행

```bash
./gradlew bootRun
```

- API: http://localhost:8081
- Swagger: http://localhost:8081/swagger-ui/index.html
- 헬스체크: http://localhost:8081/actuator/health

Swagger로 편하게 테스트하려면 CSRF를 잠깐 끈다. (운영에서는 절대 끄지 않는다)

```bash
CSRF_ENABLED=false ./gradlew bootRun
```

---

## 3. 환경변수

**백엔드와 반드시 같은 값**을 써야 하는 것들이다. 다르면 두 서버가 서로 다른 데이터를 보게 된다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/pairing` | 백엔드와 **동일** |
| `DB_USERNAME` / `DB_PASSWORD` | `pairing` / `pairing` | 백엔드와 **동일** |
| `S3_BUCKET` | (비어 있음) | 백엔드와 **동일** |
| `S3_KEY_PREFIX` | (비어 있음) | 백엔드와 **동일**. 다르면 백엔드가 올린 파일을 못 찾는다 |
| `S3_CDN_URL` | (비어 있음) | CloudFront를 쓸 때만 |
| `AWS_REGION` | `ap-northeast-2` | |

관리자 서버 전용 설정.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SERVER_PORT` | `8081` | 백엔드(8080)와 겹치지 않게 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:17001,...` | 관리자 **프론트** 주소. 쉼표 구분 |
| `SESSION_TIMEOUT` | `30m` | 무활동 기준 만료 |
| `SESSION_COOKIE_SECURE` | `false` | **HTTPS 배포에서는 `true`** |
| `SESSION_COOKIE_SAME_SITE` | `lax` | 프론트와 사이트가 다르면 `none` (이때 `secure=true` 필수) |
| `CSRF_ENABLED` | `true` | 테스트할 때만 `false` |
| `LOGIN_FAIL_MAX` | `5` | 연속 실패 시 계정 잠금 |
| `DB_POOL_SIZE` | `5` | 같은 RDS를 공유하므로 작게 |
| `SWAGGER_ENABLED` | `true` | 운영에서는 `false` 권장 (API 목록 노출) |

AWS 자격증명(`AWS_ACCESS_KEY`/`AWS_SECRET_KEY`)은 **주입하지 않는 것을 권한다.**
비워두면 EC2 인스턴스 IAM 역할을 쓴다. 백엔드 EC2에 붙인 것과 같은 S3 권한을 관리자 EC2 역할에도 주면 된다.

---

## 4. 프론트엔드 연동

세션 쿠키를 주고받으려면 **모든 요청에 credentials를 켜야 한다.** 이걸 빠뜨리면
로그인은 200이 오는데 다음 요청부터 401이 나는, 원인 찾기 어려운 증상이 생긴다.

```js
// axios 를 쓴다면 이 한 줄이면 CSRF까지 자동 처리된다.
// (XSRF-TOKEN 쿠키를 읽어 X-XSRF-TOKEN 헤더로 넣어주는 것이 axios 기본 동작)
axios.defaults.withCredentials = true;

// fetch 를 쓴다면 매 요청에 credentials: 'include' 를 넣고,
// 변경 요청(POST/PATCH/DELETE)에는 CSRF 토큰을 직접 실어야 한다.
await fetch(`${BASE}/api/v1/admin/members/12/lock`, {
  method: 'PATCH',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': getCookie('XSRF-TOKEN'),
  },
  body: JSON.stringify({ reason: '약관 위반' }),
});
```

호출 순서.

1. 로그인 화면 진입 → `GET /api/v1/admin/auth/csrf` (XSRF-TOKEN 쿠키를 받는다)
2. `POST /api/v1/admin/auth/login` → `ADMIN_SESSION` 쿠키를 받는다
3. 이후 모든 API 호출
4. 401이 오면 세션 만료 → 로그인 화면으로

CORS 설정에 프론트 주소가 정확히 들어 있어야 한다. 쿠키를 쓰므로 `*` 는 브라우저가 거부한다.

---

## 5. 구현된 것 / 스켈레톤

| 도메인 | 상태 | 경로 |
|---|---|---|
| 인증 | **동작함** | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `PATCH /auth/password` |
| 회원 관리 | **동작함** | `GET /members`, `GET /members/{id}`, `PATCH /members/{id}/lock`, `/unlock` |
| 파일 업로드 | **동작함** | `POST /files`, `DELETE /files` |
| 대시보드 | 회원 지표만 실제 집계 | `GET /dashboard` |
| 프로젝트 | 스켈레톤 (빈 목록) | `GET /projects` |
| 정산 | 스켈레톤 (빈 목록) | `GET /settlements` |
| 문의 | 스켈레톤 (빈 목록) | `GET /inquiries` |

모든 경로 앞에 `/api/v1/admin` 이 붙는다.

**새 관리 화면을 추가할 때는 `member` 도메인을 그대로 따라가면 된다.**

```
member/
├── domain/                        # 백엔드와 이름이 같아야 하는 enum (DB에 문자열로 저장됨)
├── infrastructure/persistence/    # JpaEntity + Repository + Specs(검색조건)
├── application/                   # 서비스
├── presentation/api/              # 컨트롤러 + request/response DTO
└── exception/                     # 도메인 에러코드
```

엔티티는 백엔드 `db/init/02-create-schema.sql` 의 테이블 정의를 그대로 옮긴다.
컬럼명을 틀리면 `ddl-auto=validate` 가 기동 단계에서 잡아준다.

---

## 6. 백엔드와 공유하기 때문에 조심할 것

**1. 공유 테이블의 스키마는 백엔드가 단일 소스다.**
이 프로젝트의 `ddl-auto` 는 `validate` 다. 절대 `update` 로 바꾸지 않는다.
두 서버가 각자 스키마를 고치기 시작하면 어느 쪽이 바꾼 건지 추적이 안 된다.
`account` 등에 컬럼 추가가 필요하면 백엔드의 `02-create-schema.sql` 을 먼저 고친다.

`admin_user` 만 예외다. 이 테이블은 관리자 서버가 소유하므로 컬럼을 바꿀 때
`AdminUserJpaEntity` 와 `db/schema-admin.sql` 두 곳을 함께 고친다.
(`IF NOT EXISTS` 는 테이블이 이미 있으면 아무 것도 하지 않으므로, 컬럼 추가는
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 를 스크립트에 덧붙여야 반영된다)

**2. enum 이름은 백엔드와 정확히 같아야 한다.**
`Role`, `AccountStatus`, `SignupType` 은 DB에 문자열로 저장된다.
한쪽에서 이름을 바꾸면 다른 쪽이 읽다가 터진다.
(`admin_user` 에는 enum 컬럼이 없으므로 이 제약과 무관하다)

**3. 관리자 서버는 회원 계정을 만들거나 비밀번호를 바꾸지 않는다.**
`AccountJpaEntity` 에 `lock` / `unlock` 만 열어 둔 것이 그 이유다.
계정 생성·비밀번호 변경은 백엔드(본인 요청)의 몫이지 관리 화면의 몫이 아니다.

**4. "정지"의 의미가 백엔드와 다르다.**
백엔드는 정지(SUSPENDED)를 `account.status` 가 아니라 **Redis**에서 관리한다(스키마 v12 결정).
이 서버는 Redis를 보지 않으므로 정지를 `status = LOCKED` 로 처리한다. 로그인 차단이라는 효과는 같다.
백엔드의 Redis 기반 정지와 완전히 맞추려면 Redis도 함께 공유하도록 확장해야 한다.

**5. 커넥션 풀을 작게 잡았다.** (`DB_POOL_SIZE=5`)
같은 RDS를 백엔드와 나눠 쓴다. 관리자 서버가 커넥션을 많이 물면 서비스 쪽이 마른다.

---

## 7. 배포

```bash
./gradlew clean bootJar
cp build/libs/pairing-admin.jar app.jar
docker build -t pairing-admin .
```

`Dockerfile` 은 백엔드와 같은 방식이다 (CI에서 만든 `app.jar` 를 복사). 포트만 8081이다.

배포 시 반드시 확인할 것.

- `SESSION_COOKIE_SECURE=true` (HTTPS)
- `CSRF_ENABLED=true` (기본값이지만 확인)
- `CORS_ALLOWED_ORIGINS` 에 실제 관리자 프론트 도메인
- `ADMIN_BOOTSTRAP_*` 이 남아 있지 않은지
- `SWAGGER_ENABLED=false`
- 보안 그룹: 관리자 서버는 사내 IP / VPN 에서만 접근하도록 좁히는 것을 권한다

---

## 8. 트러블슈팅

**로그인은 되는데 다음 요청이 401**
프론트에서 `withCredentials` / `credentials: 'include'` 가 빠졌다. 가장 흔한 원인이다.
크로스 사이트라면 `SESSION_COOKIE_SAME_SITE=none` + `SESSION_COOKIE_SECURE=true` 도 필요하다.

**POST/PATCH 가 403, 에러코드 `ADMIN_GLOBAL_009`**
CSRF 토큰 문제다. `GET /api/v1/admin/auth/csrf` 를 먼저 호출했는지, 헤더 이름이 `X-XSRF-TOKEN` 인지 확인한다.

**기동 시 `Schema-validation: missing table [admin_user]`**
`admin_user` 자동 생성이 막힌 것이다. DB 계정에 `CREATE` 권한이 없거나 `SQL_INIT_MODE=never` 다.
[db/init/05-create-admin-user-table.sql](db/init/05-create-admin-user-table.sql) 을 직접 실행한다.

**기동 시 그 밖의 `missing table` / `missing column`**
백엔드의 `02-create-schema.sql` 이 실행되지 않았거나, 엔티티 컬럼명이 실제와 다르다.

**기동 시 `permission denied for table ...`**
DB 객체 소유자가 `pairing` 이 아니다. 백엔드 쪽 `db/init/04-fix-object-owner.sql` 로 소유권을 정리한다.

**로그인이 계속 실패하고 5회 뒤 잠김**
`password_hash` 에 평문이 들어갔을 가능성이 높다. BCrypt 해시(`$2a$` 로 시작, 60자)여야 한다.

```sql
UPDATE admin_user SET locked_at = NULL, login_fail_count = 0 WHERE username = 'admin';
```

**비밀번호를 잊었다**
부트스트랩은 이미 있는 아이디를 건드리지 않으므로 초기화 용도로 쓸 수 없다.
계정 행을 지우고 부트스트랩을 다시 돌리는 것이 가장 간단하다. (계정이 하나뿐이라면)

```sql
DELETE FROM admin_user WHERE username = 'admin';
```
