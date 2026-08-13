# 회원 상세 화면 — 역할별로 무엇이 뜨는가

`GET /api/v1/admin/members/{accountId}` 응답 하나로 클라이언트 상세와 프리랜서 상세를 모두 그린다.
**호출하는 API는 같고, `role` 에 따라 채워지는 필드만 다르다.**

API 규약(파라미터·에러코드 등)은 [MEMBER-FRONTEND.md](MEMBER-FRONTEND.md) 참고.
이 문서는 **화면에 무엇이 실제로 뜨는지**만 다룬다.

---

## 0. 결론부터

응답은 세 덩어리다.

| 블록 | 클라이언트 | 프리랜서 |
|---|---|---|
| **기본 정보** (21개 필드) | 완전히 동일 | 완전히 동일 |
| **`profile`** | 기업 정보 **6개** | 개인 정보 5개 + `condition` 19개 + `skills` 배열 |
| **`activity`** (6개 지표) | 완전히 동일 | 완전히 동일 |

**다른 곳은 `profile` 하나뿐이다.** 나머지 두 블록은 역할과 무관하게 같은 필드가 같은 규칙으로 온다.
그래서 화면도 프로필 카드만 분기하고 위아래는 공용 컴포넌트로 만들면 된다.

```jsx
<MemberBasicInfo member={m} />                        {/* 공용 */}
{m.role === 'CLIENT' ? <ClientProfile p={m.profile} />
                     : <FreelancerProfile p={m.profile} />}
<MemberActivity a={m.activity} />                     {/* 공용 */}
```

> 프리랜서 상세는 근무 조건(`condition`)과 기술 스택(`skills`)이 추가되어 있다. 3번 항목 참고.
> **포트폴리오는 넣지 않았다.**

---

## 1. 기본 정보 — 양쪽 동일 (21개)

| 필드 | 타입 | 값이 없을 때 | 비고 |
|---|---|---|---|
| `accountId` | number | — | **화면의 회원번호.** 별도 컬럼 없음 |
| `name` | string | — | 클라이언트는 담당자명 |
| `email` | string | — | 탈퇴 시 더미값으로 치환됨 |
| `phone` | string | — | **하이픈 없음** (`01012345678`) |
| `role` | `CLIENT` \| `FREELANCER` | — | |
| `roleLabel` | string | — | "클라이언트" / "프리랜서" |
| `status` | enum | — | 화면용 상태 (아래 참고) |
| `statusLabel` | string | — | 그대로 출력 |
| `suspended` | boolean | — | 배지·버튼 분기용 |
| `suspendedAt` | datetime | `null` | 정지 중이 아니면 null |
| `suspendReason` | string | `null` | 정지 중이 아니면 null |
| `signupMethod` | `EMAIL`\|`KAKAO`\|`GOOGLE`\|`SOCIAL` | — | |
| `signupMethodLabel` | string | — | "이메일"/"카카오"/"구글"/"소셜" |
| `emailVerified` | boolean | — | |
| `loginFailCount` | number | — | 0~5 |
| `lockedAt` | datetime | `null` | **자동 잠금** 시각. 정지와 무관 |
| `lastLoginAt` | datetime | `null` | 로그인한 적 없으면 null |
| `createdAt` | datetime | — | 가입일 |
| `updatedAt` | datetime | — | |
| `withdrawnAt` | datetime | `null` | 탈퇴 회원만 |
| `withdrawReason` | string | `null` | 탈퇴 회원만 |

`status` 값은 5가지이고 **정지가 계정 상태보다 우선**한다.

| `status` | `statusLabel` | 누가 푸는가 |
|---|---|---|
| `ACTIVE` | 정상 | — |
| `SUSPENDED` | 정지 | **관리자만** |
| `LOCKED` | 잠금 | 회원 본인 (이메일 인증) |
| `PENDING` | 가입 대기 | 회원 본인 (이메일 인증) |
| `WITHDRAWN` | 탈퇴 | — |

정지된 회원의 실제 계정 상태는 `ACTIVE` 그대로지만 `status` 에는 `SUSPENDED` 가 온다.
`lockedAt` 이 있으면서 `suspended: true` 인 경우도 있다 — 잠긴 회원을 정지한 것이고, 이때 화면은 "정지"로 보인다.

---

## 2. 클라이언트 상세

```json
{
  "accountId": 12,
  "name": "김담당",
  "role": "CLIENT",
  "roleLabel": "클라이언트",
  "...": "기본 정보 나머지",

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
```

### `profile` — 값이 오는 필드 6개

| 필드 | 예시 | 비고 |
|---|---|---|
| `companyName` | `"삼성전자"` | 기업명 |
| `businessNo` | `"1248100998"` | **하이픈 없는 10자리.** 표시할 때 `000-00-00000` 로 포맷 |
| `businessField` | `"IT·정보통신"` | 사업분야. 자유 문자열(40자) |
| `employeeCount` | `"100~299명"` | **문자열이다.** 숫자가 아니라 구간 표기 |
| `address` | `"서울시 강남구"` | `null` 가능 |
| `grade` / `gradeLabel` | `"GOLD"` / `"골드"` | 실버·골드·다이아 |

`birthDate` 와 프리랜서 전용 4개(`aiMatchingAgreed` `matchingPaused` `condition` `skills`)는
**항상 `null`** 이다. 클라이언트에는 해당 개념이 없다.

---

## 3. 프리랜서 상세

```json
{
  "accountId": 34,
  "name": "김프리",
  "role": "FREELANCER",
  "roleLabel": "프리랜서",
  "...": "기본 정보 나머지",

  "profile": {
    "companyName": null,
    "businessNo": null,
    "businessField": null,
    "employeeCount": null,
    "birthDate": "1995-03-02",
    "address": "서울시 마포구",
    "grade": "SENIOR",
    "gradeLabel": "시니어",
    "aiMatchingAgreed": true,
    "matchingPaused": false,

    "condition": {
      "jobCategory": "DEVELOPMENT",
      "jobCategoryLabel": "개발",
      "jobRole": "BACKEND",
      "jobRoleLabel": "백엔드 개발자",
      "affiliation": "무소속",
      "careerYears": 5,
      "hasFreelanceExperience": true,
      "workStyle": "REMOTE",
      "workStyleLabel": "재택",
      "workForm": "FULL_TIME",
      "workFormLabel": "풀타임",
      "payUnit": "MONTHLY",
      "payUnitLabel": "월급",
      "payAmount": 5000000,
      "minAcceptAmount": 4000000,
      "availableFrom": "2026-09-01",
      "startNegotiable": true,
      "periodValue": 6,
      "periodUnit": "MONTH",
      "periodUnitLabel": "개월"
    },

    "skills": [
      { "code": "DOTNET",      "label": "C#/.NET",    "level": "BEGINNER", "levelLabel": "초급" },
      { "code": "SPRING_BOOT", "label": "Spring Boot", "level": "ADVANCED", "levelLabel": "고급" }
    ]
  },

  "activity": {
    "inProgressProjects": 1,
    "completedProjects": 3,
    "canceledProjects": 0,
    "totalTradeAmount": 1500000,
    "reviewCount": 2,
    "averageScore": 4.5
  }
}
```

### `profile` 직속 5개

| 필드 | 예시 | 비고 |
|---|---|---|
| `birthDate` | `"1995-03-02"` | `yyyy-MM-dd`. 나이는 프론트에서 계산 |
| `address` | `"서울시 마포구"` | `null` 가능 |
| `grade` / `gradeLabel` | `"SENIOR"` / `"시니어"` | 주니어·시니어·마스터 |
| `aiMatchingAgreed` | `true` | AI 매칭 동의 |
| `matchingPaused` | `false` | 매칭 일시중지 |

기업 관련 4개(`companyName` `businessNo` `businessField` `employeeCount`)는 **항상 `null`** 이다.

### `profile.condition` — 근무 조건 19개

| 필드 | 예시 | 비고 |
|---|---|---|
| `jobCategory` / `jobCategoryLabel` | `DEVELOPMENT` / 개발 | 직군. 개발·디자인 2종 |
| `jobRole` / `jobRoleLabel` | `BACKEND` / 백엔드 개발자 | 직무. 27종 |
| `affiliation` | `"무소속"` | 소속. `null` 가능 |
| `careerYears` | `5` | 경력 연수 |
| `hasFreelanceExperience` | `true` | 프리랜서 경험 유무 |
| `workStyle` / `workStyleLabel` | `REMOTE` / 재택 | 재택·상주·모두 가능 |
| `workForm` / `workFormLabel` | `FULL_TIME` / 풀타임 | 풀타임·파트타임·모두 가능 |
| `payUnit` / `payUnitLabel` | `MONTHLY` / 월급 | 시급·일급·월급 |
| `payAmount` | `5000000` | 희망 단가 |
| `minAcceptAmount` | `4000000` | 수용 가능 최소 금액. `null` 가능 |
| `availableFrom` | `"2026-09-01"` | 투입 가능일. `null` 가능 |
| `startNegotiable` | `true` | 시작일 협의 가능 |
| `periodValue` / `periodUnit` / `periodUnitLabel` | `6` / `MONTH` / 개월 | 희망 기간 |

> ⚠️ **`condition` 자체가 `null` 일 수 있다.** 가입만 하고 근무 조건을 아직 등록하지 않은
> 프리랜서다. 조건이 없으면 AI 매칭 대상이 아니므로 **"조건 미등록"** 으로 표시하면 된다.
> `profile?.condition?.jobRoleLabel` 처럼 접근할 것.

### `profile.skills` — 기술 스택 (배열)

```json
{ "code": "SPRING_BOOT", "label": "Spring Boot", "level": "ADVANCED", "levelLabel": "고급" }
```

없으면 **빈 배열 `[]`** 이다(`null` 아님). 정렬은 `code` 오름차순 고정이라 요청마다 순서가 바뀌지 않는다.
숙련도는 `BEGINNER`/`INTERMEDIATE`/`ADVANCED` 3단계(초급·중급·고급)다.

`label` 을 코드에서 유도하려 하지 말 것. `DOTNET → C#/.NET`, `SCIKIT_LEARN → scikit-learn` 처럼
규칙으로 안 되는 것이 섞여 있어 서버가 표로 관리한다.

---

## 4. 코드값에는 항상 `*Label` 이 같이 온다

`grade` `jobCategory` `jobRole` `workStyle` `workForm` `payUnit` `periodUnit`
`skills[].code` `skills[].level` — **전부 짝이 되는 라벨 필드가 함께 온다.**
프론트에 매핑 테이블을 만들 필요가 없다.

```jsx
{p.condition.jobRoleLabel}      {/* "백엔드 개발자" — 이렇게 쓰면 된다 */}
```

`grade` 는 특히 조심할 값이었다. **같은 필드인데 역할마다 값 체계가 다르다.**

| 역할 | 값 | 표시 |
|---|---|---|
| CLIENT | `SILVER` / `GOLD` / `DIAMOND` | 실버 / 골드 / 다이아 |
| FREELANCER | `JUNIOR` / `SENIOR` / `MASTER` | 주니어 / 시니어 / 마스터 |

프론트가 매핑하면 역할을 잘못 짚었을 때 **조용히 틀린 등급**이 표시되므로 서버가 만들어 준다.
`gradeLabel` 을 그대로 쓰면 된다.

> 모르는 코드가 오면 라벨 자리에 **코드 원문이 그대로** 온다(`"SPRING_BOOT"` 같은 식).
> 백엔드에 값이 추가됐는데 관리자 서버가 아직 모를 때 그렇다. 화면이 죽지는 않으니,
> 한글 대신 영문 코드가 보이기 시작하면 알려 달라.

---

## 5. 활동 현황 — 양쪽 동일 (6개)

| 필드 | 타입 | 없을 때 | 정의 |
|---|---|---|---|
| `inProgressProjects` | number | `0` | 종료·취소가 **아닌** 프로젝트 전부 |
| `completedProjects` | number | `0` | 종료(`CLOSED`) |
| `canceledProjects` | number | `0` | 취소(`CANCELED`) |
| `totalTradeAmount` | number | `0` | **결제 완료된** 정산의 거래액 합계 |
| `reviewCount` | number | `0` | **받은** 리뷰 수 |
| `averageScore` | number | **`null`** | 받은 리뷰 평균 별점 |

세는 경로만 역할마다 다르고 **의미와 필드는 같다.**

- 클라이언트 → 본인이 **등록한** 프로젝트
- 프리랜서 → **계약을 맺은** 프로젝트 (협상만 하다 만 건은 제외)

> ⚠️ `averageScore` 만 `null` 이고 나머지 5개는 `0` 이다. 별점 컴포넌트에 `null` 을 그대로 넣으면
> `NaN` 이 뜬다. `averageScore ?? '-'` 로 처리할 것.

> ⚠️ **리뷰 수·평균 별점은 당분간 0으로 보일 수 있다.** 백엔드 `review` 테이블에 옛 컬럼이 남아
> 리뷰 저장이 실패하는 문제가 있다. 조회는 이미 붙어 있어서 백엔드가 고쳐지면 자동으로 채워진다.
> **화면은 지금 만들어 두면 된다.**

---

## 6. `profile` 이 통째로 `null` 인 경우

가입 도중 이탈해 프로필 행이 아직 없는 계정이다. 실제로 존재한다.

```js
if (!member.profile) {
  // "프로필 정보가 없습니다" 안내. 기본 정보와 활동 현황은 그대로 보여준다.
}
```

역할별 판정 기준은 이렇다.
- 클라이언트 → `client_profile` 이 없거나 기업명이 비어 있으면 `null`
- 프리랜서 → `freelancer_profile` 이 없거나 생년월일이 비어 있으면 `null`

**항상 옵셔널 체이닝으로 접근할 것.** `member.profile?.companyName`

---

## 7. 아직 안 내려주는 것

**포트폴리오는 의도적으로 뺐다.** (`portfolio` 테이블 — 제목·링크·파일)
관리 화면에서 볼 필요가 없다고 판단했다. 나중에 필요해지면 배열 하나 추가하면 된다.

클라이언트 쪽에 더 붙일 수 있는 것은 회사 로고(`client_profile.logo_file_id`) 정도다.

---

## 8. 화면 조립 체크리스트

- [ ] `phone` 하이픈 포맷 (`010-1234-5678`)
- [ ] `businessNo` 하이픈 포맷 (`124-81-00998`) — 클라이언트만
- [ ] `accountId` → `MEM-001` 포맷. **서버에는 원래 숫자를 보낼 것**
- [ ] 코드값은 `*Label` 을 쓴다 (프론트에 매핑 테이블 만들지 않기)
- [ ] `averageScore` 의 `null` 처리
- [ ] `profile` 자체의 `null` 처리
- [ ] `profile.condition` 의 `null` 처리 (조건 미등록 프리랜서)
- [ ] `payAmount` `minAcceptAmount` 천 단위 콤마
- [ ] `birthDate` → 나이 계산
- [ ] 정지 버튼 노출 조건 — `role !== 'ADMIN' && status !== 'WITHDRAWN' && !suspended`
- [ ] 해제 버튼 노출 조건 — `suspended === true`
- [ ] `LOCKED` 를 "정지"로 표시하지 않기 (다른 상태다)
