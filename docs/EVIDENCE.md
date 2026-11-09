# 요구사항별 근거

- 서비스: https://52-62-28-94.sslip.io (테스트 계정: 로그인 화면 「데모 방송자」/「데모 시청자」 버튼)
- 저장소: https://github.com/Dawit-lee/onair-live-streaming
- 코드 링크는 커밋 `f80e383`에 고정되어 있어 줄 번호가 바뀌지 않습니다.
- 자동 테스트: `cd backend && ./gradlew test` (7개, [ApiIntegrationTest](../backend/src/test/java/com/onair/ApiIntegrationTest.java), [LiveKitWebhookVerifyTest](../backend/src/test/java/com/onair/LiveKitWebhookVerifyTest.java))

각 항목의 「제출용 근거」 블록을 제출 페이지의 「+ 근거 추가」에 그대로 붙여 넣으면 됩니다.

---

## 필수 1. [PC 라이브 방송] PC 화면을 캡처하여 실시간으로 송출한다
> **제출용 근거**: 스튜디오에서 「PC 화면 공유」를 고르면 `getDisplayMedia`(livekit-client `createLocalScreenTracks`)로 화면을 캡처하고, WebRTC(LiveKit SFU)로 실시간 송출합니다. 코드: StudioPage.vue L64-L78(캡처), L143-L146(송출). 확인: 서비스에서 데모 방송자 로그인 → 방송 만들기 → PC 화면 공유 → 미리보기 → 방송 시작 → 다른 기기에서 시청.

- 캡처: [StudioPage.vue#L64-L78](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L64-L78)
- 송출: [StudioPage.vue#L141-L146](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L141-L146) (`room.connect` → `publishTrack(source: ScreenShare)`)
- 화면 공유를 지원하지 않는 기기 감지: [StudioPage.vue#L27-L28](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L27-L28)

## 필수 2. [PC 라이브 방송] 탭 소리 또는 시스템 소리와 마이크 음성을 함께 송출한다
> **제출용 근거**: 화면 캡처 시 `audio: true, systemAudio: 'include'`로 탭/시스템 소리를 함께 받고, 마이크 트랙과 **각각 별도 오디오 트랙(ScreenShareAudio, Microphone)으로 동시에 publish**합니다. 시청자는 두 트랙을 모두 재생합니다. 송출 화면에 마이크/탭 소리 레벨 미터가 있어 소리가 들어오는지 확인할 수 있습니다. 코드: StudioPage.vue L64-L76, L147-L148 / WatchPage.vue L44-L47.

- 탭/시스템 소리 캡처: [StudioPage.vue#L64-L76](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L64-L76)
- 두 오디오 트랙 동시 송출: [StudioPage.vue#L147-L148](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L147-L148)
- 시청자 쪽 모든 오디오 트랙 재생: [WatchPage.vue#L40-L48](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/WatchPage.vue#L40-L48)
- 지원 환경: Chrome/Edge. 탭 소리는 모든 OS, 시스템 소리는 Windows 「전체 화면」 공유 시 ([README 지원 환경](../README.md#지원-환경))

## 필수 3. [PC 라이브 방송] 마이크 음성을 함께 송출한다
> **제출용 근거**: `createLocalAudioTrack({echoCancellation, noiseSuppression})`로 마이크를 열고 화면 영상과 함께 `Microphone` 트랙으로 publish합니다. 방송 중 마이크 켜기/끄기도 지원합니다. 코드: StudioPage.vue L82, L148.

- 마이크 캡처: [StudioPage.vue#L82](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L82)
- 송출: [StudioPage.vue#L148](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L148)

## 필수 4. [PC 라이브 방송] 방송 시작과 종료 시 송출 연결을 생성하고 종료한다
> **제출용 근거**: 시작 시 서버가 소유자를 확인하고 송출 토큰을 발급하며, 클라이언트가 LiveKit에 연결해 트랙을 publish합니다. 종료 시 서버가 상태를 ENDED로 바꾸고 **LiveKit DeleteRoom을 호출해 송출자와 시청자 연결을 서버에서 끊습니다.** 브라우저 「공유 중지」나 탭 닫기도 웹훅으로 자동 종료합니다. 코드: BroadcastService.java L73-L91, LiveKitService.java L76, StudioPage.vue L125-L211.

- 시작(연결 생성): [BroadcastService.java#L73-L79](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L73-L79), [StudioPage.vue#L125-L148](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L125-L148)
- 종료(연결 해제): [BroadcastService.java#L82-L91](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L82-L91), [LiveKitService.java#L76-L91](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/livekit/LiveKitService.java#L76-L91), [StudioPage.vue#L172-L212](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L172-L212)
- 「공유 중지」 시 종료: [StudioPage.vue#L78](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L78)
- 비정상 종료(웹훅, 서명 검증): [LiveKitWebhookController.java#L25-L34](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/livekit/LiveKitWebhookController.java#L25-L34), [BroadcastService.java#L105-L113](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L105-L113)
- 배포 서버에서 확인: 종료 API 호출 후 송출자 연결 상태 `disconnected`(사유 ROOM_DELETED), 시청 화면 「방송이 종료되었습니다」

## 필수 5. [모바일 라이브 방송] 휴대폰 카메라 영상과 마이크 음성을 함께 실시간으로 송출한다
> **제출용 근거**: 모바일에서 스튜디오를 열면 기본으로 「카메라」 모드가 선택됩니다. `getUserMedia`(livekit-client `createLocalVideoTrack({facingMode})`)로 전면/후면 카메라를, `createLocalAudioTrack`로 마이크를 열어 Camera + Microphone 트랙으로 동시에 publish합니다. 코드: StudioPage.vue L28, L80-L82, L143-L148.

- 모바일 감지 및 기본 카메라 모드: [StudioPage.vue#L27-L29](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L27-L29)
- 카메라 + 마이크 캡처: [StudioPage.vue#L80-L82](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L80-L82)
- 송출: [StudioPage.vue#L143-L148](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L143-L148)
- 지원 환경: Android Chrome, iOS Safari 15+ (HTTPS)

## 필수 6. [모바일 라이브 방송] 휴대폰 마이크 음성을 함께 송출한다
> **제출용 근거**: 카메라 영상과 별도로 마이크를 `createLocalAudioTrack({echoCancellation: true, noiseSuppression: true})`로 열어 Microphone 트랙으로 함께 송출합니다. 스튜디오의 마이크 레벨 미터로 입력을 확인하고, 방송 중 음소거/해제할 수 있습니다. 코드: StudioPage.vue L82, L148, L214-L218.

- [StudioPage.vue#L82](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L82), [#L148](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L148)

## 필수 7. [모바일 라이브 방송] 모바일에서 방송을 시작하고 종료한다
> **제출용 근거**: PC와 같은 스튜디오 화면이 반응형으로 동작하고, 모바일에서 「● 방송 시작」/「■ 방송 종료」 버튼으로 시작/종료합니다. 서버에는 sourceType=CAMERA로 저장되어 목록과 상세에 「📱 모바일 카메라 방송」으로 표시됩니다. 코드: StudioPage.vue L125-L203, BroadcastService.java L73-L91.

- 시작/종료: [StudioPage.vue#L125-L203](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/StudioPage.vue#L125-L203)
- 서버 상태 전이: [Broadcast.java#L62-L81](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/Broadcast.java#L62-L81)

## 필수 8. [방송 관리 및 시청] 방송 정보를 등록, 조회, 수정, 삭제한다
> **제출용 근거**: REST API `POST/GET/PATCH/DELETE /api/broadcasts`로 제목, 설명, 카테고리를 관리합니다. 화면: 「방송 만들기」, 목록, 상세, 「정보 수정」, 「삭제」. 테스트: ApiIntegrationTest.broadcastPermissions (등록 → 조회 → 수정 → 삭제 → 404). 코드: BroadcastController.java L33-L59.

- API: [BroadcastController.java#L33-L59](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastController.java#L33-L59)
- 서비스: [BroadcastService.java#L32-L70](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L32-L70)
- 화면: [BroadcastFormPage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/BroadcastFormPage.vue)
- 테스트: [ApiIntegrationTest.java#L69-L111](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L69-L111)

## 필수 9. [방송 관리 및 시청] 방송 목록과 상세 화면을 제공하고, PC와 모바일에서 송출한 방송을 각각 다른 기기에서 수신하고 재생한다
> **제출용 근거**: 홈은 방송 목록(전체/방송 중/종료 탭, 방송 중 우선 정렬)이고, 상세 화면은 플레이어, 정보, 채팅, 후원으로 구성됩니다. 시청자는 구독 전용 토큰으로 LiveKit에 연결해 영상 트랙은 `<video>`, 오디오 트랙(마이크, 탭 소리)은 각각 `<audio>`로 재생합니다. 배포 서버에서 송출 탭과 **비로그인 시청 탭**에서 실시간 수신을 확인했습니다. 코드: HomePage.vue, WatchPage.vue L36-L70.

- 목록: [HomePage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/HomePage.vue), [BroadcastService.java#L32-L42](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L32-L42)
- 상세 + 수신/재생: [WatchPage.vue#L36-L70](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/WatchPage.vue#L36-L70)
- 시청 토큰(구독 전용): [BroadcastService.java#L94-L101](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L94-L101), [LiveKitService.java#L61-L73](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/livekit/LiveKitService.java#L61-L73)

## 필수 10. [방송 관리 및 시청] 방송 중과 종료 상태를 관리하고 방송 종료 시 송출을 중단한다
> **제출용 근거**: 상태는 READY → LIVE → ENDED이고, DB `broadcasts.status`에 저장됩니다. 이미 LIVE면 시작 409, LIVE가 아니면 시청 토큰 409입니다. 종료 시 LiveKit 방을 삭제해 송출을 서버에서 중단하고, 송출자가 끊기면 웹훅으로 자동 ENDED 처리합니다. 매 시작마다 새 방 이름을 써서 이전 세션의 늦은 웹훅이 새 방송을 끝내지 않습니다. 테스트: broadcastPermissions(시작 → 중복 시작 409 → 종료 → 시청 토큰 409). 코드: Broadcast.java L62-L85.

- 상태 전이: [Broadcast.java#L62-L85](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/Broadcast.java#L62-L85)
- 종료 시 송출 중단: [BroadcastService.java#L82-L113](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L82-L113)
- 시청 화면 상태 반영: [WatchPage.vue#L26-L35](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/WatchPage.vue#L26-L35)
- 테스트: [ApiIntegrationTest.java#L94-L106](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L94-L106)

## 필수 11. [방송 관리 및 시청] 방송 정보를 영구 저장하고 서버 재시작 후에도 조회한다
> **제출용 근거**: MariaDB(Docker 볼륨 `db-data`)에 저장하고, 스키마는 Flyway 마이그레이션(V1__init.sql)으로 관리합니다(`ddl-auto: validate`). 배포 환경에서 `docker compose restart backend db` 후에도 방송, 녹화 목록이 그대로 조회되는 것을 확인했습니다. 코드: V1__init.sql L15-L30, docker-compose.yml L12, L80.

- 스키마: [V1__init.sql#L15-L30](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/resources/db/migration/V1__init.sql#L15-L30)
- DB 설정: [application.yml#L4-L13](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/resources/application.yml#L4-L13)
- 영구 볼륨: [docker-compose.yml#L12](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/docker-compose.yml#L12), [#L79-L82](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/docker-compose.yml#L79-L82)

## 필수 12. [사용자 인증 및 인가] 회원가입, 로그인, 로그아웃 및 인증 상태 관리
> **제출용 근거**: 회원가입(BCrypt 해시), 로그인(HS256 JWT 발급), 로그아웃(토큰 jti를 revoked_tokens에 저장해 이후 요청을 서버에서 거부)을 구현했습니다. 프론트는 토큰을 저장해 새로고침 후에도 로그인을 유지하고, 401을 받으면 자동으로 로그아웃합니다. 테스트: authFlow_signupLoginMeLogout(로그아웃한 토큰 → 401). 코드: AuthController.java L52-L88, TokenService.java L29-L47, SecurityConfig.java L113-L124.

- API: [AuthController.java#L52-L88](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/auth/AuthController.java#L52-L88)
- 비밀번호 해시/검증: [AuthService.java#L22-L39](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/auth/AuthService.java#L22-L39)
- 토큰 발급/폐기: [TokenService.java#L29-L51](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/auth/TokenService.java#L29-L51), 폐기 토큰 거부: [SecurityConfig.java#L113-L124](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/auth/SecurityConfig.java#L113-L124)
- 프론트 상태 관리: [auth.js](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/lib/auth.js), [api.js](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/lib/api.js)
- 테스트: [ApiIntegrationTest.java#L43-L61](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L43-L61)

## 필수 13. [사용자 인증 및 인가] 비로그인 사용자도 방송을 조회/시청하고, 방송 등록과 송출은 로그인한 사용자만 가능
> **제출용 근거**: Spring Security에서 목록, 상세, 시청 토큰 등은 공개(permitAll)하고 나머지 `/api/**`는 인증을 요구합니다. 비로그인 등록 요청은 401이고, 시청 토큰은 `canPublish:false`라서 LiveKit 서버도 비로그인 사용자의 송출을 거부합니다. 배포 서버에서 비로그인 등록 → 401, 비로그인 시청 → 재생을 확인했습니다. 테스트: broadcastPermissions. 코드: SecurityConfig.java L33-L70.

- 공개/인증 경로: [SecurityConfig.java#L33-L70](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/auth/SecurityConfig.java#L33-L70)
- 송출 권한 분리 토큰: [LiveKitService.java#L47-L73](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/livekit/LiveKitService.java#L47-L73)
- 테스트: [ApiIntegrationTest.java#L69-L92](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L69-L92)

## 필수 14. [사용자 인증 및 인가] 방송 수정, 삭제 및 송출 제어 시 서버에서 방송 소유자 권한을 검증
> **제출용 근거**: 수정, 삭제, 송출 시작, 종료, 녹화 시작은 모두 `BroadcastService.findOwned`를 거칩니다. 방송이 없으면 404, 소유자가 아니면 403입니다. 화면에서 버튼을 숨기는 것과 별개로 서버에서 거절합니다. 테스트: broadcastPermissions에서 다른 사용자의 PATCH/DELETE/start/end → 403, 비로그인 → 401. 코드: BroadcastService.java L120-L124.

- 소유자 검증: [BroadcastService.java#L120-L124](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L120-L124) (사용처 L57, L64, L74, L83)
- 녹화 소유자 검증: [RecordingService.java#L48-L55](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/recording/RecordingService.java#L48-L55)
- 테스트: [ApiIntegrationTest.java#L80-L87](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L80-L87)

---

## 선택 1. [댓글] 로그인한 사용자가 방송별 댓글을 작성, 조회, 수정, 삭제
> **제출용 근거**: `GET/POST /api/broadcasts/{id}/comments`, `PATCH/DELETE /api/comments/{id}`. 조회는 공개이고 작성은 로그인 필요입니다. 방송 상세와 스튜디오 오른쪽 채팅 패널에서 3초마다 새로고침되며, 본인 댓글에 수정/삭제 버튼이 보입니다. 테스트: commentPermissions. 코드: CommentController.java L44-L76, CommentPanel.vue.

- [CommentController.java#L44-L76](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/comment/CommentController.java#L44-L76), [CommentPanel.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/components/CommentPanel.vue)

## 선택 2. [댓글] 댓글 수정 및 삭제 시 서버에서 작성자 권한 검증
> **제출용 근거**: 수정과 삭제는 `findOwned`에서 `author_id == 로그인 사용자`를 확인하고, 아니면 403입니다. 테스트: 방송 소유자라도 남의 댓글 수정/삭제는 403, 작성자는 성공. 코드: CommentController.java L78-L83, ApiIntegrationTest.java L114-L126.

- [CommentController.java#L78-L83](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/comment/CommentController.java#L78-L83), [ApiIntegrationTest.java#L114-L126](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L114-L126)

## 선택 3. [도네이션] 로그인한 사용자가 후원 대상과 금액을 선택하고 테스트 결제를 요청
> **제출용 근거**: 방송 상세 후원 패널에서 후원 대상(방송자)을 확인하고 금액(프리셋 또는 직접 입력)과 메시지를 입력합니다. 서버가 주문을 먼저 저장한 뒤 토스페이먼츠 SDK v2 결제창(테스트 키)을 엽니다. 코드: DonationPanel.vue L36-L56, DonationService.java L40-L54.

- [DonationPanel.vue#L36-L56](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/components/DonationPanel.vue#L36-L56), [DonationService.java#L40-L54](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/donation/DonationService.java#L40-L54)

## 선택 4. [도네이션] 서버에서 결제 승인 결과, 금액, 후원 대상을 검증하고 후원 내역을 저장
> **제출용 근거**: 승인 요청 시 주문자 본인 여부, 요청 금액 == 저장된 주문 금액(위변조 차단)을 확인한 뒤 토스 승인 API를 호출하고, 응답의 status=DONE, totalAmount, orderId가 주문과 일치하는지 다시 검증해 DONE으로 저장합니다. 후원 대상은 주문 생성 시 서버가 방송 소유자로 정합니다(자기 방송 후원 차단). 테스트: 금액 위변조 시 토스 호출 없이 400. 코드: DonationService.java L60-L74, L105-L112.

- [DonationService.java#L60-L74](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/donation/DonationService.java#L60-L74), 검증 함수 [#L105-L112](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/donation/DonationService.java#L105-L112)
- 테스트: [ApiIntegrationTest.java#L129-L141](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L129-L141)

## 선택 5. [도네이션] 중복 승인 통지에 따른 중복 적립을 방지하고 방송별 후원 내역과 합계를 조회
> **제출용 근거**: 주문 행을 비관적 잠금(SELECT … FOR UPDATE)으로 직렬화하고, `approve()`는 PENDING일 때만 DONE으로 바꿉니다. payment_key, order_id는 UNIQUE입니다. 토스 웹훅은 본문을 믿지 않고 paymentKey로 결제를 재조회해 같은 규칙으로 처리합니다. 테스트: 같은 승인 2회 + 웹훅 2회 → 토스 승인 1회, 합계 5,000원/1건. 방송별 합계/건수/내역: `GET /api/broadcasts/{id}/donations`. 코드: DonationRepository.java L15-L23, Donation.java L48-L54, DonationService.java L81-L103.

- 잠금/합계: [DonationRepository.java#L15-L27](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/donation/DonationRepository.java#L15-L27)
- 1회 적립: [Donation.java#L48-L54](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/donation/Donation.java#L48-L54)
- 웹훅 처리/조회: [DonationService.java#L81-L103](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/donation/DonationService.java#L81-L103)
- 테스트: [ApiIntegrationTest.java#L143-L156](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/test/java/com/onair/ApiIntegrationTest.java#L143-L156)

## 선택 6. [녹화 및 다시보기] 라이브 방송을 녹화 영상으로 저장
> **제출용 근거**: 송출 중인 영상과 오디오(마이크 + 탭/시스템 소리를 WebAudio로 믹스)를 MediaRecorder로 녹화하고, 3초 조각을 순번과 함께 서버에 업로드합니다. 서버는 순서를 검증해 하나의 파일(webm/mp4)로 이어 붙이고(재전송 무시, 건너뜀 409), 방송 종료 시 확정합니다. 테스트: recording_chunksAppendInOrderAndPlayback. 코드: recorder.js L28-L64, RecordingService.java L48-L96.

- [recorder.js#L28-L64](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/lib/recorder.js#L28-L65), [RecordingService.java#L48-L96](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/recording/RecordingService.java#L48-L96)

## 선택 7. [녹화 및 다시보기] 녹화 영상 목록과 상세 조회 및 재생
> **제출용 근거**: 상단 「다시보기」 메뉴에서 목록을 보고, 상세 화면의 `<video>`로 재생합니다. 영상 API는 HTTP Range(206)를 지원해 구간 탐색이 됩니다. 테스트: 전체 재생 200, `Range: bytes=3-` → 206. 코드: RecordingController.java L58-L77, RecordingsPage.vue, RecordingPage.vue.

- [RecordingController.java#L58-L77](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/recording/RecordingController.java#L58-L77), [RecordingsPage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/RecordingsPage.vue), [RecordingPage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/frontend/src/pages/RecordingPage.vue)

## 선택 8. [녹화 및 다시보기] 영상 정보와 저장 위치를 영구 저장
> **제출용 근거**: `recordings` 테이블에 방송 ID, 제목, 형식, 크기, 상태, 녹화 시각, **파일 위치(file_path)**를 저장하고, 파일은 Docker 볼륨 `recordings`(/data/recordings)에 둡니다. 배포 환경에서 재시작 후에도 목록과 재생이 유지되는 것을 확인했습니다. 코드: V1__init.sql L62-L76, docker-compose.yml L39.

- [V1__init.sql#L62-L76](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/resources/db/migration/V1__init.sql#L62-L76), [docker-compose.yml#L39](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/docker-compose.yml#L39), 경로 검증 [RecordingService.java#L122-L126](https://github.com/Dawit-lee/onair-live-streaming/blob/f80e383/backend/src/main/java/com/onair/recording/RecordingService.java#L122-L126)

---

## 함께 올리면 좋은 스크린샷 / 영상 (직접 촬영)
| # | 장면 | 근거 대상 |
|---|---|---|
| 1 | PC Chrome 공유 창에서 「탭 오디오도 공유」 체크 + 스튜디오 레벨 미터가 움직이는 화면 | 필수 1~3 |
| 2 | PC 송출 중 + 휴대폰에서 같은 방송이 재생되는 화면 (두 기기를 한 화면에) | 필수 1, 4, 9 |
| 3 | 휴대폰 카메라 송출 중 + PC에서 재생되는 화면 | 필수 5~7, 9 |
| 4 | 「■ 방송 종료」 직후 시청 화면 「방송이 종료되었습니다」 | 필수 4, 10 |
| 5 | 비로그인 상태로 목록과 시청이 되는 화면 + 방송 만들기 클릭 시 로그인 화면으로 이동 | 필수 13 |
| 6 | 데모 시청자로 남의 방송 상세에 수정/삭제 버튼이 없는 화면 + README의 curl 403 결과 | 필수 14 |
| 7 | `./gradlew test` 통과 결과 | 필수 8, 10, 12~14 |
| 8 | 다시보기 목록/재생 화면, 후원 완료 화면 | 선택 |
