# 관리자 로그인 연동 가이드 (프론트엔드용)

`Pairing-admin` 서버의 **인증 부분만** 다룬다. 회원 관리 같은 업무 API는 여기 없다.

- Base URL: `http://localhost:8081` (로컬) / 배포는 실제 도메인
- 인증 방식: **세션 쿠키**. 토큰을 직접 저장하거나 헤더에 붙이지 않는다.
- 모든 경로 앞에 `/api/v1/admin/auth` 가 붙는다.

---

## 0. 먼저 알아야 할 것 — 쿠키 2개

세션 방식이라 브라우저가 쿠키 두 개를 들고 다닌다. **둘 다 프론트가 직접 다루지 않는다.**

| 쿠키 | 발급 시점 | 역할 | JS 접근 |
|---|---|---|---|
| `ADMIN_SESSION` | 로그인 성공 | 로그인 상태 | ❌ HttpOnly — 읽을 수 없다 |
| `XSRF-TOKEN` | 아무 요청이나 하면 | CSRF 방어 | ✅ 읽을 수 있다 (axios가 자동 처리) |

`ADMIN_SESSION` 은 JS로 읽을 수 없다. **"로그인했는지"를 쿠키로 판단하려 하지 말 것.**
`GET /me` 를 호출해서 200이면 로그인 상태, 401이면 아니다. (아래 6번 참고)

가장 중요한 한 줄.

```js
axios.defaults.withCredentials = true;   // 이거 없으면 쿠키가 안 실린다
```

이걸 빠뜨리면 **로그인은 200인데 다음 요청부터 401**이 나온다. 가장 흔한 사고다.

---

## 1. 공통 응답 형식

### 성공

```json
{
  "timestamp": "2026-08-11T03:36:04.505792100Z",
  "status": 200,
  "code": "LOGIN_SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": { }
}
```

실제 데이터는 항상 `data` 안에 있다. `res.data.data` 로 꺼내야 한다는 뜻이다.

### 실패

```json
{
  "timestamp": "2026-08-11T03:14:03.615675300Z",
  "status": 401,
  "errorCode": "ADMIN_AUTH_001",
  "message": "아이디 또는 비밀번호가 올바르지 않습니다.",
  "traceId": "07f82f25"
}
```

- 분기는 `errorCode` 로 한다. `message` 는 문구가 바뀔 수 있다.
- 사용자에게 보여줄 문구는 `message` 를 그대로 써도 된다. 서버가 한국어로 내려준다.
- `traceId` 는 서버 로그 추적용이다. 에러 화면 구석에 작게 노출해두면 문의 대응이 쉬워진다.
  응답 헤더 `X-Trace-Id` 로도 같은 값이 온다.

---

## 2. `GET /api/v1/admin/auth/csrf` — CSRF 토큰 받기

로그인 화면에 진입할 때 **한 번** 호출한다. 로그인 전에도 호출할 수 있다.

이 요청의 응답으로 `XSRF-TOKEN` 쿠키가 내려온다. axios를 쓰면 응답 본문은 쓸 일이 없다.
(쿠키만 있으면 되고, axios가 알아서 헤더에 실어준다)

**요청** — 없음

**응답 `200`**

```json
{
  "timestamp": "2026-08-11T03:10:38.071263400Z",
  "status": 200,
  "code": "CSRF_TOKEN_ISSUED",
  "message": "조회에 성공했습니다.",
  "data": {
    "headerName": "X-XSRF-TOKEN",
    "token": "34e3da3d-6982-4a2f-b564-8b19f73fce01",
    "enabled": true
  }
}
```

`enabled: false` 면 서버가 CSRF를 꺼둔 상태(`CSRF_ENABLED=false`)라 토큰 없이 호출해도 된다.
개발 환경에서만 그렇게 띄운다. **운영에서는 항상 `true` 라고 가정하고 짜면 된다.**

---

## 3. `POST /api/v1/admin/auth/login` — 로그인

**이메일이 아니라 아이디다.** 관리자 계정은 회원 테이블과 분리된 `admin_user` 테이블을 쓴다.
서비스 회원 계정으로는 로그인할 수 없다.

이 API만 CSRF 토큰 없이 호출할 수 있다. (로그인 전에는 세션도 토큰도 없는 게 정상이므로)

**요청**

```json
{
  "username": "admin",
  "password": "Admin!2345"
}
```

**응답 `200`** — `Set-Cookie: ADMIN_SESSION=...` 이 함께 내려온다

```json
{
  "status": 200,
  "code": "LOGIN_SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": {
    "adminId": 1,
    "username": "admin",
    "lastLoginAt": "2026-08-11T12:36:04.430229"
  }
}
```

**실패**

| HTTP | `errorCode` | 상황 | 화면 처리 |
|---|---|---|---|
| 401 | `ADMIN_AUTH_001` | 아이디 없음 **또는** 비밀번호 틀림 | 입력 폼에 에러 문구 |
| 403 | `ADMIN_AUTH_002` | 5회 연속 실패로 잠김 | 잠김 안내. 프론트에서 풀 수 없다 |
| 403 | `ADMIN_AUTH_003` | 사용할 수 없는 계정 | 관리자 문의 안내 |
| 400 | `ADMIN_GLOBAL_002` | 아이디/비밀번호 미입력 | `message` 에 어떤 필드가 문제인지 담겨 온다 |

아이디가 없는 것과 비밀번호가 틀린 것을 **일부러 구분하지 않는다.** 구분하면 어떤 아이디가
존재하는지 알려주는 셈이 된다. 프론트에서도 나눠서 안내하지 말 것.

> **잠금 주의** — 비밀번호를 5번 연속 틀리면 계정이 잠긴다. 계정이 하나뿐이라
> 잠기면 아무도 못 들어온다. 푸는 건 DB에서만 가능하다.
> 로그인 폼에서 자동 재시도 같은 걸 넣지 말 것.

---

## 4. `GET /api/v1/admin/auth/me` — 내 정보 / 세션 확인

로그인 여부를 확인하는 표준 방법이다. 앱을 처음 띄울 때, 그리고 라우트 가드에서 쓴다.

**응답 `200`**

```json
{
  "status": 200,
  "code": "ADMIN_FOUND",
  "message": "조회에 성공했습니다.",
  "data": {
    "adminId": 1,
    "username": "admin",
    "lastLoginAt": "2026-08-11T12:36:04.430229"
  }
}
```

**응답 `401` `ADMIN_GLOBAL_006`** — 로그인 안 됨 또는 세션 만료(기본 30분 무활동)

---

## 5. `POST /api/v1/admin/auth/logout` — 로그아웃

CSRF 토큰이 필요하다. (상태를 바꾸는 요청이므로)

**응답 `200`**

```json
{ "status": 200, "code": "LOGOUT_SUCCESS", "message": "로그아웃되었습니다.", "data": null }
```

서버가 세션을 즉시 무효화하고 쿠키를 만료시킨다. 프론트는 로그인 화면으로 보내면 된다.

세션이 이미 만료된 상태에서 호출하면 `401` 이 온다. **이 경우도 로그아웃 성공으로 취급한다.**
어차피 결과가 같다.

---

## 6. `PATCH /api/v1/admin/auth/password` — 비밀번호 변경

**요청**

```json
{
  "currentPassword": "Admin!2345",
  "newPassword": "NewPassw0rd!"
}
```

새 비밀번호 규칙: **영문·숫자·특수문자를 모두 포함한 8~30자.**
프론트에서도 같은 규칙으로 미리 검사해주면 왕복을 줄일 수 있다.

```js
const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,30}$/;
```

**응답 `200`** — `code: "PASSWORD_CHANGED"`. 세션은 유지된다. 다시 로그인할 필요 없다.

**실패**

| HTTP | `errorCode` | 상황 |
|---|---|---|
| 400 | `ADMIN_AUTH_004` | 현재 비밀번호가 틀림 |
| 400 | `ADMIN_AUTH_005` | 새 비밀번호가 기존과 같음 |
| 400 | `ADMIN_GLOBAL_002` | 형식 규칙 위반 |

---

## 7. 전역 에러 코드

인증 API 외에도 공통으로 나올 수 있는 것들이다.

| HTTP | `errorCode` | 의미 | 처리 |
|---|---|---|---|
| 401 | `ADMIN_GLOBAL_006` | 로그인 필요 / 세션 만료 | **로그인 화면으로 이동** |
| 403 | `ADMIN_GLOBAL_005` | 권한 없음 | 안내 문구 |
| 403 | `ADMIN_GLOBAL_009` | CSRF 토큰 문제 | `/csrf` 재호출 후 1회 재시도 |
| 400 | `ADMIN_GLOBAL_002` | 요청 형식 오류 | `message` 노출 |
| 404 | `ADMIN_GLOBAL_004` | 없는 API 경로 | 개발 중 오타 |
| 500 | `ADMIN_GLOBAL_001` | 서버 오류 | `traceId` 와 함께 안내 |

---

## 8. axios 설정 (권장)

```js
// src/api/client.js
import axios from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_ADMIN_API_URL ?? 'http://localhost:8081',

  // 이 줄이 핵심. 쿠키를 주고받게 한다.
  withCredentials: true,

  // 아래 두 줄은 axios 기본값과 같지만, 서버 설정과 짝이라는 걸 남겨두려고 명시한다.
  // XSRF-TOKEN 쿠키를 읽어 X-XSRF-TOKEN 헤더로 자동으로 넣어준다.
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

// 401이면 세션이 끝난 것이다. 어디서 났든 로그인 화면으로 보낸다.
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const errorCode = error.response?.data?.errorCode;

    if (error.response?.status === 401) {
      // 로그인 API 자체의 401(아이디/비번 틀림)은 로그인 화면에서 처리해야 하므로 제외한다.
      const isLoginRequest = error.config?.url?.includes('/auth/login');

      if (!isLoginRequest && window.location.pathname !== '/login') {
        window.location.replace('/login?expired=1');
        return new Promise(() => {});   // 이후 catch가 실행되지 않게 멈춘다
      }
    }

    // CSRF 토큰이 어긋났으면 한 번만 다시 받아서 재시도한다.
    if (errorCode === 'ADMIN_GLOBAL_009' && !error.config._csrfRetried) {
      error.config._csrfRetried = true;
      return api.get('/api/v1/admin/auth/csrf').then(() => api.request(error.config));
    }

    return Promise.reject(error);
  },
);
```

```js
// src/api/auth.js
import { api } from './client';

const BASE = '/api/v1/admin/auth';

export const fetchCsrfToken = () => api.get(`${BASE}/csrf`).then((r) => r.data.data);

export const login = (username, password) =>
  api.post(`${BASE}/login`, { username, password }).then((r) => r.data.data);

export const logout = () =>
  api.post(`${BASE}/logout`).catch(() => {
    // 세션이 이미 만료됐어도 로그아웃은 성공으로 본다. 결과가 같다.
  });

export const fetchMe = () => api.get(`${BASE}/me`).then((r) => r.data.data);

export const changePassword = (currentPassword, newPassword) =>
  api.patch(`${BASE}/password`, { currentPassword, newPassword });
```

---

## 9. 화면 흐름

### 앱 시작 시 — 로그인 상태 복원

세션은 서버에 있으므로 새로고침해도 살아 있다. localStorage에 뭔가 저장할 필요가 없다.

```js
async function bootstrap() {
  try {
    const me = await fetchMe();      // 200이면 로그인 상태
    setAdmin(me);
  } catch {
    setAdmin(null);                  // 401이면 비로그인
  } finally {
    setLoading(false);               // 이 전에는 라우팅 판단을 하지 않는다
  }
}
```

`loading` 을 두지 않으면 `/me` 응답이 오기 전에 "비로그인"으로 판단해서
**새로고침할 때마다 로그인 화면이 깜빡**한다.

### 로그인 화면

```js
useEffect(() => { fetchCsrfToken(); }, []);   // 진입 시 1회

async function onSubmit({ username, password }) {
  try {
    const me = await login(username, password);
    setAdmin(me);
    navigate('/');
  } catch (e) {
    const { errorCode, message } = e.response?.data ?? {};

    if (errorCode === 'ADMIN_AUTH_002') {
      setError('계정이 잠겼습니다. 관리자에게 문의해 주세요.');
    } else {
      setError(message ?? '로그인에 실패했습니다.');
    }
  }
}
```

### 로그아웃

```js
await logout();
setAdmin(null);
navigate('/login');
```

---

## 10. fetch 를 쓴다면

axios가 자동으로 해주던 것을 직접 해야 한다.

```js
function getCookie(name) {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.split('=')[1];
}

await fetch(`${BASE_URL}/api/v1/admin/auth/logout`, {
  method: 'POST',
  credentials: 'include',                                  // 쿠키 주고받기
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': decodeURIComponent(getCookie('XSRF-TOKEN') ?? ''),
  },
});
```

`GET` 은 CSRF 헤더가 필요 없다. `POST` / `PATCH` / `PUT` / `DELETE` 에만 붙인다.

---

## 11. 흔한 실수

**로그인은 200인데 다음 요청이 401**
`withCredentials: true` (또는 `credentials: 'include'`)가 빠졌다. 압도적으로 1위 원인이다.
로그인 요청에만 넣고 나머지에 안 넣은 경우도 포함된다. axios 인스턴스에 한 번만 설정할 것.

**403, `errorCode: ADMIN_GLOBAL_009`**
CSRF 토큰이 없거나 어긋났다. `/csrf` 를 먼저 호출했는지, 헤더 이름이 `X-XSRF-TOKEN` 인지 확인한다.
`fetch` 로 직접 넣는다면 쿠키 값에 `decodeURIComponent` 를 씌웠는지도 확인한다.

**브라우저 콘솔에 CORS 에러**
서버의 `CORS_ALLOWED_ORIGINS` 에 프론트 주소가 정확히 들어 있어야 한다.
쿠키를 쓰기 때문에 와일드카드(`*`)는 브라우저가 거부한다.
포트까지 정확히 일치해야 한다 — `localhost:17001` 과 `127.0.0.1:17001` 은 다른 오리진이다.

**배포하니 쿠키가 아예 안 붙는다**
프론트와 API의 사이트가 다르면(`admin.pairing.com` ↔ `api-admin.pairing.com`)
서버에 `SESSION_COOKIE_SAME_SITE=none` + `SESSION_COOKIE_SECURE=true` 가 필요하다.
그리고 `SameSite=None` 쿠키는 **HTTPS 에서만** 동작한다.

**새로고침하면 로그인 화면이 잠깐 보인다**
`/me` 응답 전에 라우팅을 판단하고 있다. 9번의 `loading` 처리를 참고한다.

**세션이 자꾸 풀린다**
기본 만료가 **무활동 30분**이다. 요청이 있을 때마다 갱신되므로 작업 중에는 안 풀린다.
길게 쓰려면 서버의 `SESSION_TIMEOUT` 을 늘린다.

---

## 12. 로컬 개발 세팅

| | 값 |
|---|---|
| 관리자 API | `http://localhost:8081` |
| 관리자 프론트 | `http://localhost:17001` (서버 CORS 기본 허용값) |
| 백엔드 API | `http://localhost:8080` (관리자 로그인과 무관) |

프론트를 다른 포트로 띄운다면 관리자 서버를 이렇게 실행한다.

```bash
CORS_ALLOWED_ORIGINS=http://localhost:5173 ./gradlew bootRun
```

Swagger에서 직접 눌러보며 확인하고 싶으면 CSRF를 끄고 띄우면 편하다. (운영에서는 끄지 않는다)

```bash
CSRF_ENABLED=false ./gradlew bootRun
```

- Swagger UI: http://localhost:8081/swagger-ui/index.html
- 계정이 아직 없다면 프로젝트 README 2-2 를 참고해 부트스트랩으로 하나 만든다.
