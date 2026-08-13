# 회원 관리 페이지 연동 가이드 (프론트엔드용)

`Pairing-Admin-backend` 의 **회원 관리 화면**만 다룬다. 로그인은 [AUTH-FRONTEND.md](AUTH-FRONTEND.md) 참고.

- Base URL: `http://localhost:8081` (로컬) / 배포는 실제 도메인
- 모든 경로 앞에 `/api/v1/admin/members` 가 붙는다
- 인증: **세션 쿠키**. `axios.defaults.withCredentials = true` 가 없으면 전부 401 이다

응답 형식은 다른 관리자 API와 같다. 성공은 실제 데이터가 `data` 안에 있고(`res.data.data`),
실패는 `errorCode` 로 분기한다.

---

## 0. 화면 하나에 API 세 개

목록 화면은 요약 카드와 표를 **따로** 부른다. 한 번에 주지 않는 이유는, 그러면 페이지를 넘길
때마다 같은 집계를 다시 돌리기 때문이다. 카드는 검색 조건이 바뀌어도 변하지 않으므로
**처음 한 번만** 부르면 된다.

| 화면 | 메서드 | 경로 |
|---|---|---|
| 상단 요약 카드 6개 | `GET` | `/summary` |
| 목록 표 | `GET` | `/` |
| 상세 | `GET` | `/{accountId}` |
| 회원 정지 | `PATCH` | `/{accountId}/suspension` |
| 정지 해제 | `DELETE` | `/{accountId}/suspension` |

---

## 1. 요약 카드 — `GET /api/v1/admin/members/summary`

파라미터 없다.

```json
{
  "code": "MEMBER_STATS_FOUND",
  "data": {
    "total": 1250,
    "active": 1180,
    "suspended": 12,
    "withdrawn": 58,
    "clients": 420,
    "freelancers": 830
  }
}
```

> ⚠️ **여섯 값을 더해도 `total` 이 되지 않는다. 버그가 아니다.**
> - 가입 대기(`PENDING`)·잠금(`LOCKED`)은 카드가 없어서 정상/정지/탈퇴 어디에도 안 들어간다
> - 클라이언트·프리랜서 카드는 상태와 무관하게 세므로 앞의 세 값과 겹친다
>
> 카드에 "합계 = 전체" 를 표시하거나 검산하는 UI를 만들지 말 것.

`active` 는 **정지되지 않은** `ACTIVE` 만 센다. 관리자 계정은 전부에서 제외된다.

---

## 2. 목록 — `GET /api/v1/admin/members`

### 쿼리 파라미터

| 이름 | 값 | 비고 |
|---|---|---|
| `role` | `CLIENT` `FREELANCER` | 유형 필터 |
| `status` | `ACTIVE` `SUSPENDED` `WITHDRAWN` `PENDING` `LOCKED` | 상태 필터 |
| `signupMethod` | `EMAIL` `KAKAO` `GOOGLE` `SOCIAL` | 가입방식 필터 |
| `keyword` | 문자열 | 이름·이메일·휴대폰·**기업명** 부분 일치 |
| `page` | 0부터 | 기본 0 |
| `size` | 숫자 | 기본 20 |

전부 선택이다. **비우면 전체**다. 파라미터를 아예 안 보내도, 빈 문자열(`role=`)을 보내도
"조건 없음"으로 처리된다. 다만 **enum에 없는 값(`role=CLIENTS` 같은 오타)은 400** 이므로
아래처럼 값이 있는 것만 담아 보내는 편이 안전하다.

```js
// 값이 있는 것만 담는다
const params = Object.fromEntries(
  Object.entries({ role, status, signupMethod, keyword, page, size })
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
);
const { data } = await axios.get('/api/v1/admin/members', { params });
```

> ⚠️ **`sort` 파라미터는 무시된다.** 정렬은 최신 가입순으로 고정이다.
> (네이티브 쿼리에 정렬을 문자열로 이어 붙이면 SQL 주입 경로가 되어 막아 뒀다)
> 컬럼 헤더 클릭 정렬이 필요하면 말해 달라 — 서버에 허용 목록을 만들어야 한다.

### 응답

```json
{
  "code": "MEMBER_LIST_FOUND",
  "data": {
    "content": [
      {
        "accountId": 12,
        "name": "김담당",
        "companyName": "삼성전자",
        "email": "hong@example.com",
        "phone": "01012345678",
        "role": "CLIENT",
        "roleLabel": "클라이언트",
        "status": "ACTIVE",
        "statusLabel": "정상",
        "suspended": false,
        "signupMethod": "KAKAO",
        "signupMethodLabel": "카카오",
        "createdAt": "2026-03-14T10:22:31",
        "lastLoginAt": "2026-08-12T09:01:44",
        "activeProjectCount": 2
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 135,
    "totalPages": 7,
    "first": true,
    "last": false
  }
}
```

### 표에 그릴 때 주의할 것

**회원번호** — 별도 컬럼이 없다. `accountId` 를 그대로 쓴다.
`MEM-001` 처럼 보이게 하려면 프론트에서 포맷한다. (`MEM-${String(id).padStart(3,'0')}`)
**서버로 보낼 때는 원래 숫자를 보내야 한다.**

**이름·기업명 2줄** — 피그마처럼 기업명 위, 담당자명 아래로 그린다.
`companyName` 은 **클라이언트만 값이 있고 프리랜서는 `null`** 이다. null이면 한 줄로 그린다.

**라벨은 서버가 준다.** `roleLabel` `statusLabel` `signupMethodLabel` 을 그대로 쓰면 된다.
프론트에 매핑 테이블을 또 만들면 서버가 값을 추가할 때 화면만 영어로 남는다.

**`phone` 은 하이픈이 없다.** (`01012345678`) 표시할 때 프론트에서 넣는다.

**`status` 는 계정 상태가 아니라 화면용 값이다.** 정지된 회원은 DB상 `ACTIVE` 그대로지만
여기서는 `SUSPENDED` 로 내려온다. 정지가 다른 상태보다 우선한다. `suspended` 불리언도 같이
주므로 배지 스타일 분기는 그걸 쓰는 게 편하다.

| `status` | `statusLabel` | 의미 |
|---|---|---|
| `ACTIVE` | 정상 | |
| `SUSPENDED` | 정지 | **관리자가 건 정지.** 관리자만 풀 수 있다 |
| `LOCKED` | 잠금 | 비밀번호 5회 실패로 **자동** 잠김. 본인이 이메일 인증으로 푼다 |
| `PENDING` | 가입 대기 | 이메일 인증 전 |
| `WITHDRAWN` | 탈퇴 | |

> `LOCKED` 와 `SUSPENDED` 는 다른 것이다. 화면에서 "정지"로 뭉쳐 보여주지 말 것.
> 잠긴 회원을 정지할 수도 있고, 그때는 `SUSPENDED` 가 표시된다.

**탈퇴 회원도 목록에 나온다.** 탈퇴 시 이메일·휴대폰이 더미값으로 치환되므로
`withdrawn@...` 같은 값이 보이는 게 정상이다. 개인정보를 지운 결과다.

**`signupMethod` 가 `SOCIAL`** 로 오면 소셜 가입인데 공급자를 찾지 못한 경우다(연동 끊김 등).
"소셜"로 표시하고 카카오나 구글로 임의 추측하지 말 것.

---

## 3. 상세 — `GET /api/v1/admin/members/{accountId}`

```json
{
  "code": "MEMBER_FOUND",
  "data": {
    "accountId": 12,
    "name": "김담당",
    "email": "hong@example.com",
    "phone": "01012345678",
    "role": "CLIENT",
    "roleLabel": "클라이언트",
    "status": "ACTIVE",
    "statusLabel": "정상",
    "suspended": false,
    "suspendedAt": null,
    "suspendReason": null,
    "signupMethod": "EMAIL",
    "signupMethodLabel": "이메일",
    "emailVerified": true,
    "loginFailCount": 0,
    "lockedAt": null,
    "lastLoginAt": "2026-08-12T09:01:44",
    "createdAt": "2026-03-14T10:22:31",
    "updatedAt": "2026-08-12T09:01:44",
    "withdrawnAt": null,
    "withdrawReason": null,

    "profile": {
      "companyName": "삼성전자",
      "businessNo": "1248100998",
      "businessField": "IT·정보통신",
      "employeeCount": "100~299명",
      "birthDate": null,
      "address": "서울시 강남구",
      "grade": "GOLD",
      "gradeLabel": "골드",
      "aiMatchingAgreed": null,
      "matchingPaused": null,
      "condition": null,
      "skills": null
    },

    "activity": {
      "inProgressProjects": 2,
      "completedProjects": 8,
      "canceledProjects": 1,
      "totalTradeAmount": 12500000,
      "reviewCount": 7,
      "averageScore": 4.6
    }
  }
}
```

### `profile` — 역할마다 채워지는 필드가 다르다

한 객체 안에 클라이언트 필드와 프리랜서 필드가 같이 있다.
**해당하지 않는 쪽이 `null` 인 것이 정상이다.**

| 필드 | CLIENT | FREELANCER |
|---|---|---|
| `companyName` `businessNo` `businessField` `employeeCount` | ✅ | `null` |
| `birthDate` `aiMatchingAgreed` `matchingPaused` | `null` | ✅ |
| `condition` (근무 조건 19개) · `skills` (기술 스택 배열) | `null` | ✅ |
| `address` `grade` `gradeLabel` | ✅ | ✅ (각자의 프로필에서) |

**`profile` 자체가 `null`** 일 수 있다. 가입 도중 이탈해 프로필이 아직 없는 계정이다.
프리랜서는 **`profile.condition` 도 따로 `null`** 일 수 있다(근무 조건 미등록).
`data.profile?.condition?.jobRoleLabel` 처럼 옵셔널 체이닝으로 접근할 것.

코드값에는 **항상 짝이 되는 `*Label` 이 함께 온다.** 프론트에 매핑 테이블을 만들지 말 것.
필드별 상세와 화면 조립 방법은 [MEMBER-DETAIL-SCREENS.md](MEMBER-DETAIL-SCREENS.md) 참고.

**포트폴리오는 내려주지 않는다.** 관리 화면에서 볼 필요가 없다고 판단해 뺐다.

### `activity` — 6개 지표

| 필드 | 정의 |
|---|---|
| `inProgressProjects` | 종료·취소가 **아닌** 프로젝트 전부 (등록완료·모집중·협상중·계약대기·진행중·완료대기) |
| `completedProjects` | 종료(`CLOSED`) |
| `canceledProjects` | 취소(`CANCELED`) |
| `totalTradeAmount` | **결제 완료된** 정산의 거래액 합계. 미납·대기·실패는 제외 |
| `reviewCount` | 이 회원이 **받은** 리뷰 수 |
| `averageScore` | 받은 리뷰 평균 별점 |

프로젝트는 클라이언트면 본인이 등록한 것, 프리랜서면 **계약을 맺은** 것을 센다.
협상만 하다 만 프로젝트는 참여로 치지 않는다.

> ⚠️ **`averageScore` 는 리뷰가 없으면 `null` 이다.** `0` 이 아니다.
> `0.0` 으로 내리면 "별점 0점" 과 구분되지 않아 일부러 `null` 로 둔다.
> 별점 UI에 그대로 넣으면 `NaN` 이 뜨므로 `?? '-'` 같은 처리가 필요하다.
> `totalTradeAmount` 는 반대로 이력이 없으면 `0` 이다.

> ⚠️ **리뷰 수·평균 별점은 당분간 0으로 나올 수 있다.** 백엔드의 `review` 테이블에
> 옛 컬럼이 남아 있어 리뷰 저장이 실패하는 문제가 있다. 조회 쪽은 이미 붙어 있으니
> 백엔드가 고쳐지면 값이 저절로 채워진다. **화면은 지금 만들어 두면 된다.**

---

## 4. 회원 정지 — `PATCH /api/v1/admin/members/{accountId}/suspension`

```json
{ "reason": "약관 위반 신고 누적" }
```

`reason` 은 **필수**이고 500자 이하다. 회원이 문의했을 때 근거가 되므로 비워 보내면 400이다.

응답은 **상세 조회와 완전히 같은 형태**다(`code` 만 `MEMBER_SUSPENDED`).
그래서 정지 후 상세를 다시 부를 필요 없이 응답을 그대로 상태에 넣으면 된다.

```js
const { data } = await axios.patch(
  `/api/v1/admin/members/${accountId}/suspension`,
  { reason }
);
setMember(data.data);   // 다시 GET 하지 않아도 된다
```

정지하면 **그 회원은 즉시 로그아웃되고 다시 로그인할 수 없다.** 액세스 토큰이 남아 있어도
바로 끊긴다. 되돌릴 수 있는 동작이므로 확인 모달은 넣되, "영구 삭제" 같은 문구는 쓰지 말 것.

## 5. 정지 해제 — `DELETE /api/v1/admin/members/{accountId}/suspension`

본문 없다. 응답은 정지와 같은 형태(`code` 는 `SUSPENSION_RELEASED`).
해제하면 비밀번호 실패 횟수도 0으로 초기화된다. **몇 번이든 정지·해제를 반복할 수 있다.**

---

## 6. 에러 코드

| HTTP | `errorCode` | 언제 | 화면에서 |
|---|---|---|---|
| 400 | `ADMIN_GLOBAL_002` | `reason` 누락/500자 초과, enum에 없는 필터값 | `message` 를 그대로 노출 |
| 401 | `ADMIN_GLOBAL_006` | 세션 만료 | 로그인 페이지로 보낸다 |
| 403 | `ADMIN_GLOBAL_009` | CSRF 토큰 불일치 | 새로고침 안내 |
| 403 | `ADMIN_MEMBER_002` | 관리자 역할 계정을 정지하려 함 | 정지 버튼을 아예 숨기는 편이 낫다 |
| 404 | `ADMIN_MEMBER_001` | 없는 회원 | 목록으로 되돌린다 |
| 409 | `ADMIN_MEMBER_003` | 이미 정지된 회원을 또 정지 / 정지 아닌데 해제 | 목록을 새로고침한다 |
| 409 | `ADMIN_MEMBER_004` | 탈퇴 회원을 정지하려 함 | 탈퇴 회원은 정지 버튼을 숨긴다 |

`ADMIN_MEMBER_003` 은 대개 **다른 관리자가 먼저 처리했을 때** 난다.
"이미 처리된 회원입니다. 목록을 새로고침합니다." 정도로 안내하고 다시 조회하면 된다.

버튼 노출 규칙을 정리하면:

```js
const canSuspend = member.role !== 'ADMIN'
  && member.status !== 'WITHDRAWN'
  && !member.suspended;

const canRelease = member.suspended;
```

---

## 7. 알아 두면 좋은 것

**정지된 회원이 로그인하면** 백엔드가 `403 AU_022 이용이 정지된 계정입니다.` 를 준다.
서비스 프론트(`Pairing-frontend`) 쪽 이야기이고 관리자 화면과는 무관하다.

**정지는 즉시 반영된다.** 관리자 서버와 서비스 백엔드가 같은 Redis를 보고 있어서,
정지 버튼을 누른 시점부터 그 회원의 다음 요청이 막힌다. 반영까지 기다리는 UI는 필요 없다.

**목록의 집계 컬럼(`activeProjectCount`)은 조인이 무겁다.** `size` 를 100 이상으로 크게
잡으면 느려질 수 있다. 기본 20을 권한다.
