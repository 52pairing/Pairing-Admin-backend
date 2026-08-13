# 프로젝트 관리 · 거래정산 관리 페이지 연동 가이드 (프론트엔드용)

`Pairing-Admin-backend` 의 **프로젝트 관리(R38)** 와 **거래·정산 관리(R39)** 화면을 다룬다.
로그인은 [AUTH-FRONTEND.md](AUTH-FRONTEND.md), 회원 관리는 [MEMBER-FRONTEND.md](MEMBER-FRONTEND.md) 참고.

- Base URL: `http://localhost:8081` (로컬) / 배포는 실제 도메인
- 인증: **세션 쿠키**. `axios.defaults.withCredentials = true` 가 없으면 전부 401 이다
- 응답 형식은 다른 관리자 API와 같다. 성공은 `res.data.data`, 실패는 `errorCode` 로 분기

**두 화면 모두 조회 전용이다.** 프로젝트 상태나 정산 상태를 관리자가 바꾸는 API는 없다.
상태는 클라이언트의 행동과 매칭·계약·결제 흐름으로만 움직인다. 화면에 상태 변경 버튼을
만들지 말 것 — 만들면 서비스 백엔드의 진행 상황과 어긋난다.

---

## 0. 화면 하나에 API 세 개

두 화면 모두 구조가 같다. **상단 집계 · 목록 · 상세**를 따로 부른다.
집계를 목록에 끼워 주지 않는 이유는 회원 관리와 같다 — 페이지를 넘길 때마다 같은 집계를
다시 돌리게 된다. **집계는 검색 조건과 무관하므로 처음 한 번만** 부르면 된다.

| 화면 | 메서드 | 경로 |
|---|---|---|
| 프로젝트 상단 탭 카운트 | `GET` | `/api/v1/admin/projects/status-counts` |
| 프로젝트 목록 | `GET` | `/api/v1/admin/projects` |
| 프로젝트 상세 | `GET` | `/api/v1/admin/projects/{projectId}` |
| 정산 요약 카드 | `GET` | `/api/v1/admin/settlements/summary` |
| 정산 목록 | `GET` | `/api/v1/admin/settlements` |
| 정산 상세 | `GET` | `/api/v1/admin/settlements/{settlementId}` |

---

# A. 프로젝트 관리

## A-1. 상단 탭 카운트 — `GET /api/v1/admin/projects/status-counts`

피그마 상단의 `전체 (6) · 등록 완료 (1) · 모집중 (1) ...` 괄호 숫자다. 파라미터 없다.

```json
{
  "code": "PROJECT_STATUS_COUNTS_FOUND",
  "data": {
    "total": 6,
    "items": [
      { "status": "REGISTERED",         "label": "등록 완료", "count": 1 },
      { "status": "RECRUITING",         "label": "모집중",    "count": 1 },
      { "status": "NEGOTIATING",        "label": "협상중",    "count": 1 },
      { "status": "CONTRACT_PENDING",   "label": "계약 대기", "count": 1 },
      { "status": "IN_PROGRESS",        "label": "진행중",    "count": 1 },
      { "status": "COMPLETION_PENDING", "label": "완료 대기", "count": 0 },
      { "status": "CLOSED",             "label": "종료",      "count": 1 },
      { "status": "CANCELED",           "label": "취소됨",    "count": 0 }
    ]
  }
}
```

`items` 는 **화면 탭 순서 그대로** 온다. 배열을 그대로 돌려 탭을 그리면 되고,
`label` 을 쓰면 프론트에 한글 매핑 테이블을 둘 필요가 없다.
`status` 값을 목록 조회의 `status` 파라미터에 그대로 넣으면 된다("전체"는 파라미터 생략).

```jsx
<Tab active={!status} onClick={() => setStatus(null)}>전체 ({data.total})</Tab>
{data.items.map(t => (
  <Tab key={t.status} active={status === t.status} onClick={() => setStatus(t.status)}>
    {t.label} ({t.count})
  </Tab>
))}
```

> ⚠️ **검색어를 넣어도 이 숫자는 바뀌지 않는다.** 탭은 "지금 무엇이 몇 건인지"를 보여주는
> 고정 지표다. 키워드를 칠 때마다 숫자가 흔들리면 기준으로 쓸 수 없어서 일부러 전체 집계다.
> 검색 결과 건수가 필요하면 목록 응답의 `totalElements` 를 쓸 것.

`total` 은 취소·종료를 **포함한** 전체다. `CANCELED` 를 뺀 나머지 여덟의 합이 `total` 과
같아야 정상이다. 어긋나면 백엔드에 새 상태가 생겼다는 뜻이니 알려 달라.

## A-2. 목록 — `GET /api/v1/admin/projects`

### 쿼리 파라미터

| 이름 | 값 | 비고 |
|---|---|---|
| `status` | `REGISTERED` `RECRUITING` `NEGOTIATING` `CONTRACT_PENDING` `IN_PROGRESS` `COMPLETION_PENDING` `CLOSED` `CANCELED` | 탭 |
| `keyword` | 문자열 | **프로젝트명 · 클라이언트 회사명** 부분 일치 (대소문자 무시) |
| `fromDate` | `yyyy-MM-dd` | **등록일** 시작 (그날 포함) |
| `toDate` | `yyyy-MM-dd` | **등록일** 종료 (그날 포함) |
| `page` | 0부터 | 기본 0 |
| `size` | 숫자 | 기본 20 |

전부 선택이고 **비우면 전체**다. 회원 관리와 마찬가지로 **enum에 없는 값은 400** 이므로
값이 있는 것만 담아 보내는 편이 안전하다.

```js
const params = Object.fromEntries(
  Object.entries({ status, keyword, fromDate, toDate, page, size })
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
);
const { data } = await axios.get('/api/v1/admin/projects', { params });
```

> `toDate` 는 **그날을 포함**한다. `2026-08-06` 을 보내면 8월 6일 23:59 에 등록된 것도 잡힌다.
> 프론트에서 하루를 더해 보내지 말 것 — 서버가 이미 처리한다.

> ⚠️ **`sort` 파라미터는 무시된다.** 정렬은 최근 등록순 고정이다(회원 관리와 같은 이유).

> ⚠️ **`fromDate > toDate` 면 400** 이다(`ADMIN_PROJECT_002`). 조용히 0건을 주면 관리자가
> "그 기간에 프로젝트가 없다"로 오해하기 때문에 일부러 에러로 막았다. 날짜 피커에서
> 역순 선택을 막아 두면 이 에러를 볼 일이 없다.

### 응답

```json
{
  "code": "PROJECT_LIST_FOUND",
  "data": {
    "content": [
      {
        "projectId": 1,
        "projectNo": "PRJ-001",
        "title": "쇼핑몰 관리자 페이지 리뉴얼",
        "clientAccountId": 12,
        "clientName": "삼성전자",
        "status": "IN_PROGRESS",
        "statusCode": "IN_PROGRESS",
        "statusLabel": "진행중",
        "paymentStatus": "DEPOSIT_PAID",
        "paymentStatusLabel": "착수금 결제 완료",
        "jobCategory": "DEVELOPMENT",
        "jobCategoryLabel": "개발",
        "jobRole": "FRONTEND",
        "jobRoleLabel": "프론트엔드 개발자",
        "positionCount": 1,
        "contractSalaryAmount": 5000000,
        "budgetAmount": 15000000,
        "periodValue": 3,
        "periodUnit": "MONTH",
        "periodUnitLabel": "개월",
        "totalHeadcount": 1,
        "confirmedHeadcount": 1,
        "createdAt": "2026-07-25T00:00:00"
      }
    ],
    "page": 0, "size": 20, "totalElements": 6, "totalPages": 1,
    "first": true, "last": true
  }
}
```

### 표에 그릴 때 주의할 것

**번호** — `projectNo` 를 그대로 쓴다(`PRJ-001`). 서버가 만들어 준다.
**단, 서버로 보낼 때는 `projectId`(숫자)를 보내야 한다.** `projectNo` 는 DB에 저장된 값이
아니라 ID로 만든 표시용 표기라서 조회 키로 쓸 수 없다.

**계약 금액 — 🚨 여기가 제일 조심할 곳이다.**
금액 필드가 두 개고 **단위가 서로 다르다.**

| 필드 | 뜻 | 단위 |
|---|---|---|
| `contractSalaryAmount` | 체결된 계약의 **월 단가** | 월 |
| `budgetAmount` | 클라이언트가 등록 때 적은 **예산 총액** | 총액 |

계약 전(등록 완료·모집중·협상중) 프로젝트에는 계약이 없어서 `contractSalaryAmount` 가
`null` 이다. 그때 `budgetAmount` 로 대체하되 **"월"을 붙이면 안 된다.**

```js
// ✅ 이렇게
const amountText = row.contractSalaryAmount != null
  ? `월 ${row.contractSalaryAmount.toLocaleString()}원`
  : `예산 ${row.budgetAmount.toLocaleString()}원`;

// ❌ 이러면 6개월짜리 프로젝트가 6배 큰 "월 금액"으로 보인다
const wrong = `월 ${(row.contractSalaryAmount ?? row.budgetAmount).toLocaleString()}원`;
```

계약이 여러 건이면 **가장 먼저 체결된 계약**의 월 단가가 온다.

**직군·직무** — 피그마는 `개발 · 프론트엔드` 한 줄이지만 **프로젝트는 포지션을 여러 개
모집할 수 있다.** 목록에는 대표 1건(포지션 번호가 가장 작은 것)만 오고, 나머지는
`positionCount` 로만 알 수 있다.

```js
`${row.jobCategoryLabel} · ${row.jobRoleLabel}` + (row.positionCount > 1 ? ` 외 ${row.positionCount - 1}건` : '')
```

포지션이 아직 없는 프로젝트는 `jobCategory` `jobRole` 이 **`null`** 이다(등록 직후).
`positionCount` 는 그때 `0` 이다.

**라벨은 서버가 준다.** `statusLabel` `jobCategoryLabel` `jobRoleLabel` `periodUnitLabel`
`paymentStatusLabel` 을 그대로 쓸 것. 프론트에 매핑 테이블을 만들면 서버가 값을 추가할 때
화면만 영어로 남는다.

**`status` 와 `statusCode` 가 둘 다 있는 이유** — `status` 는 서버가 아는 값일 때만 채워지고
모르는 코드면 `null` 이 된다. `statusCode` 는 DB 원문이라 항상 값이 있다.
**배지 색 분기는 `status`, 최후의 표시는 `statusLabel`** 을 쓰면 된다.
(백엔드에 새 상태가 생겨도 화면이 죽지 않게 하려고 이렇게 나눠 뒀다. 나머지 코드 필드도 같은 규칙이다)

**삭제된 프로젝트는 목록에 안 나온다.** 다만 **등록 취소(`CANCELED`)는 나온다** —
"취소됨" 탭이 그것이다. 둘은 다른 것이니 뭉치지 말 것.

**`clientAccountId`** 로 회원 관리 상세(`/members/{accountId}`)로 이동하는 링크를 걸 수 있다.
`clientName` 은 **회사명**이다(담당자명이 아니다).

## A-3. 상세 — `GET /api/v1/admin/projects/{projectId}`

```json
{
  "code": "PROJECT_FOUND",
  "data": {
    "projectId": 1,
    "projectNo": "PRJ-001",
    "title": "쇼핑몰 관리자 페이지 리뉴얼",

    "clientAccountId": 12,
    "clientName": "삼성전자",
    "clientManagerName": "홍길동",
    "clientEmail": "hong@example.com",
    "clientPhone": "01011112222",

    "status": "IN_PROGRESS",
    "statusCode": "IN_PROGRESS",
    "statusLabel": "진행중",
    "paymentStatus": "DEPOSIT_PAID",
    "paymentStatusLabel": "착수금 결제 완료",

    "budgetAmount": 15000000,
    "periodValue": 3,
    "periodUnit": "MONTH",
    "periodUnitLabel": "개월",
    "workStyle": "ANY",
    "workStyleLabel": "혼합",
    "workForm": "FULL_TIME",
    "workFormLabel": "풀타임",
    "workLocation": null,
    "contractType": "프리랜서",

    "startDesiredDate": null,
    "startNegotiable": false,
    "totalHeadcount": 1,
    "confirmedHeadcount": 1,

    "currentSituation": null,
    "mainTask": null,
    "detailScope": null,
    "extraNote": null,

    "recruitStartedAt": null,
    "recruitDeadline": null,
    "extensionCount": 0,
    "noticeAgreedAt": null,
    "canceledAt": null,
    "closedAt": null,
    "createdAt": "2026-07-25T00:00:00",

    "positions": [
      {
        "positionId": 1,
        "positionNo": 1,
        "jobCategory": "DEVELOPMENT",
        "jobCategoryLabel": "개발",
        "jobRole": "FRONTEND",
        "jobRoleLabel": "프론트엔드 개발자",
        "minCareerYears": 3,
        "headcount": 1,
        "confirmedCount": 1,
        "status": "CONFIRMED",
        "statusLabel": "확정",
        "preferredNote": null,
        "skillCodes": ["REACT", "TYPESCRIPT"]
      }
    ],

    "contracts": [
      {
        "contractId": 1,
        "contractNo": "CT-2026-0001",
        "positionId": 1,
        "freelancerAccountId": 77,
        "freelancerName": "김프리",
        "freelancerEmail": "kim@example.com",
        "salaryAmount": 5000000,
        "totalAmount": 15000000,
        "startDate": "2026-08-01",
        "endDate": "2026-10-31",
        "status": "IN_PROGRESS",
        "statusCode": "IN_PROGRESS",
        "statusLabel": "진행중",
        "workStyleLabel": "혼합",
        "workFormLabel": "풀타임",
        "signedAt": "2026-07-30T00:00:00",
        "completedAt": null,
        "terminatedAt": null,
        "terminatedBy": null,
        "createdAt": "2026-07-30T00:00:00"
      }
    ]
  }
}
```

### 피그마 카드 ↔ 필드 대응

**프로젝트 기본 정보**

| 화면 | 필드 |
|---|---|
| 번호 | `projectNo` |
| 프로젝트명 | `title` |
| 클라이언트 | `clientName` (회사명) |
| 매칭 프리랜서 | `contracts[0].freelancerName` — 아래 주의 참고 |
| 상태 | `statusLabel` |
| 직군 / 직무 | `positions[0].jobCategoryLabel` / `positions[0].jobRoleLabel` |
| 예산 | `budgetAmount` — **총액이다** |
| 등록일 | `createdAt` |
| 근무방식 | `workStyleLabel` |
| 계약유형 | `contractType` — **고정값이다** |

**계약 정보**

| 화면 | 필드 |
|---|---|
| 계약금액 | `contracts[0].salaryAmount` (**월 단가**) |
| 계약시작 / 계약종료 | `contracts[0].startDate` / `endDate` |
| 계약상태 | `contracts[0].statusLabel` |

### 상세에서 주의할 것

> 🚨 **`positions` 와 `contracts` 는 배열이다.** 피그마는 각각 한 줄로 그리지만
> 실제로는 여러 건일 수 있다(인원을 여럿 모집하는 프로젝트). 화면이 `[0]` 만 그리더라도
> **`length > 1` 이면 "외 N건" 정도는 표시**해 주는 게 좋다. 서버가 하나로 줄여 버리면
> 관리자는 나머지가 있다는 사실조차 알 수 없어서 배열로 내린다.

> **`contracts` 는 빈 배열일 수 있다.** 계약 전 프로젝트가 그렇다. 이때 "계약 정보" 카드는
> 통째로 숨기거나 "계약 전"으로 표시한다. `contracts[0].xxx` 를 그냥 읽으면 터진다.
> `positions` 도 등록 직후에는 비어 있을 수 있다.

> **`contracts` 에는 서명 거부(`REJECTED`)된 계약도 들어 있다.** 목록의 대표 금액에서는
> 빠지지만 상세는 이력이라 남긴다. "왜 계약이 안 됐는지"를 보는 화면에서 거부 건이 사라지면
> 확인할 방법이 없기 때문이다. 대표 계약을 고를 때는 `REJECTED` 를 걸러 내는 편이 낫다.

> ⚠️ **계약 상태와 프로젝트 상태는 라벨 체계가 다르다.** 값이 같아도 뜻이 다르니 섞지 말 것.
> - 계약 `COMPLETION_PENDING` = **"정산 대기"**
> - 프로젝트 `COMPLETION_PENDING` = **"완료 대기"**
>
> 한 화면에 둘이 같이 나오므로 각자의 `statusLabel` 을 그대로 써야 한다.

**금액 세 가지를 구분할 것**

| 필드 | 뜻 |
|---|---|
| `budgetAmount` | 등록 시 예산 **총액** |
| `contracts[].salaryAmount` | 계약 **월 단가** |
| `contracts[].totalAmount` | 월 단가 × 계약 개월 수 = 총 계약금액 |

**`clientName` 과 `clientManagerName` 은 다른 값이다.** 앞은 회사명, 뒤는 담당자 개인 이름이다.
화면의 "클라이언트"는 발주한 회사를 가리키므로 `clientName` 이 맞다.

**`workStyleLabel` 의 "혼합"** 은 `workStyle = "ANY"` 다. 서비스 프론트에서도 같은 용어를 쓴다.

**`skillCodes` 는 코드 배열**이다(`["REACT", "TYPESCRIPT"]`). 여기만은 한글 라벨을 주지 않는다 —
기술명은 대부분 원문 그대로 쓰는 게 자연스러워서다. 칩으로 그대로 그리면 된다.
없으면 **빈 배열**이다(`null` 아님).

**없는 프로젝트 / 삭제된 프로젝트는 둘 다 404** 다. 목록·탭 카운트와 기준이 같아서
"목록에는 있는데 상세는 404" 가 나지 않는다.

---

# B. 거래·정산 관리

## B-1. 요약 카드 — `GET /api/v1/admin/settlements/summary`

파라미터 없다.

```json
{
  "code": "SETTLEMENT_STATS_FOUND",
  "data": {
    "totalRevenue": 125000000,
    "monthlyRevenue": 18000000,
    "revenueMonth": "2026-08",
    "pendingAmount": 9000000,
    "pendingCount": 4,
    "overdueAmount": 1500000,
    "overdueCount": 2,
    "failedAmount": 300000,
    "failedCount": 1,
    "penaltyPaidAmount": 0,
    "penaltyPaidCount": 0,
    "penaltyPendingAmount": 0,
    "penaltyPendingCount": 0
  }
}
```

R39 의 카드 6개 대응:

| 카드 | 필드 |
|---|---|
| 총 수수료 수익 | `totalRevenue` |
| 이번달 수익 | `monthlyRevenue` (+ 기준 달 `revenueMonth`) |
| 결제 예정 금액 | `pendingAmount` / `pendingCount` |
| 미납 | `overdueAmount` / `overdueCount` |
| 결제 실패 | `failedAmount` / `failedCount` |
| 위약금 수수료 | `penaltyPaidAmount` / `penaltyPaidCount` |

> **모든 금액은 "수수료" 기준이다.** 기준금액(`baseAmount`, 거래 규모)이 아니라 플랫폼이
> 실제로 받는 돈이다. 카드에 거래액을 섞지 말 것.

> ⚠️ **`penalty*` 는 현재 항상 0 이다.** 백엔드에 Penalty 도메인이 아직 없어서
> `penalty` 테이블에 쓰는 코드가 없다(서비스의 위약금 API도 고정 응답이다).
> 조회는 이미 붙어 있으니 **화면은 지금 만들어 두면 되고**, 백엔드가 저장을 시작하면
> 값이 저절로 채워진다.

> **취소된 정산(`CANCELED`)은 어느 카드에도 안 들어간다.** 낼 이유가 사라진 건이라
> 받을 돈도 못 받은 돈도 아니다. 그래서 카드 금액의 합이 전체 정산 금액과 맞지 않는다.
> 회원 관리 요약 카드와 마찬가지로 **검산하는 UI를 만들지 말 것.**

`revenueMonth` 는 서버(KST) 기준으로 정한 "이번 달"이다(`"2026-08"`). 카드 제목에
`{revenueMonth} 수익` 처럼 붙여 주면 자정 근처에 브라우저 시각과 어긋나 보이는 일이 없다.

## B-2. 목록 — `GET /api/v1/admin/settlements`

### 쿼리 파라미터

| 이름 | 값 | 비고 |
|---|---|---|
| `status` | `PENDING` `PAID` `OVERDUE` `FAILED` `CANCELED` | 상태 필터 |
| `phase` | `DEPOSIT` `SUCCESS_FEE` | 착수금 / 완료금(성공보수) 필터 |
| `payerRole` | `CLIENT` `FREELANCER` | 클라이언트 / 프리랜서 필터 |
| `keyword` | 문자열 | **정산번호 · 프로젝트명 · 회원명 · 회사명** 부분 일치 |
| `fromDate` | `yyyy-MM-dd` | **정산 생성일** 시작 (그날 포함) |
| `toDate` | `yyyy-MM-dd` | **정산 생성일** 종료 (그날 포함) |
| `page` / `size` | | 기본 0 / 20 |

> **R39 의 "결제 가능" 필터는 `PENDING`(결제 대기)이다.** DB에 "가능"이라는 상태는 없다.
> 아직 내지 않았고 기한도 지나지 않은 건이 그것이다.

> **R39 의 "완료금 수수료"는 `SUCCESS_FEE`(성공보수 수수료)다.** 같은 것이다.

> **기간은 정산 "생성일" 기준이다.** 납부 기한(`dueDate`)이나 완료일(`paidAt`)이 아니다.
> 완료일로 걸면 아직 안 낸 건이 통째로 빠져서 "이번 달 미납"이 언제나 0건이 된다.

`fromDate > toDate` 면 400(`ADMIN_SETTLEMENT_002`), `sort` 무시는 프로젝트와 같다.

### 응답

```json
{
  "code": "SETTLEMENT_LIST_FOUND",
  "data": {
    "content": [
      {
        "settlementId": 31,
        "settlementNo": "ST-2026-0031",
        "projectId": 1,
        "projectTitle": "쇼핑몰 관리자 페이지 리뉴얼",
        "payerAccountId": 12,
        "memberName": "삼성전자",
        "payerName": "홍길동",
        "companyName": "삼성전자",
        "payerRole": "CLIENT",
        "payerRoleLabel": "클라이언트",
        "phase": "DEPOSIT",
        "phaseLabel": "착수금 수수료",
        "baseAmount": 15000000,
        "feeRate": 3.00,
        "gradeDiscount": 1.00,
        "effectiveFeeRate": 2.00,
        "feeAmount": 300000,
        "status": "PAID",
        "statusCode": "PAID",
        "statusLabel": "결제 완료",
        "dueDate": "2026-08-05",
        "paidAt": "2026-08-04T00:00:00",
        "createdAt": "2026-08-01T00:00:00"
      }
    ],
    "page": 0, "size": 20, "totalElements": 2, "totalPages": 1,
    "first": true, "last": true
  }
}
```

R39 의 표 컬럼 대응:

| 화면 | 필드 |
|---|---|
| 정산번호 | `settlementNo` |
| 프로젝트 | `projectTitle` |
| 회원 | `memberName` |
| 유형 | `phaseLabel` |
| 기준금액 | `baseAmount` |
| 수수료율 | `effectiveFeeRate` — 아래 주의 참고 |
| 수수료 | `feeAmount` |
| 상태 | `statusLabel` |
| 기한 | `dueDate` |
| 완료일 | `paidAt` |
| 상세 | `settlementId` 로 이동 |

### 표에 그릴 때 주의할 것

**정산번호는 진짜 저장된 값이다.** 프로젝트 번호(`projectNo`)와 달리 채번된 식별자라
검색에도 쓸 수 있다.

**회원명 — `memberName` 을 쓰면 된다.**
역할마다 화면에 맞는 이름이 다른데(클라이언트는 회사명, 프리랜서는 개인 이름)
서버가 이미 골라서 준다. `payerName`(항상 개인 이름)과 `companyName`(클라이언트만)도
같이 주므로 툴팁 등에 필요하면 쓸 것. `companyName` 은 프리랜서면 `null` 이다.

**수수료율 — 🚨 세 개 중 `effectiveFeeRate` 를 그린다.**

| 필드 | 뜻 |
|---|---|
| `feeRate` | 기본 요율 (%) |
| `gradeDiscount` | 등급 할인 — **비율이 아니라 %p 차감**이다 |
| `effectiveFeeRate` | 실제 적용 요율 = `feeRate - gradeDiscount` |

`3.00` 에 `1.00` 할인이면 `2.00%` 다. **2.97% 가 아니다.** 다이아(클라이언트)·마스터(프리랜서)
등급만 1.00 이 붙고 나머지는 0.00 이다. 할인이 있을 때 `3.00% → 2.00%` 처럼 원가에
취소선을 긋고 싶다면 세 값이 다 있으니 그렇게 그려도 된다.

**`paidAt` 은 결제 완료 건만 값이 있다.** 대기·미납·실패는 `null` 이므로 `-` 로 그린다.

**`dueDate` 는 `null` 일 수 있다.** 기한이 정해지지 않은 정산이다.

**`baseAmount` 는 "이 수수료를 매긴 기준 금액"** 이다. 착수금 클라이언트 건은 프로젝트 예산,
프리랜서 건과 성공보수는 계약 총액이다. 화면에는 그대로 그리면 된다.

**미납·실패 행은 눈에 띄게.** 관리자가 이 화면을 여는 대부분의 이유가 그것이다.
`status` 로 분기하면 된다(`OVERDUE` `FAILED`).

## B-3. 상세 — `GET /api/v1/admin/settlements/{settlementId}`

목록 필드에 **"왜 이 상태인지"** 를 설명하는 값이 더 붙는다.

```json
{
  "code": "SETTLEMENT_FOUND",
  "data": {
    "settlementId": 31,
    "settlementNo": "ST-2026-0031",

    "projectId": 1,
    "projectNo": "PRJ-001",
    "projectTitle": "쇼핑몰 관리자 페이지 리뉴얼",
    "projectStatusLabel": "진행중",
    "contractId": 3,
    "contractNo": "CT-2026-0003",

    "payerAccountId": 12,
    "memberName": "삼성전자",
    "payerName": "홍길동",
    "payerEmail": "hong@example.com",
    "payerPhone": "01011112222",
    "companyName": "삼성전자",
    "payerRole": "CLIENT",
    "payerRoleLabel": "클라이언트",

    "phase": "DEPOSIT",
    "phaseLabel": "착수금 수수료",
    "baseAmount": 15000000,
    "feeRate": 3.00,
    "gradeDiscount": 1.00,
    "effectiveFeeRate": 2.00,
    "feeAmount": 300000,

    "status": "FAILED",
    "statusCode": "FAILED",
    "statusLabel": "결제 실패",
    "dueDate": "2026-08-05",
    "paidAt": null,
    "approvalNo": null,
    "failReason": "한도 초과",
    "overdueReason": null,
    "paymentMethodId": 8,
    "createdAt": "2026-08-01T00:00:00"
  }
}
```

| 필드 | 언제 값이 있나 |
|---|---|
| `approvalNo` | 결제 완료(`PAID`) 건 |
| `failReason` | 결제 실패(`FAILED`) 건 |
| `overdueReason` | 미납(`OVERDUE`) 건 |

**`contractId` 는 `null` 일 수 있다.** 착수금 정산 중 **클라이언트 건은 프로젝트 등록 시점에
생기고 그때는 아직 계약이 없다.** `contractNo` 도 같이 `null` 이니 "계약 전"으로 표시한다.

**`paymentMethodId` 는 ID만 준다.** 카드사·마스킹 번호는 회원 소유의 정보라, 정산 확인에
필요하지 않은 값을 관리자 화면으로 끌어오지 않았다. 필요해지면 말해 달라.

`projectId` → 프로젝트 상세, `payerAccountId` → 회원 상세로 링크를 걸 수 있다.

---

## C. 에러 코드

| HTTP | `errorCode` | 언제 | 화면에서 |
|---|---|---|---|
| 400 | `ADMIN_PROJECT_002` | 프로젝트 조회 `fromDate > toDate` | `message` 그대로 노출 |
| 400 | `ADMIN_SETTLEMENT_002` | 정산 조회 `fromDate > toDate` | `message` 그대로 노출 |
| 400 | `ADMIN_GLOBAL_002` | enum에 없는 필터값 (`status=RUNNING` 같은 오타) | 파라미터를 점검 |
| 401 | `ADMIN_GLOBAL_006` | 세션 만료 | 로그인 페이지로 |
| 403 | `ADMIN_GLOBAL_009` | CSRF 토큰 불일치 | 새로고침 안내 |
| 404 | `ADMIN_PROJECT_001` | 없거나 삭제된 프로젝트 | 목록으로 되돌린다 |
| 404 | `ADMIN_SETTLEMENT_001` | 없는 정산 | 목록으로 되돌린다 |

조회 API뿐이라 CSRF 토큰이 필요 없다(`GET` 은 CSRF 검사 대상이 아니다).
401 처리는 회원 관리와 동일하게 공통 인터셉터에서 하면 된다.

---

## D. DB에 없어서 서버가 만들어 주는 값 (중요)

피그마에 있지만 **DB에 원본이 없는** 값들이다. 화면을 만들 때 전제가 달라지므로 정리해 둔다.

| 값 | 실제 | 프론트에서 |
|---|---|---|
| `projectNo` (`PRJ-001`) | **저장된 값 아님.** ID로 만든 표기 | 표시용으로만. 서버로는 `projectId` 를 보낼 것 |
| `contractType` (`"프리랜서"`) | **DB에 계약 종류 컬럼 자체가 없음.** 플랫폼 계약이 전부 프리랜서 도급이라 고정값 | 그대로 그리면 된다. 필터를 만들지 말 것 |
| 예산 `월 X원` | `budget_amount` 는 **총액**이고 월 단가가 아님 | 계약 전이면 "예산 총액"으로 표기 (A-2 참고) |
| 직군·직무 한 줄 | 포지션은 **N개** | `positionCount` / `positions[]` (A-2, A-3 참고) |
| 매칭 프리랜서 한 명 | 계약은 **N건** | `contracts[]` (A-3 참고) |
| 위약금 수수료 카드 | `penalty` 테이블에 쓰는 코드가 **백엔드에 아직 없음** | 지금은 항상 0. 화면은 만들어 둘 것 (B-1 참고) |

혼동하기 쉬운 세 값도 정리해 둔다. **서로 다른 것이다.**

| 필드 | 값 |
|---|---|
| `contractType` (계약유형) | `프리랜서` — 고정 |
| `workForm` (근무 형태) | `FULL_TIME` 풀타임 / `PART_TIME` 파트타임 / `ANY` 모두 가능 |
| `workStyle` (근무 방식) | `REMOTE` 재택 / `ONSITE` 상주 / `ANY` **혼합** |

---

## E. 알아 두면 좋은 것

**목록의 집계가 가볍지 않다.** 프로젝트 목록은 행마다 대표 포지션·대표 계약을 서브쿼리로
집는다. `size` 를 100 이상으로 크게 잡으면 느려질 수 있으니 기본 20을 권한다.

**코드 필드는 전부 `xxx` / `xxxCode` / `xxxLabel` 3종 세트다.**
- `xxx` — 서버가 아는 값일 때만. 모르면 `null`. **분기 로직에 쓴다**
- `xxxCode` — DB 원문. 항상 값이 있다. 디버깅·문의 대응에 쓴다
- `xxxLabel` — 화면 문구. **표시에 쓴다**

백엔드에 새 상태가 추가돼도 화면이 죽지 않게 하려고 이렇게 나눴다. 화면에 코드가 그대로
(`SPRING_BOOT` 처럼) 보이기 시작하면 서버에 라벨을 추가하라는 신호이니 알려 달라.

**날짜 타입이 두 가지다.** `LocalDate`(`"2026-08-01"`)와 `LocalDateTime`(`"2026-08-01T00:00:00"`).
`dueDate` `startDate` `endDate` `startDesiredDate` 는 날짜만, 나머지 `*At` 은 시각까지다.
타임존 표기가 없으므로 `new Date(...)` 로 파싱하면 브라우저 로컬로 해석된다. 서버는 KST 기준이다.

**정렬·상태 변경이 필요하면 말해 달라.** 컬럼 헤더 클릭 정렬은 서버에 허용 목록을 만들어야
하고(네이티브 쿼리에 정렬을 이어 붙이면 SQL 주입 경로가 된다), 정산 결제 처리 같은 변경은
백엔드에 유스케이스를 만들고 관리자 서버가 호출하는 형태가 맞다.
관리자 서버가 정산 상태를 직접 바꾸면 실제 입금과 장부가 어긋난다.
