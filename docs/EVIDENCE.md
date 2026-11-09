# 요구사항별 근거

- 서비스: https://52-62-28-94.sslip.io (테스트 계정: 로그인 화면 「데모 방송자」/「데모 시청자」 버튼)
- 저장소: https://github.com/Dawit-lee/onair-live-streaming
- 코드 링크는 커밋 `7ecf8b9`에 고정되어 있어 이후 코드가 바뀌어도 줄 번호가 어긋나지 않습니다.
- 자동 테스트: `cd backend && ./gradlew test` (8개, [ApiIntegrationTest.java](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java), [LiveKitWebhookVerifyTest.java](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/LiveKitWebhookVerifyTest.java))

각 항목의 「제출용 근거」 블록을 제출 페이지의 「+ 근거 추가」에 그대로 붙여 넣으면 됩니다.

---

## 필수 1. [PC 라이브 방송] PC 화면을 캡처하여 실시간으로 송출한다
> **제출용 근거**: 스튜디오에서 「🖥️ PC 화면 공유」를 고르면 `getDisplayMedia`(livekit-client `createLocalScreenTracks`)로 화면을 캡처하고, WebRTC(LiveKit SFU)로 실시간 송출합니다. 송출 중에는 30초마다 화면을 캡처해 목록 썸네일로 올립니다. 코드: StudioPage.vue L69-L83(캡처), L143-L150(송출). 확인: 데모 방송자 로그인 → 방송하기 → PC 화면 공유 → 미리보기 켜기 → 방송 시작 → 다른 기기에서 시청.

- 캡처: [StudioPage.vue#L69-L83](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L69-L83)
- 송출: [StudioPage.vue#L143-L150](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L143-L150) (`room.connect` → `publishTrack(source: ScreenShare)`)
- 화면 공유 미지원 기기 감지: [StudioPage.vue#L28-L30](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L28-L30)
- 라이브 썸네일 업로드: [StudioPage.vue#L212-L234](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L212-L234)

## 필수 2. [PC 라이브 방송] 탭 소리 또는 시스템 소리와 마이크 음성을 함께 송출한다
> **제출용 근거**: 화면 캡처 시 `audio: true, systemAudio: 'include'`로 탭/시스템 소리를 함께 받고, 마이크와 **각각 별도 오디오 트랙(ScreenShareAudio, Microphone)으로 동시에 publish**합니다. 시청자는 두 트랙을 모두 재생합니다. 스튜디오에 마이크와 탭/시스템 소리 레벨 미터가 있어 소리 입력을 확인할 수 있습니다. 코드: StudioPage.vue L69-L81, L149-L150, L98-L121(미터) / WatchPage.vue L50-L58.

- 탭/시스템 소리 캡처: [StudioPage.vue#L69-L81](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L69-L81)
- 두 오디오 트랙 동시 송출: [StudioPage.vue#L149-L150](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L149-L150)
- 레벨 미터: [StudioPage.vue#L98-L121](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L98-L121)
- 시청자 쪽 모든 오디오 트랙 재생: [WatchPage.vue#L50-L58](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/WatchPage.vue#L50-L58)
- 지원 환경: Chrome/Edge. 탭 소리는 모든 OS, 시스템 소리는 Windows 「전체 화면」 공유 시 ([README 지원 환경](../README.md#지원-환경))

## 필수 3. [PC 라이브 방송] 마이크 음성을 함께 송출한다
> **제출용 근거**: `createLocalAudioTrack({echoCancellation, noiseSuppression})`로 마이크를 열고 화면 영상과 함께 `Microphone` 트랙으로 publish합니다. 방송 중 마이크 켜기/끄기를 지원합니다. 코드: StudioPage.vue L87, L150, L253-L257.

- 마이크 캡처: [StudioPage.vue#L87](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L87), 송출: [StudioPage.vue#L150](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L150), 음소거: [StudioPage.vue#L253-L257](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L253-L257)

## 필수 4. [PC 라이브 방송] 방송 시작과 종료 시 송출 연결을 생성하고 종료한다
> **제출용 근거**: 시작 시 서버가 소유자를 확인하고 송출 토큰을 발급하며, 클라이언트가 LiveKit에 연결해 트랙을 publish합니다. 종료 시 서버가 상태를 ENDED로 바꾸고 **LiveKit DeleteRoom을 호출해 송출자와 시청자 연결을 서버에서 끊습니다.** 브라우저 「공유 중지」나 탭 닫기도 웹훅으로 자동 종료합니다. 코드: BroadcastService.java L118-L135, LiveKitService.java L78-L91, StudioPage.vue L130-L199.

- 시작(연결 생성): [BroadcastService.java#L118-L124](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L118-L124), [StudioPage.vue#L130-L176](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L130-L176)
- 종료(연결 해제): [BroadcastService.java#L127-L135](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L127-L135), [LiveKitService.java#L78-L91](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/livekit/LiveKitService.java#L78-L91), [StudioPage.vue#L178-L199](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L178-L199), [StudioPage.vue#L242-L251](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L242-L251)
- 「공유 중지」 시 종료: [StudioPage.vue#L83](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L83)
- 비정상 종료(웹훅, 서명 검증): [LiveKitWebhookController.java#L25-L34](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/livekit/LiveKitWebhookController.java#L25-L34), [BroadcastService.java#L150-L158](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L150-L158)
- 배포 서버에서 확인: 종료 API 호출 후 송출자 연결 `disconnected`(사유 ROOM_DELETED), 시청 화면 「방송이 종료되었습니다」

## 필수 5. [모바일 라이브 방송] 휴대폰 카메라 영상과 마이크 음성을 함께 실시간으로 송출한다
> **제출용 근거**: 모바일에서 스튜디오를 열면 기본으로 「📱 카메라」가 선택됩니다. `getUserMedia`(livekit-client `createLocalVideoTrack({facingMode})`)로 전면/후면 카메라를, `createLocalAudioTrack`로 마이크를 열어 Camera + Microphone 트랙으로 동시에 publish합니다. 코드: StudioPage.vue L28-L30, L85-L87, L145-L150.

- 모바일 감지 및 기본 카메라 모드: [StudioPage.vue#L28-L30](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L28-L30)
- 카메라 + 마이크 캡처: [StudioPage.vue#L85-L87](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L85-L87)
- 송출: [StudioPage.vue#L145-L150](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L145-L150)
- 지원 환경: Android Chrome, iOS Safari 15+ (HTTPS)

## 필수 6. [모바일 라이브 방송] 휴대폰 마이크 음성을 함께 송출한다
> **제출용 근거**: 카메라 영상과 별도로 마이크를 `createLocalAudioTrack({echoCancellation: true, noiseSuppression: true})`로 열어 Microphone 트랙으로 함께 송출합니다. 스튜디오의 마이크 레벨 미터로 입력을 확인하고, 방송 중 음소거/해제할 수 있습니다. 코드: StudioPage.vue L87, L150, L253-L257.

- [StudioPage.vue#L87](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L87), [StudioPage.vue#L150](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L150), [StudioPage.vue#L253-L257](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L253-L257)

## 필수 7. [모바일 라이브 방송] 모바일에서 방송을 시작하고 종료한다
> **제출용 근거**: 스튜디오 화면이 모바일 폭에 맞춰 세로로 배치되고, 「● 방송 시작」/「■ 방송 종료」 버튼으로 시작과 종료를 합니다. 서버에는 sourceType=CAMERA로 저장되어 목록 카드와 시청 화면에 「📱 모바일」로 표시됩니다. 코드: StudioPage.vue L130-L199, Broadcast.java L62-L81.

- 시작/종료: [StudioPage.vue#L130-L199](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L130-L199)
- 서버 상태 전이: [Broadcast.java#L62-L81](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/Broadcast.java#L62-L81)

## 필수 8. [방송 관리 및 시청] 방송 정보를 등록, 조회, 수정, 삭제한다
> **제출용 근거**: REST API `POST/GET/PATCH/DELETE /api/broadcasts`로 제목, 설명, 카테고리를 관리합니다. 화면: 「방송하기」(등록), 홈/시청 화면(조회), 「정보 수정」, 「삭제」. 테스트: ApiIntegrationTest.broadcastPermissions(등록 → 조회 → 수정 → 삭제 → 404). 코드: BroadcastController.java L40-L66, BroadcastService.java L90-L115.

- API: [BroadcastController.java#L40-L66](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastController.java#L40-L66)
- 서비스: [BroadcastService.java#L90-L115](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L90-L115)
- 화면: [BroadcastFormPage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/BroadcastFormPage.vue)
- 테스트: [ApiIntegrationTest.java#L69-L111](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L69-L111)

## 필수 9. [방송 관리 및 시청] 방송 목록과 상세 화면을 제공하고, PC와 모바일에서 송출한 방송을 각각 다른 기기에서 수신하고 재생한다
> **제출용 근거**: 홈은 「지금 라이브」(시청자 많은 순) / 「방송 예정」 / 「지난 방송」 섹션으로 나뉘고, 카드마다 라이브 썸네일, LIVE·PC/모바일 배지, 시청자 수가 표시됩니다. 왼쪽 사이드바에는 방송 중인 채널 목록이, 상단에는 검색창이 있습니다. 시청 화면은 플레이어(LIVE, 시청자 수, 방송 시간 표시), 방송자 정보, 채팅으로 구성되고, 시청자는 구독 전용 토큰으로 LiveKit에 연결해 영상은 `<video>`, 오디오 트랙(마이크, 탭 소리)은 각각 `<audio>`로 재생합니다. 배포 서버에서 송출 탭과 **비로그인 시청 탭**의 실시간 수신을 확인했습니다. 코드: HomePage.vue L25-L30, WatchPage.vue L47-L81, LiveKitService.java L63-L75.

- 목록: [HomePage.vue#L25-L30](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/HomePage.vue#L25-L30), [BroadcastCard.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/BroadcastCard.vue), [BroadcastService.java#L47-L67](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L47-L67) (시청자 수 포함)
- 라이브 채널 사이드바와 검색: [App.vue#L35-L66](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/App.vue#L35-L66), [live.js](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/lib/live.js)
- 시청 수신/재생: [WatchPage.vue#L47-L81](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/WatchPage.vue#L47-L81)
- 플레이어 HUD(LIVE, 시청자 수, 방송 시간): [WatchPage.vue#L147-L151](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/WatchPage.vue#L147-L151)
- 시청 토큰(구독 전용): [BroadcastService.java#L139-L146](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L139-L146), [LiveKitService.java#L63-L75](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/livekit/LiveKitService.java#L63-L75)
- 시청자 수(LiveKit 참가자 조회): [LiveKitService.java#L98-L123](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/livekit/LiveKitService.java#L98-L123)

## 필수 10. [방송 관리 및 시청] 방송 중과 종료 상태를 관리하고 방송 종료 시 송출을 중단한다
> **제출용 근거**: 상태는 READY → LIVE → ENDED이고 DB `broadcasts.status`에 저장됩니다. 이미 LIVE면 시작 409, LIVE가 아니면 시청 토큰 409입니다. 종료 시 LiveKit 방을 삭제해 송출을 서버에서 중단하고, 송출자가 끊기면 웹훅으로 자동 ENDED 처리합니다. 시작할 때마다 새 방 이름을 써서 이전 세션의 늦은 웹훅이 새 방송을 끝내지 않습니다. 홈과 시청 화면은 상태에 따라 「지금 라이브 / 방송 예정 / 지난 방송」, 「방송 시작 전 / 방송이 종료되었습니다(+다시보기)」로 표시됩니다. 테스트: broadcastPermissions(시작 → 중복 시작 409 → 종료 → 시청 토큰 409). 코드: Broadcast.java L62-L85, BroadcastService.java L127-L158.

- 상태 전이: [Broadcast.java#L62-L85](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/Broadcast.java#L62-L85)
- 종료 시 송출 중단: [BroadcastService.java#L127-L158](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L127-L158)
- 시청 화면 상태 반영: [WatchPage.vue#L30-L45](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/WatchPage.vue#L30-L45)
- 테스트: [ApiIntegrationTest.java#L94-L106](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L94-L106)

## 필수 11. [방송 관리 및 시청] 방송 정보를 영구 저장하고 서버 재시작 후에도 조회한다
> **제출용 근거**: MariaDB(Docker 볼륨 `db-data`)에 저장하고, 스키마는 Flyway 마이그레이션(V1__init.sql)으로 관리합니다(`ddl-auto: validate`). 배포 환경에서 `docker compose restart backend db` 후에도 방송과 녹화 목록이 그대로 조회되는 것을 확인했습니다. 코드: V1__init.sql L15-L30, docker-compose.yml L12, L79-L82.

- 스키마: [V1__init.sql#L15-L30](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/resources/db/migration/V1__init.sql#L15-L30)
- DB 설정: [application.yml#L4-L13](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/resources/application.yml#L4-L13)
- 영구 볼륨: [docker-compose.yml#L12](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/docker-compose.yml#L12), [docker-compose.yml#L79-L82](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/docker-compose.yml#L79-L82)

## 필수 12. [사용자 인증 및 인가] 회원가입, 로그인, 로그아웃 및 인증 상태 관리
> **제출용 근거**: 회원가입(BCrypt 해시), 로그인(HS256 JWT 발급), 로그아웃(토큰 jti를 revoked_tokens에 저장해 이후 요청을 서버에서 거부)을 구현했습니다. 프론트는 토큰을 저장해 새로고침 후에도 로그인을 유지하고, 401을 받으면 자동 로그아웃합니다. 테스트: authFlow_signupLoginMeLogout(로그아웃한 토큰 → 401). 코드: AuthController.java L52-L88, TokenService.java L29-L51, SecurityConfig.java L114-L125.

- API: [AuthController.java#L52-L88](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/auth/AuthController.java#L52-L88)
- 비밀번호 해시/검증: [AuthService.java#L22-L39](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/auth/AuthService.java#L22-L39)
- 토큰 발급/폐기: [TokenService.java#L29-L51](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/auth/TokenService.java#L29-L51), 폐기 토큰 거부: [SecurityConfig.java#L114-L125](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/auth/SecurityConfig.java#L114-L125)
- 프론트 상태 관리: [auth.js](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/lib/auth.js), [api.js](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/lib/api.js)
- 테스트: [ApiIntegrationTest.java#L43-L61](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L43-L61)

## 필수 13. [사용자 인증 및 인가] 비로그인 사용자도 방송을 조회/시청하고, 방송 등록과 송출은 로그인한 사용자만 가능
> **제출용 근거**: Spring Security에서 목록, 상세, 썸네일, 시청 토큰 등은 공개(permitAll)하고 나머지 `/api/**`는 인증을 요구합니다. 비로그인 등록 요청은 401이고, 시청 토큰은 `canPublish:false`라서 LiveKit 서버도 비로그인 사용자의 송출을 거부합니다. 비로그인 사용자는 채팅 입력 대신 「로그인하고 채팅과 후원에 참여하세요」 안내를 봅니다. 배포 서버에서 비로그인 등록 → 401, 비로그인 시청 → 재생을 확인했습니다. 테스트: broadcastPermissions. 코드: SecurityConfig.java L33-L71.

- 공개/인증 경로: [SecurityConfig.java#L33-L71](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/auth/SecurityConfig.java#L33-L71)
- 송출 권한 분리 토큰: [LiveKitService.java#L49-L75](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/livekit/LiveKitService.java#L49-L75)
- 테스트: [ApiIntegrationTest.java#L69-L92](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L69-L92)

## 필수 14. [사용자 인증 및 인가] 방송 수정, 삭제 및 송출 제어 시 서버에서 방송 소유자 권한을 검증
> **제출용 근거**: 수정, 삭제, 송출 시작, 종료, 썸네일 업로드는 모두 `BroadcastService.findOwned`를 거치고, 녹화 시작도 소유자를 확인합니다. 방송이 없으면 404, 소유자가 아니면 403입니다. 화면에서 버튼을 숨기는 것과 별개로 서버에서 거절합니다. 테스트: broadcastPermissions에서 다른 사용자의 PATCH/DELETE/start/end → 403, 비로그인 → 401 / thumbnail_ownerOnlyJpegAndPublicRead에서 다른 사용자 업로드 → 403. 코드: BroadcastService.java L165-L169.

- 소유자 검증: [BroadcastService.java#L165-L169](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L165-L169) (사용처: 수정 [BroadcastService.java#L97](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L97), 삭제 [BroadcastService.java#L104](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L104), 썸네일 [BroadcastService.java#L72](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L72), 시작/종료 [BroadcastService.java#L118-L135](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L118-L135))
- 녹화 소유자 검증: [RecordingService.java#L48-L55](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/recording/RecordingService.java#L48-L55)
- 테스트: [ApiIntegrationTest.java#L80-L87](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L80-L87), [ApiIntegrationTest.java#L184-L199](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L184-L199)

---

## 선택 1. [댓글] 로그인한 사용자가 방송별 댓글을 작성, 조회, 수정, 삭제
> **제출용 근거**: `GET/POST /api/broadcasts/{id}/comments`, `PATCH/DELETE /api/comments/{id}`. 조회는 공개, 작성은 로그인 필요입니다. 시청 화면과 스튜디오 오른쪽 **채팅**에서 3초마다 새로고침되고, 본인 메시지에 마우스를 올리면 「수정 / 삭제」가 보입니다. 방송자 메시지는 🎙️로 구분됩니다. 테스트: commentPermissions. 코드: CommentController.java L44-L76, ChatPanel.vue L49-L103.

- API: [CommentController.java#L44-L76](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/comment/CommentController.java#L44-L76)
- 채팅 화면(조회/작성/수정/삭제): [ChatPanel.vue#L49-L103](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L49-L103), [ChatPanel.vue#L142-L145](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L142-L145)

## 선택 2. [댓글] 댓글 수정 및 삭제 시 서버에서 작성자 권한 검증
> **제출용 근거**: 수정과 삭제는 `findOwned`에서 `author_id == 로그인 사용자`를 확인하고, 아니면 403입니다. 테스트: 방송 소유자라도 남의 댓글 수정/삭제는 403, 작성자는 성공. 코드: CommentController.java L78-L83, ApiIntegrationTest.java L114-L126.

- [CommentController.java#L78-L83](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/comment/CommentController.java#L78-L83), [ApiIntegrationTest.java#L114-L126](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L114-L126)

## 선택 3. [도네이션] 로그인한 사용자가 후원 대상과 금액을 선택하고 테스트 결제를 요청
> **제출용 근거**: 시청 화면 채팅 입력창 옆 「💸 후원」을 누르면 후원 모달이 열리고, 후원 대상(방송자), 금액(프리셋 또는 직접 입력), 메시지를 정합니다. 서버가 주문을 먼저 저장한 뒤 토스페이먼츠 SDK v2 결제창(테스트 키)을 엽니다. 비로그인, 자기 방송, 결제 키 미설정이면 이유를 안내합니다. 코드: DonationModal.vue L25-L50, ChatPanel.vue L24-L29, DonationService.java L40-L54.

- 후원 모달/결제 요청: [DonationModal.vue#L25-L50](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/DonationModal.vue#L25-L50)
- 후원 가능 여부 안내: [ChatPanel.vue#L24-L29](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L24-L29), [ChatPanel.vue#L105-L108](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L105-L108)
- 주문 생성(대상/금액 서버 저장): [DonationService.java#L40-L54](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/donation/DonationService.java#L40-L54)

## 선택 4. [도네이션] 서버에서 결제 승인 결과, 금액, 후원 대상을 검증하고 후원 내역을 저장
> **제출용 근거**: 승인 요청 시 주문자 본인 여부와 요청 금액 == 저장된 주문 금액(위변조 차단)을 확인한 뒤 토스 승인 API를 호출하고, 응답의 status=DONE, totalAmount, orderId가 주문과 일치하는지 다시 검증해 DONE으로 저장합니다. 후원 대상은 주문 생성 시 서버가 방송 소유자로 정합니다(자기 방송 후원 차단). 배포 서버에서 가짜 paymentKey 승인 요청이 토스에서 거절되고 적립되지 않는 것을 확인했습니다. 테스트: 금액 위변조 시 토스 호출 없이 400. 코드: DonationService.java L60-L74, L105-L112.

- [DonationService.java#L60-L74](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/donation/DonationService.java#L60-L74), 검증 함수 [DonationService.java#L105-L112](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/donation/DonationService.java#L105-L112)
- 테스트: [ApiIntegrationTest.java#L129-L141](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L129-L141)

## 선택 5. [도네이션] 중복 승인 통지에 따른 중복 적립을 방지하고 방송별 후원 내역과 합계를 조회
> **제출용 근거**: 주문 행을 비관적 잠금(SELECT … FOR UPDATE)으로 직렬화하고, `approve()`는 PENDING일 때만 DONE으로 바꿉니다. payment_key와 order_id는 UNIQUE입니다. 토스 웹훅은 본문을 믿지 않고 paymentKey로 결제를 다시 조회해 같은 규칙으로 처리합니다. 테스트: 같은 승인 2회 + 웹훅 2회 → 토스 승인 1회, 합계 5,000원/1건. 방송별 합계/건수/내역은 `GET /api/broadcasts/{id}/donations`로 조회하고, 화면에서는 **채팅 상단에 누적 금액**, **채팅 흐름 안에 후원 메시지 강조 카드**, **새 후원 시 플레이어 위 알림**으로 표시합니다. 코드: DonationRepository.java L15-L27, Donation.java L48-L54, DonationService.java L81-L103, ChatPanel.vue L32-L37, L59-L70.

- 잠금/합계: [DonationRepository.java#L15-L27](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/donation/DonationRepository.java#L15-L27)
- 1회 적립: [Donation.java#L48-L54](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/donation/Donation.java#L48-L54)
- 웹훅 처리/조회: [DonationService.java#L81-L103](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/donation/DonationService.java#L81-L103)
- 채팅 속 후원 표시: [ChatPanel.vue#L32-L37](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L32-L37), [ChatPanel.vue#L130-L133](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L130-L133)
- 새 후원 알림: [ChatPanel.vue#L59-L70](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/components/ChatPanel.vue#L59-L70), [WatchPage.vue#L97-L104](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/WatchPage.vue#L97-L104), [WatchPage.vue#L152-L156](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/WatchPage.vue#L152-L156)
- 테스트: [ApiIntegrationTest.java#L143-L156](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/test/java/com/onair/ApiIntegrationTest.java#L143-L156)

## 선택 6. [녹화 및 다시보기] 라이브 방송을 녹화 영상으로 저장
> **제출용 근거**: 송출 중인 영상과 오디오(마이크 + 탭/시스템 소리를 WebAudio로 믹스)를 MediaRecorder로 녹화하고, 3초 조각을 순번과 함께 서버에 업로드합니다. 서버는 순서를 검증해 하나의 파일(webm/mp4)로 이어 붙이고(재전송 무시, 건너뜀 409), 방송 종료 시 확정합니다. 테스트: recording_chunksAppendInOrderAndPlayback. 코드: recorder.js L28-L64, RecordingService.java L48-L96.

- [recorder.js#L28-L64](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/lib/recorder.js#L28-L64), [RecordingService.java#L48-L96](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/recording/RecordingService.java#L48-L96)

## 선택 7. [녹화 및 다시보기] 녹화 영상 목록과 상세 조회 및 재생
> **제출용 근거**: 「🎞️ 다시보기」 메뉴에서 썸네일과 영상 길이가 표시된 목록을 보고, 상세 화면에서 재생합니다. 종료된 방송 화면에도 「▶ 다시보기」 버튼이 나옵니다. 영상 API는 HTTP Range(206)를 지원해 구간 탐색이 됩니다. 테스트: 전체 재생 200, `Range: bytes=3-` → 206. 코드: RecordingController.java L58-L77.

- [RecordingController.java#L58-L77](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/recording/RecordingController.java#L58-L77), [RecordingsPage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/RecordingsPage.vue), [RecordingPage.vue](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/RecordingPage.vue)

## 선택 8. [녹화 및 다시보기] 영상 정보와 저장 위치를 영구 저장
> **제출용 근거**: `recordings` 테이블에 방송 ID, 제목, 형식, 크기, 상태, 녹화 시각, **파일 위치(file_path)**를 저장하고, 파일은 Docker 볼륨 `recordings`(/data/recordings)에 둡니다. 배포 환경에서 재시작 후에도 목록과 재생이 유지되는 것을 확인했습니다. 코드: V1__init.sql L62-L76, docker-compose.yml L39.

- [V1__init.sql#L62-L76](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/resources/db/migration/V1__init.sql#L62-L76), [docker-compose.yml#L39](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/docker-compose.yml#L39), 경로 검증 [RecordingService.java#L122-L126](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/recording/RecordingService.java#L122-L126)

---

## 추가 구현 (요구사항 외)
- **라이브 썸네일**: 송출 화면을 30초마다 JPEG로 올려 목록 카드와 종료 화면 배경에 사용합니다. 업로드는 소유자만 가능하고 JPEG인지 검증합니다. [BroadcastService.java#L71-L87](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastService.java#L71-L87), [BroadcastController.java#L84-L98](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/broadcast/BroadcastController.java#L84-L98)
- **시청자 수**: LiveKit 참가자 조회(5초 캐시)로 목록, 사이드바, 플레이어에 표시합니다. [LiveKitService.java#L98-L123](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/backend/src/main/java/com/onair/livekit/LiveKitService.java#L98-L123)
- **후원 알림**: 새 후원이 들어오면 시청자와 방송자 화면의 플레이어 위에 5초간 알림을 띄웁니다. [StudioPage.vue#L236-L240](https://github.com/Dawit-lee/onair-live-streaming/blob/7ecf8b9/frontend/src/pages/StudioPage.vue#L236-L240)

---

## 함께 올리면 좋은 스크린샷 / 영상 (직접 촬영)
| # | 장면 | 근거 대상 |
|---|---|---|
| 1 | PC Chrome 공유 창에서 「탭 오디오도 공유」 체크 + 스튜디오 레벨 미터가 움직이는 화면 | 필수 1~3 |
| 2 | PC 송출 중 + 휴대폰에서 같은 방송이 재생되는 화면 (두 기기를 한 화면에) | 필수 1, 4, 9 |
| 3 | 휴대폰 카메라 송출 중 + PC에서 재생되는 화면 | 필수 5~7, 9 |
| 4 | 「■ 방송 종료」 직후 시청 화면 「방송이 종료되었습니다」 | 필수 4, 10 |
| 5 | 홈(지금 라이브 썸네일, 사이드바) + 비로그인 상태로 시청, 「방송하기」 클릭 시 로그인 화면 이동 | 필수 9, 13 |
| 6 | 데모 시청자로 남의 방송에 수정/삭제 버튼이 없는 화면 + README의 curl 403 결과 | 필수 14 |
| 7 | `./gradlew test` 통과 결과 | 필수 8, 10, 12~14 |
| 8 | 채팅 속 후원 카드와 플레이어 위 후원 알림, 다시보기 재생 화면 | 선택 |
