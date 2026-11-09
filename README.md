# OnAir: 실시간 방송 플랫폼

PC 화면 공유와 휴대폰 카메라로 라이브 방송을 송출하고, 다른 기기에서 로그인 없이 시청할 수 있는 웹 서비스입니다.
채팅(댓글), 토스페이먼츠 테스트 결제 후원, 녹화와 다시보기를 함께 제공합니다.

| 항목 | 내용 |
|---|---|
| **서비스 주소** | **https://52-62-28-94.sslip.io** (AWS EC2 시드니 리전, t3.small) |
| **테스트 계정** | 로그인 화면의 **「데모 방송자」/「데모 시청자」 버튼**을 누르면 비밀번호 없이 바로 로그인됩니다 (아래 [테스트 계정](#테스트-계정) 참고) |
| 기술 스택 | Spring Boot 4 (Java 25), Spring Security JWT, JPA + Flyway, MariaDB · Vue 3 + Vite · LiveKit (WebRTC SFU) · Caddy (HTTPS) · Docker Compose |

---

## 목차
1. [기능 확인 절차](#기능-확인-절차)
2. [지원 환경](#지원-환경)
3. [테스트 계정](#테스트-계정)
4. [실행 방법](#실행-방법)
5. [아키텍처](#아키텍처)
6. [인증과 인가](#인증과-인가)
7. [API](#api)
8. [선택 기능 구현 방식](#선택-기능-구현-방식)
9. [테스트](#테스트)
10. [출처](#출처)

---

## 기능 확인 절차

> 준비물: PC 1대(Chrome 또는 Edge), 휴대폰 1대. 송출 기기와 시청 기기를 다르게 해서 확인합니다.

### 1. PC 라이브 방송 (화면 공유 + 탭/시스템 소리 + 마이크)
1. PC Chrome에서 서비스에 접속해 **로그인 → 「데모 방송자」** 를 누릅니다.
2. 오른쪽 위 **「방송 만들기」** 에서 제목과 카테고리를 입력하고 **「만들고 스튜디오로 이동」** 을 누릅니다.
3. 스튜디오에서 **「🖥️ PC 화면 공유」** 를 고르고 **「미리보기 켜기」** 를 누릅니다.
   - 브라우저 공유 창에서 **Chrome 탭**을 고르고 **「탭 오디오도 공유」** 를 켭니다. Windows에서 **전체 화면**을 고르면 **「시스템 오디오도 공유」** 를 켤 수 있습니다.
   - 마이크 권한을 허용합니다.
   - 미리보기 아래 **마이크 / 탭·시스템 소리 레벨 미터**가 움직이면 소리가 정상적으로 들어오고 있는 것입니다.
4. **「● 방송 시작」** → 상태가 `LIVE`로 바뀝니다. 서버가 송출 권한 토큰을 발급하고, WebRTC로 LiveKit에 연결됩니다.
5. **다른 기기(휴대폰 또는 다른 브라우저)** 에서 서비스에 접속합니다. 로그인하지 않아도 됩니다. 목록의 `LIVE` 방송을 누르면 화면과 소리가 실시간으로 재생됩니다. 소리가 안 나면 **「🔊 소리 켜기」** 를 누르세요(브라우저 자동재생 정책).
6. 스튜디오에서 **「■ 방송 종료」** 를 누르면 서버가 방송을 `ENDED`로 바꾸고 LiveKit 방을 삭제합니다. 송출이 끊기고, 시청 화면에는 「방송이 종료되었습니다」가 표시됩니다.
   - 브라우저 상단의 **「공유 중지」** 를 눌러도 방송이 종료됩니다.

### 2. 모바일 라이브 방송 (카메라 + 마이크)
1. 휴대폰(Android Chrome / iPhone Safari)으로 접속해 **「데모 방송자」** 로 로그인한 뒤 방송을 만듭니다.
2. 스튜디오에서 **「📱 카메라」** 를 고르고 전면/후면을 선택한 뒤 **「미리보기 켜기」** 를 누르고, 카메라와 마이크 권한을 허용합니다.
3. **「● 방송 시작」** 후 **PC에서** 해당 방송을 열어 영상과 음성이 실시간으로 나오는지 확인합니다.
4. **「■ 방송 종료」** 로 종료하면 PC 시청 화면이 종료 상태로 바뀝니다.

### 3. 방송 관리 및 시청
- **목록**: 홈에서 `전체 / 방송 중 / 종료` 탭으로 거를 수 있습니다. 방송 중인 방송이 먼저 표시됩니다.
- **상세 / 시청**: 방송을 누르면 플레이어, 방송 정보, 채팅, 후원 패널이 나옵니다.
- **수정 / 삭제**: 방송 소유자로 로그인하면 상세 화면에 **「정보 수정」, 「삭제」** 버튼이 보입니다.
- **영구 저장**: 방송 정보는 MariaDB에 저장되므로 `docker compose restart backend` 후에도 그대로 조회됩니다.

### 4. 권한 확인 (비로그인 / 소유자 / 다른 사용자)
| 동작 | 비로그인 | 다른 사용자(「데모 시청자」) | 소유자(「데모 방송자」) |
|---|---|---|---|
| 방송 목록, 상세, 시청 | ✅ | ✅ | ✅ |
| 방송 등록 | ❌ 401 (로그인 화면으로 이동) | ✅ | ✅ |
| 방송 수정, 삭제 | ❌ 401 | ❌ 403 | ✅ |
| 송출 시작, 종료 | ❌ 401 | ❌ 403 | ✅ |
| 댓글 작성 | ❌ 401 | ✅ | ✅ |
| 댓글 수정, 삭제 | ❌ 401 | 본인 댓글만 ✅ / 남의 댓글 403 | 본인 댓글만 ✅ |
| 후원 | ❌ 401 | ✅ | ❌ (자기 방송에는 후원 불가) |

화면에서는 권한이 없는 버튼을 숨기지만, **실제 거절은 서버가 합니다.** curl로 바로 확인할 수 있습니다.
```bash
BASE=https://52-62-28-94.sslip.io/api
VIEWER=$(curl -s -X POST $BASE/auth/demo-login/1 | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -s -o /dev/null -w "%{http_code}\n" -X POST $BASE/broadcasts -H 'Content-Type: application/json' -d '{"title":"x"}'   # 401
curl -s -o /dev/null -w "%{http_code}\n" -X PATCH $BASE/broadcasts/1 -H "Authorization: Bearer $VIEWER" -H 'Content-Type: application/json' -d '{"title":"x"}'   # 403 (방송 1의 소유자가 아니면)
curl -s -o /dev/null -w "%{http_code}\n" -X POST $BASE/broadcasts/1/start -H "Authorization: Bearer $VIEWER" -H 'Content-Type: application/json' -d '{"sourceType":"SCREEN"}'   # 403
```
또한 시청자 토큰에는 `canPublish:false`가 들어 있어서, 시청자가 영상을 올리려 해도 **LiveKit 서버가 거부**합니다.

### 5. 선택 기능
- **댓글**: 방송 상세 오른쪽 채팅 패널에서 작성합니다. 본인 댓글에만 「수정 / 삭제」가 보입니다. 3초마다 새로고침됩니다.
- **도네이션**: 「데모 시청자」로 로그인해 방송 상세의 후원 패널에서 금액을 고르고 **「테스트 결제로 후원하기」** 를 누릅니다. 토스 테스트 결제창이 뜨면 아무 카드나 선택해 진행합니다(실제 결제 없음). 결제가 끝나면 누적 금액과 후원 내역이 표시됩니다.
- **녹화 / 다시보기**: 스튜디오의 **「방송을 녹화해 다시보기로 저장」** 이 켜진 상태(기본값)로 방송하고 종료하면, 상단 **「다시보기」** 메뉴에 영상이 생깁니다. 상세 화면에서 재생하고 구간을 탐색할 수 있습니다.

---

## 지원 환경

| 용도 | 지원 환경 | 비고 |
|---|---|---|
| PC 송출 (화면 + 소리 + 마이크) | **Chrome / Edge 최신 (Windows, macOS)** | 탭 소리: 모든 OS에서 Chrome 탭 공유 시 지원. 시스템 소리: Windows에서 「전체 화면」 공유 시 지원. Firefox와 Safari는 화면 공유 소리 캡처를 지원하지 않아 화면 + 마이크만 송출됩니다 |
| 모바일 송출 (카메라 + 마이크) | **Android Chrome, iOS Safari 15+** | HTTPS 필수 (서비스 주소는 HTTPS) |
| 시청 | Chrome, Edge, Safari, Firefox, 모바일 브라우저 | 로그인 불필요 |
| 녹화 | Chrome, Edge (WebM), Safari (MP4) | 송출자 브라우저의 MediaRecorder 사용 |

- 카메라, 마이크, 화면 공유 API는 **HTTPS(또는 localhost)에서만** 동작합니다.
- 회사나 학교 네트워크처럼 UDP가 막힌 환경에서는 TCP 7881로 자동 우회합니다.

---

## 테스트 계정

| 계정 | 용도 | 로그인 방법 |
|---|---|---|
| `streamer1` (데모 방송자) | 방송 등록, 송출, 수정, 삭제 | 로그인 화면 **「데모 방송자 (streamer1)」** 버튼 |
| `viewer1` (데모 시청자) | 다른 사용자 권한 확인, 댓글, 후원 | 로그인 화면 **「데모 시청자 (viewer1)」** 버튼 |

- 데모 계정은 서버 시작 시 자동으로 생성되고, 버튼 하나로 바로 로그인됩니다(`POST /api/auth/demo-login/{0|1}`).
- 데모 계정의 비밀번호 로그인이 필요하면 서버 환경변수 `DEMO_PASSWORD`로 정합니다. 설정하지 않으면 임의 값이 쓰여서 버튼으로만 로그인할 수 있습니다.
- 회원가입으로 새 계정을 만들어도 됩니다.

---

## 실행 방법

### A. 서버 배포 (Docker Compose, 권장)
요구사항: Linux 서버(2 vCPU / 2GB 이상), Docker, 도메인 1개(서버 IP를 가리키는 A 레코드)

열어야 할 포트: `80/tcp`, `443/tcp`, `443/udp`, `7881/tcp`, `7882/udp`

```bash
git clone https://github.com/Dawit-lee/onair-live-streaming.git && cd onair-live-streaming
cp .env.example .env
```
```bash
vi .env    # DOMAIN, 비밀번호, 시크릿 값 채우기
```
```bash
docker compose up -d --build
```
- Caddy가 Let's Encrypt 인증서를 자동으로 발급합니다. `https://<DOMAIN>` 으로 접속합니다.
- `/api/*`는 백엔드로, `/rtc*`(LiveKit 시그널링)는 LiveKit으로 프록시됩니다.

### 환경변수 (`.env.example`)
| 변수 | 설명 | 예시 |
|---|---|---|
| `DOMAIN` | 서비스 도메인 | `onair.example.com` |
| `DB_PASSWORD`, `DB_ROOT_PASSWORD` | MariaDB 비밀번호 | 임의 문자열 |
| `JWT_SECRET` | 로그인 토큰 서명 키 (32바이트 이상) | `openssl rand -base64 48` |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | LiveKit 키 / 시크릿 (시크릿 32바이트 이상) | `APIonair` / `openssl rand -base64 48` |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | 토스페이먼츠 **테스트** 키 (`test_ck_…` / `test_sk_…`, API 개별 연동 키). 비워 두면 후원만 비활성화 | 개발자센터에서 발급 |
| `DEMO_ENABLED` | 데모 로그인 버튼 노출 | `true` |
| `LIVEKIT_USE_EXTERNAL_IP` / `LIVEKIT_NODE_IP` | 미디어 서버 IP 광고 방식 (로컬 실행용) | 기본값 `true` / 빈 값 |

> 토스 웹훅(선택): 토스 개발자센터 → 웹훅 → `https://<DOMAIN>/api/donations/webhook` 등록 (`PAYMENT_STATUS_CHANGED`).

### B. 로컬 실행 (Docker, 한 대에서 확인)
```bash
cp .env.example .env
```
`.env`에서 `DOMAIN=localhost`, `LIVEKIT_USE_EXTERNAL_IP=false`, `LIVEKIT_NODE_IP=127.0.0.1`로 바꾼 뒤 실행합니다.
```bash
docker compose up -d --build
```
`https://localhost` 로 접속합니다(Caddy 로컬 인증서 경고는 무시). 이 경우 같은 PC 안의 다른 브라우저 창에서 시청할 수 있습니다.

### C. 개발 모드 (핫 리로드)
- 요구사항: JDK 25, Node 22+, MariaDB, [LiveKit 서버](https://docs.livekit.io/home/self-hosting/local/)
```bash
livekit-server --dev --bind 0.0.0.0 --keys "devkey: dev-secret-that-is-at-least-32-bytes-long"
```
```bash
cd backend && JWT_SECRET=local-dev-jwt-secret-at-least-32-bytes LIVEKIT_API_KEY=devkey LIVEKIT_API_SECRET=dev-secret-that-is-at-least-32-bytes-long ./gradlew bootRun
```
```bash
cd frontend && npm install && npm run dev
```
`http://localhost:5173` 에 접속합니다. Vite가 `/api`를 백엔드 8080으로 프록시합니다. DB 기본값은 `jdbc:mariadb://localhost:3306/onair` (onair/onair)입니다.

---

## 아키텍처

```
 ┌──────────── 송출 (PC / 모바일 브라우저) ───────────┐        ┌──────── 시청 (다른 기기) ────────┐
 │ getDisplayMedia(화면 + 탭/시스템 소리)               │        │ livekit-client (구독 전용 토큰)  │
 │ getUserMedia(카메라 / 마이크)                        │        │ <video> + <audio> 트랙별 재생    │
 │ livekit-client publishTrack (WebRTC)                 │        └───────────────┬──────────────────┘
 │ MediaRecorder → 3초 조각 업로드(녹화)                │                        │
 └──────────┬───────────────────────────┬───────────────┘                        │
            │ HTTPS /api               │ WebRTC (UDP 7882 / TCP 7881)            │
            ▼                           ▼                                         │
   ┌──────────────────┐  wss /rtc  ┌───────────────────┐  미디어 전달(SFU)       │
   │ Caddy (HTTPS)    │──────────▶│ LiveKit Server    │◀────────────────────────┘
   │ SPA + 리버스 프록시│            │ (방 = 방송 1회)    │
   └────────┬─────────┘            └───┬──────▲────────┘
            │ /api                     │웹훅   │ DeleteRoom (방송 종료 시 강제 연결 해제)
            ▼                          ▼       │
   ┌───────────────────────────────────────────┴─┐      ┌──────────────┐
   │ Spring Boot API                              │─────▶│ MariaDB      │ users, broadcasts, comments,
   │  - JWT 인증, 소유자 권한 검증                 │      └──────────────┘ donations, recordings
   │  - LiveKit 토큰 발급 (송출/시청 권한 분리)     │      ┌──────────────┐
   │  - 토스 결제 승인 검증                       │─────▶│ 녹화 파일 볼륨│ /data/recordings/*.webm|mp4
   └──────────────────────────────────────────────┘      └──────────────┘
```

### 방송 상태 관리
```
READY ──start(소유자)──▶ LIVE ──end(소유자) / 송출자 연결 끊김(웹훅)──▶ ENDED
                          ▲                                               │
                          └──────────────── start(다시 방송) ◀────────────┘
```
- **시작**: 소유자 확인 → `LIVE` 저장 → 이번 송출용 **새 LiveKit 방 이름**을 만들고 송출 토큰 발급. 방송마다 새 방을 써서, 이전 세션의 늦은 웹훅이 새 방송을 끝내지 않게 합니다.
- **종료**: 소유자 확인 → `ENDED` 저장 → LiveKit `DeleteRoom` 호출로 **송출자와 시청자 연결을 서버에서 끊습니다** → 진행 중인 녹화를 확정합니다.
- **비정상 종료**: 탭을 닫거나 네트워크가 끊겨 송출자가 방을 나가면, LiveKit 웹훅(`participant_left`, 서명 검증)으로 자동 `ENDED` 처리합니다.
- **시청 토큰**은 `LIVE`일 때만 발급됩니다. 종료된 방송에는 접속할 수 없습니다.

### 디렉터리
```
backend/   Spring Boot API (auth, broadcast, comment, donation, recording, livekit)
frontend/  Vue 3 SPA + Caddyfile
docker-compose.yml, .env.example
```

---

## 인증과 인가
- **회원가입 / 로그인**: BCrypt 비밀번호 해시, HS256 JWT(access token, 12시간) 발급.
- **인증 상태 관리**: 프론트는 토큰을 저장하고 `Authorization: Bearer`로 보냅니다. 새로고침해도 로그인이 유지되고, 401을 받으면 자동으로 로그아웃됩니다.
- **로그아웃**: 토큰의 `jti`를 `revoked_tokens`에 저장하고, 이후 요청은 서버에서 거부합니다(만료된 항목은 1시간마다 정리).
- **인가**: Spring Security에서 공개 엔드포인트(목록, 상세, 시청 토큰, 댓글 조회 등)와 인증 필요 엔드포인트를 나눕니다. **소유자 / 작성자 검증은 서비스 계층**(`BroadcastService.findOwned`, `CommentController.findOwned`, `RecordingService`)에서 하고, 아니면 403을 반환합니다.
- **LiveKit 권한**: API secret은 서버에만 있습니다. 소유자에게만 `canPublish:true` 토큰을, 시청자에게는 구독 전용 토큰을 발급합니다.

---

## API
| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | 공개 | 회원가입 (+토큰) |
| POST | `/api/auth/login` | 공개 | 로그인 |
| POST | `/api/auth/demo-login/{0\|1}` | 공개 | 평가용 데모 계정 로그인 |
| POST | `/api/auth/logout` | 로그인 | 토큰 폐기 |
| GET | `/api/auth/me` | 로그인 | 내 정보 |
| GET | `/api/broadcasts?status=` | 공개 | 방송 목록 |
| GET | `/api/broadcasts/{id}` | 공개 | 방송 상세 |
| POST | `/api/broadcasts` | 로그인 | 방송 등록 |
| PATCH / DELETE | `/api/broadcasts/{id}` | 소유자 | 수정 / 삭제 |
| POST | `/api/broadcasts/{id}/start` | 소유자 | 송출 시작 (송출 토큰) |
| POST | `/api/broadcasts/{id}/end` | 소유자 | 송출 종료 |
| POST | `/api/broadcasts/{id}/viewer-token` | 공개 | 시청 토큰 (LIVE만) |
| POST | `/api/livekit/webhook` | LiveKit 서명 | 송출자 퇴장 시 자동 종료 |
| GET / POST | `/api/broadcasts/{id}/comments` | 조회 공개 / 작성 로그인 | 댓글 |
| PATCH / DELETE | `/api/comments/{id}` | 작성자 | 댓글 수정 / 삭제 |
| POST | `/api/broadcasts/{id}/donations/orders` | 로그인 | 후원 주문 생성 |
| POST | `/api/donations/confirm` | 주문자 | 결제 승인 검증 및 확정 |
| POST | `/api/donations/webhook` | 공개 (재조회 검증) | 토스 승인 통지 |
| GET | `/api/broadcasts/{id}/donations` | 공개 | 후원 합계와 내역 |
| POST | `/api/broadcasts/{id}/recordings` | 소유자 (LIVE) | 녹화 시작 |
| POST | `/api/recordings/{id}/chunks?seq=` | 소유자 | 녹화 조각 업로드 |
| POST | `/api/recordings/{id}/complete` | 소유자 | 녹화 확정 |
| GET | `/api/recordings`, `/api/recordings/{id}` | 공개 | 다시보기 목록 / 상세 |
| GET | `/api/recordings/{id}/video` | 공개 | 영상 스트리밍 (HTTP Range 지원) |
| DELETE | `/api/recordings/{id}` | 소유자 | 녹화 삭제 |

모든 오류는 `{"message": "..."}` 형식으로 반환됩니다(400, 401, 403, 404, 409).

---

## 선택 기능 구현 방식

### 댓글
`comments` 테이블에 방송별로 저장합니다. 수정과 삭제는 `author_id == 로그인 사용자`일 때만 허용하고, 아니면 403을 반환합니다.

### 도네이션 (토스페이먼츠 테스트 모드)
1. **주문 생성**: 서버가 `orderId`, 후원 대상(방송 소유자), 금액(1,000~1,000,000원)을 `PENDING`으로 먼저 저장합니다.
2. **결제 요청**: 브라우저가 토스 결제창(SDK v2)을 띄웁니다.
3. **승인 검증**: 성공 리다이렉트 후 `/api/donations/confirm`이 다음을 확인합니다.
   - 주문자 본인인지
   - 요청 금액 == **저장된 주문 금액**인지 (위변조 차단)
   - 토스 승인 API 응답의 `status == DONE`, `totalAmount`, `orderId`가 주문과 일치하는지
4. **중복 적립 방지**: 주문 행을 비관적 잠금(`SELECT … FOR UPDATE`)으로 직렬화하고, `PENDING`일 때만 `DONE`으로 바꿉니다. `payment_key`는 UNIQUE입니다. 같은 승인 요청이나 웹훅이 여러 번 와도 **한 번만 적립**됩니다. 웹훅 본문은 신뢰하지 않고 `paymentKey`로 토스에 결제를 다시 조회해 검증합니다.
5. **조회**: 방송별 `DONE` 합계, 건수, 최근 내역을 보여줍니다.

### 녹화 및 다시보기
- 송출 브라우저가 송출 영상과 오디오(마이크 + 탭/시스템 소리를 WebAudio로 믹스)를 `MediaRecorder`로 녹화하고, 3초 조각을 **순번(seq)과 함께 순서대로 업로드**합니다.
- 서버는 조각을 파일에 이어 붙입니다. 재전송된 조각은 무시하고, 순번이 건너뛰면 409로 거절해 파일이 깨지지 않게 합니다.
- 방송이 종료되면(정상 종료, 웹훅 종료 모두) 녹화를 `READY`로 확정합니다. **영상 정보와 파일 위치**(`recordings.file_path`)는 DB에, 파일은 Docker 볼륨에 저장되어 재시작 후에도 유지됩니다.
- 재생은 `Resource` 응답으로 HTTP Range를 지원해 구간 탐색이 됩니다.
- 한계: 송출자 브라우저에서 녹화하므로 업로드 대역이 추가로 듭니다. 서버 측 녹화가 필요하면 LiveKit Egress로 바꿀 수 있습니다.

---

## 테스트
```bash
cd backend && ./gradlew test
```
- `ApiIntegrationTest` (H2, MockMvc):
  - 회원가입 → 로그인 → 내 정보 → 로그아웃 후 같은 토큰 401
  - 방송 권한: 비로그인 401, 다른 사용자 403, 소유자 성공 / 시청 토큰 `canPublish:false` / 종료 후 시청 토큰 409
  - 댓글 작성자 권한
  - 후원: 금액 위변조 거절, 같은 승인 2회 + 웹훅 2회 → 1회만 적립
  - 녹화: 조각 재전송 무시, 순서 건너뜀 409, 종료 시 확정, Range 재생
- `LiveKitWebhookVerifyTest`: 웹훅 서명 검증(본문 변조, 다른 시크릿, 다른 키 거부)

---

## 출처
- **Meetbowl** (본인 참여 팀 프로젝트, [beyond-sw-camp/be25-fin-thanos-meetbowl](https://github.com/Dawit-lee/be25-fin-thanos-meetbowl)): Spring Security stateless JWT 구성(`SecurityConfig`)과 LiveKit 접속 토큰 발급 코드(`NimbusLiveKitTokenIssuer`)를 가져와 단순화했습니다. 방송용으로 송출/시청 권한을 나누도록 수정했습니다.
- [LiveKit Server](https://github.com/livekit/livekit) (Apache-2.0): WebRTC SFU 미디어 서버
- [livekit-client](https://github.com/livekit/client-sdk-js) (Apache-2.0): 브라우저 송출 / 시청 SDK
- [토스페이먼츠 결제 SDK v2](https://docs.tosspayments.com/): 테스트 모드 결제
- [Caddy](https://github.com/caddyserver/caddy) (Apache-2.0): HTTPS 리버스 프록시
- 개발 과정에서 AI 개발 도구(Claude Code)를 활용했습니다.
