# 1:1 문의 · 사이트 리뷰 관리 연동 가이드 (프론트엔드용)

`Pairing-admin` 서버의 **1:1 문의 관리**와 **사이트 리뷰 관리** API 를 다룬다.
로그인·세션은 `AUTH-FRONTEND.md` 를 먼저 볼 것.

- Base URL: `http://localhost:8081` (로컬) / 배포는 실제 도메인
- 모든 API는 **로그인 필수**. 세션 쿠키가 없으면 `401 ADMIN_GLOBAL_006`
- **상태를 바꾸는 요청(`POST` / `PUT`)은 CSRF 토큰이 필요하다.** axios 쓰면 자동 처리된다

---

## 0. 한눈에 보기

| 화면 | Method | Path |
|---|---|---|
| 문의 요약 카드 | `GET` | `/api/v1/admin/inquiries/summary` |
| 문의 목록 | `GET` | `/api/v1/admin/inquiries` |
| 문의 상세 | `GET` | `/api/v1/admin/inquiries/{inquiryId}` |
| 문의 답변 | `POST` | `/api/v1/admin/inquiries/{inquiryId}/answer` |
| 리뷰 요약 + 별점 분포 | `GET` | `/api/v1/admin/site-reviews/summary` |
| 리뷰 목록 | `GET` | `/api/v1/admin/site-reviews` |
| 공개·홍보 설정 | `PUT` | `/api/v1/admin/site-reviews/{siteReviewId}/visibility` |

**응답 껍데기는 전부 같다.**

```json
{ "code": "...", "message": "...", "data": { } }
```

에러는 `errorCode` 가 붙는다.

```json
{ "timestamp": "...", "status": 404, "errorCode": "ADMIN_INQUIRY_001",
  "message": "문의를 찾을 수 없습니다.", "traceId": "b11908d2" }
```

---

# 1. 1:1 문의 관리

## 1-1. 요약 카드

```
GET /api/v1/admin/inquiries/summary
```

```json
{
  "code": "INQUIRY_SUMMARY_FOUND",
  "data": { "totalCount": 4, "pendingCount": 2, "answeredCount": 2, "todayCount": 1 }
}
```

목록 상단 카드 4개다. **필터와 무관한 전체 기준**이라 검색·필터를 걸어도 값이 바뀌지 않는다.
`todayCount` 는 자정 이후 접수된 건수다.

## 1-2. 목록

```
GET /api/v1/admin/inquiries?keyword=착수금&writerRole=CLIENT&status=PENDING&page=0&size=20
```

| 파라미터 | 기본값 | 값 |
|---|---|---|
| `keyword` | — | **회원명 · 제목 · 문의번호를 한 번에** 검색 |
| `writerRole` | 전체 | `CLIENT` / `FREELANCER` / `ADMIN` |
| `status` | 전체 | `PENDING`(대기중) / `ANSWERED`(답변완료) |
| `page` / `size` | 0 / 20 | |
| `sort` | `createdAt,desc` | |

검색창이 하나라서 `keyword` 하나로 세 가지를 찾는다. **문의번호는 `QNA-20260805-0012` 전체를
붙여넣어도 되고 뒤 네 자리만 넣어도 걸린다.**

```json
{
  "code": "INQUIRIES_FOUND",
  "data": {
    "content": [{
      "inquiryId": 12,
      "inquiryNo": "QNA-20260805-0012",
      "writerRole": "CLIENT",
      "title": "착수금 수수료 결제 문의",
      "writerName": "오이랩",
      "createdAt": "2026-08-05T14:20:00",
      "status": "ANSWERED",
      "answeredAt": "2026-08-06T10:05:00"
    }],
    "page": 0, "size": 20, "totalElements": 4, "totalPages": 1,
    "first": true, "last": true
  }
}
```

- **`inquiryNo` 는 화면 표시용**이다. 상세 조회에는 `inquiryId` 를 쓴다
- `answeredAt` 은 미답변이면 `null`
- 목록에는 본문·답변 내용이 없다. 상세에서 받는다

## 1-3. 상세

```
GET /api/v1/admin/inquiries/12
```

```json
{
  "code": "INQUIRY_FOUND",
  "data": {
    "inquiryId": 12,
    "inquiryNo": "QNA-20260805-0012",
    "writerAccountId": 301,
    "writerName": "오이랩",
    "writerRole": "CLIENT",
    "writerEmail": "contact@oilab.kr",
    "title": "착수금 수수료 결제 문의",
    "content": "착수금 수수료 결제 버튼이 활성화되지 않습니다.",
    "files": [{
      "fileId": 42,
      "originalName": "오류화면.png",
      "url": "https://.../dev/inquiry_attachment/3f1c....png"
    }],
    "status": "ANSWERED",
    "answer": "확인 후 안내드립니다.",
    "answererName": "페어링 고객지원",
    "answeredAt": "2026-08-06T10:05:00",
    "createdAt": "2026-08-05T14:20:00"
  }
}
```

- `files` 는 **없으면 빈 배열**이다. `url` 은 바로 열 수 있는 절대 경로다
- **첨부파일이 나중에 삭제됐으면 그 항목만 빠진다.** 상세 조회가 막히지는 않는다
- `answererName` 은 미답변이면 `null`. 개별 관리자 이름이 아니라 `"페어링 고객지원"` 고정이다
- 없는 문의면 `404 ADMIN_INQUIRY_001`

> **와이어프레임의 "문의분야"(카테고리)는 응답에 없다.** 스키마에서 제거된 항목이라
> 서버가 내려줄 값이 없다. 화면에서 빼면 된다.

## 1-4. 답변 등록

```
POST /api/v1/admin/inquiries/12/answer
Content-Type: application/json

{ "answer": "안녕하세요. 착수금 수수료는 프로젝트 등록 후 AI 검수 결과 확인 후 결제할 수 있습니다." }
```

응답은 **1-3 상세와 같은 모양**이다. 상태가 `ANSWERED` 로 바뀌고 `answeredAt` 이 채워진다.

- 답변은 **2,000자 이하**, 공백만 있으면 `400`
- **이미 답변한 문의에 다시 호출하면 답변이 교체된다.** 수정 API 가 따로 없다.
  이때 `answeredAt` 도 갱신되고 **사용자에게 알림이 다시 나간다**
- 답변 수정 이력은 남지 않는다

| 응답 | 의미 |
|---|---|
| `400 ADMIN_INQUIRY_002` | 답변 내용이 비어 있음 |
| `404 ADMIN_INQUIRY_001` | 없는 문의 |

### 사용자 알림에 대해

답변을 등록하면 `notification` 테이블에 행이 들어가 사용자 알림 목록에 뜬다.
다만 **실시간 푸시(토스트)는 나가지 않는다.** WebSocket 세션을 들고 있는 쪽이 백엔드 서버라
사용자가 새로고침하거나 알림을 다시 조회할 때 보인다.

**알림 발송이 실패해도 답변 등록은 성공한다.** 응답이 `200` 이면 답변은 저장된 것이다.

---

# 2. 사이트 리뷰 관리

## 2-1. 요약 + 별점 분포

```
GET /api/v1/admin/site-reviews/summary
```

```json
{
  "code": "SITE_REVIEW_SUMMARY_FOUND",
  "data": {
    "ratingAverage": 4.8,
    "totalCount": 4,
    "thisMonthCount": 1,
    "promotedCount": 2,
    "notPromotedCount": 2,
    "publicCount": 3,
    "scoreDistribution": { "5": 2, "4": 1, "3": 1, "2": 0, "1": 0 }
  }
}
```

카드 6개 + 그래프에 그대로 쓰면 된다. **필터와 무관한 전체 기준**이다.

- `ratingAverage` 는 후기가 없으면 `0`
- **`scoreDistribution` 은 5~1 을 항상 다 내려준다.** 0건인 별점도 `0` 으로 오므로
  그래프에서 빈 막대를 그리면 된다. 키가 없을까 봐 방어할 필요 없다

## 2-2. 목록

```
GET /api/v1/admin/site-reviews?keyword=삼성&score=5&writerRole=CLIENT&visibility=PUBLIC&promoted=true&page=0&size=20
```

| 파라미터 | 기본값 | 값 |
|---|---|---|
| `keyword` | — | **회원명 · 후기 내용 · 프로젝트명을 한 번에** 검색 |
| `score` | 전체 | `1` ~ `5` |
| `writerRole` | 전체 | `CLIENT` / `FREELANCER` |
| `visibility` | 전체 | `PUBLIC`(공개) / `PRIVATE`(비공개) |
| `promoted` | 전체 | `true`(홍보 활용) / `false`(홍보 제외) |
| `page` / `size` | 0 / 20 | |
| `sort` | `createdAt,desc` | |

**`promoted` 를 비우면 홍보/미홍보를 모두 준다.** `false` 를 넣어야 "홍보 제외"만 걸린다.

```json
{
  "code": "SITE_REVIEWS_FOUND",
  "data": {
    "content": [{
      "siteReviewId": 1,
      "siteReviewNo": "REV-001",
      "writerRole": "CLIENT",
      "writerName": "삼성전자",
      "score": 5,
      "content": "매칭 속도가 빠르고 AI 협상 기능이 정말 유용했습니다.",
      "projectTitle": "쇼핑몰 관리자 페이지",
      "visibility": "PUBLIC",
      "promoted": true,
      "createdAt": "2026-08-01T09:30:00"
    }],
    "page": 0, "size": 20, "totalElements": 4, "totalPages": 1,
    "first": true, "last": true
  }
}
```

- **`writerName` 은 마스킹하지 않는다.** 클라이언트는 회사명, 프리랜서는 이름이다.
  (비로그인 메인에 나가는 후기는 마스킹되지만 관리자 화면은 실명으로 본다)
- 계정이 지워졌으면 `"클라이언트"` / `"프리랜서"` 로 대체된다
- `projectTitle` 은 프로젝트가 삭제됐으면 `null`
- `siteReviewNo` 는 화면 표시용, 설정 변경에는 `siteReviewId` 를 쓴다

## 2-3. 공개 · 홍보 설정

```
PUT /api/v1/admin/site-reviews/1/visibility
Content-Type: application/json

{ "visibility": "PUBLIC", "promoted": true }
```

응답은 **2-2 목록 행과 같은 모양**이다. 바뀐 값으로 그 행만 갈아끼우면 된다.

**두 값을 항상 함께 보낸다.** 화면 버튼은 "공개로 변경"과 "홍보 활용"이 따로지만,
한쪽만 보내면 "비공개인데 홍보 활용" 같은 조합이 생긴다.
**버튼 하나를 누를 때 나머지는 현재 값을 그대로 실어 보낼 것.**

| 응답 | 의미 |
|---|---|
| `400 ADMIN_REVIEW_002` | **비공개 + 홍보 활용** 조합 (아래 참고) |
| `404 ADMIN_REVIEW_001` | 없는 리뷰 |

### 비공개 + 홍보 활용은 막는다

`{ "visibility": "PRIVATE", "promoted": true }` 는 `400` 이다.
메인에 나갈 수 없는 후기를 홍보로 골라두면, 나중에 공개로 바꾸는 순간 검수 없이 홍보에 실린다.

**비공개로 내릴 때는 `promoted` 도 `false` 로 같이 보내면 된다.**

```json
{ "visibility": "PRIVATE", "promoted": false }
```

---

## 3. 공개 여부와 홍보 활용의 차이

둘을 헷갈리기 쉬운데 역할이 다르다.

| | 뜻 | 누가 정하나 |
|---|---|---|
| `visibility` | **검수 통과 여부** — 노출해도 되는 후기인가 | 작성 시 **기본 `PUBLIC`**. 관리자가 부적절한 것만 내림 |
| `promoted` | **홍보 선별** — 메인에 걸 것인가 | 관리자가 직접 켠다 (기본 `false`) |

**메인 노출 조건은 셋 다 만족해야 한다.**

```
visibility = PUBLIC   AND   promoted = true   AND   score >= 4
```

3점 이하는 공개+홍보로 켜도 메인에 안 나온다. 서버 고정값이다.

> 후기는 작성되는 순간 **공개** 상태다. 관리자가 손대지 않아도 비공개로 숨겨지지 않는다.
> 사후 관리 방식이다 — 부적절한 내용이 보이면 그때 내린다.

---

## 4. 화면 구현 흐름

```
1:1 문의 관리
 ├── GET /admin/inquiries/summary      상단 카드 4개
 ├── GET /admin/inquiries?...          목록 (검색·필터·페이징)
 ├── [상세보기] → GET /admin/inquiries/{id}
 └── [답변 등록] → POST /admin/inquiries/{id}/answer
                   → 응답으로 상세 갱신 + 목록의 상태 뱃지 갱신

사이트 리뷰 관리
 ├── GET /admin/site-reviews/summary   카드 6개 + 별점 분포 그래프
 ├── GET /admin/site-reviews?...       목록 (검색·필터·페이징)
 └── [공개로 변경] / [홍보 활용] → PUT /admin/site-reviews/{id}/visibility
                                   → 응답으로 그 행만 갱신
```

**설정을 바꾸면 요약 카드 값도 달라진다.** `PUT` 성공 후 `summary` 를 다시 불러야
"공개 3 → 4" 처럼 카드가 따라 움직인다.

---

## 5. 공통 에러

| 응답 | 의미 |
|---|---|
| `401 ADMIN_GLOBAL_006` | 로그인 안 됨 / 세션 만료 → 로그인 화면으로 |
| `403 ADMIN_GLOBAL_009` | CSRF 토큰 없음·불일치 → 새로고침 후 재시도 |
| `400 ADMIN_GLOBAL_002` | 요청 값 형식 오류 (enum 오타, 필수 누락 등) |
| `500 ADMIN_GLOBAL_001` | 서버 오류 — **`traceId` 와 함께 알려줄 것** |

`4xx` 는 요청을 고치면 풀린다. **`5xx` 는 프론트가 할 수 있는 게 없으니 재시도 안내만 띄우고 공유해줄 것.**

---

## 6. 알아둘 것

**문의는 이 서버에서 만들 수 없다.** 접수는 사용자가 백엔드 서버로 한다.
관리자 서버에는 문의 생성 API 가 없다. 후기도 마찬가지로 작성·삭제 API 가 없다 —
공개·홍보만 바꾼다.

**백엔드와 같은 DB 를 쓴다.** 여기서 답변하면 사용자 화면에 즉시 반영되고,
공개·홍보를 켜면 비로그인 메인 노출에 바로 반영된다.

---

문의는 편하게 주세요.
