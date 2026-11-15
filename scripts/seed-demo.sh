#!/usr/bin/env bash
# 데모용 더미 데이터를 공개 API로만 넣는다. 여러 번 실행해도 같은 제목의 방송은 다시 만들지 않는다.
#   사용법: scripts/seed-demo.sh [BASE_URL]   (기본값: 배포 서버)
#   필요: curl, jq, ffmpeg(libvpx, libopus), swift(macOS 기본 포함, 제목 이미지 렌더링용)
# 데모 사용자 비밀번호는 실행 때 랜덤으로 만들고 출력하지 않으며, 재실행 로그인을 위해
# ~/.local/state/onair-seed/<host>.pw (권한 600)에만 저장한다. 후원(도네이션)은 일부러 만들지 않는다.
set -euo pipefail

BASE="${1:-https://52-62-28-94.sslip.io}"
BASE="${BASE%/}"
for c in curl jq ffmpeg swiftc; do
  command -v "$c" >/dev/null || { echo "필요한 명령이 없습니다: $c (ffmpeg는 brew install ffmpeg)" >&2; exit 1; }
done
CURL=(curl -sS --max-time 120)
[[ "$BASE" == https://localhost* ]] && CURL+=(-k) # 로컬 Caddy 자체 서명 인증서

HOST="$(sed -E 's#^https?://##; s#[/:].*##' <<<"$BASE")"
PW_FILE="$HOME/.local/state/onair-seed/$HOST.pw"
mkdir -p "$(dirname "$PW_FILE")"; touch "$PW_FILE"; chmod 600 "$PW_FILE"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
token() { cat "$WORK/tok_$1" 2>/dev/null || true; } # macOS 기본 bash 3.2에는 연관 배열이 없다

# --- API 헬퍼: 실패하면 상태 코드와 본문을 보여 주고 종료 ---
api() { # api METHOD PATH TOKEN [curl 추가 인자...]
  local m="$1" p="$2" t="$3"; shift 3
  local out code
  out="$("${CURL[@]}" -w $'\n%{http_code}' -X "$m" ${t:+-H "Authorization: Bearer $t"} "$@" "$BASE$p")"
  code="${out##*$'\n'}"; out="${out%$'\n'*}"
  if [[ "$code" != 2* ]]; then echo "API 실패: $m $p -> $code $out" >&2; return 1; fi
  printf '%s' "$out"
}
json() { api "$1" "$2" "$3" -H 'Content-Type: application/json' -d "$4"; }

# --- 데모 사용자 (username|닉네임) ---
USERS="cook_mina|미나의 자취요리
dev_jun|개발자 준
game_hoon|훈이게임
music_sora|소라의 기타
talk_yuna|유나랑 수다
trip_minho|민호의 산책"

while IFS='|' read -r u nick; do
  pw="$(grep "^$u " "$PW_FILE" | cut -d' ' -f2 || true)"
  if [[ -n "$pw" ]]; then
    body="$(jq -nc --arg u "$u" --arg p "$pw" '{username:$u,password:$p}')"
    json POST /api/auth/login "" "$body" | jq -r .accessToken >"$WORK/tok_$u"
    echo "로그인: $u"
  else
    pw="$(openssl rand -base64 18 | tr -d '/+=')"
    body="$(jq -nc --arg u "$u" --arg p "$pw" --arg n "$nick" '{username:$u,password:$p,nickname:$n}')"
    if res="$(json POST /api/auth/signup "" "$body" 2>/dev/null)"; then
      echo "$u $pw" >>"$PW_FILE"
      jq -r .accessToken <<<"$res" >"$WORK/tok_$u"
      echo "회원가입: $u"
    else
      echo "건너뜀: $u (이미 있는 계정인데 저장된 비밀번호가 없음)" >&2
    fi
  fi
done <<<"$USERS"

# --- 제목 이미지 렌더러 (ffmpeg에 drawtext가 없는 빌드가 많아 AppKit으로 그린다) ---
cat >"$WORK/render.swift" <<'SWIFT'
import AppKit
let a = CommandLine.arguments // out.png "#rrggbb" 제목 부제
let hex = Int(a[2].dropFirst(), radix: 16)!
let img = NSImage(size: NSSize(width: 1280, height: 720))
img.lockFocus()
NSColor(red: CGFloat(hex >> 16 & 255) / 255, green: CGFloat(hex >> 8 & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1).setFill()
NSRect(x: 0, y: 0, width: 1280, height: 720).fill()
let p = NSMutableParagraphStyle(); p.alignment = .center
func draw(_ s: String, _ size: CGFloat, _ y: CGFloat, _ w: NSFont.Weight, _ alpha: CGFloat) {
  (s as NSString).draw(in: NSRect(x: 60, y: y, width: 1160, height: size * 1.5), withAttributes: [
    .font: NSFont.systemFont(ofSize: size, weight: w), .foregroundColor: NSColor.white.withAlphaComponent(alpha), .paragraphStyle: p])
}
draw(a[3], 64, 340, .bold, 1)
draw(a[4], 32, 270, .regular, 0.8)
draw("OnAir 데모 방송", 24, 40, .medium, 0.6)
img.unlockFocus()
let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: a[1]))
SWIFT
swiftc -O -o "$WORK/render" "$WORK/render.swift" 2>/dev/null

EXISTING="$(api GET /api/broadcasts "")"
exists() { jq -e --arg t "$1" 'any(.[]; .title == $t)' <<<"$EXISTING" >/dev/null; }

chat() { # chat BROADCAST_ID "user:내용"
  local u="${2%%:*}" msg="${2#*:}"
  local t; t="$(token "$u")"; [[ -n "$t" ]] || return 0
  json POST "/api/broadcasts/$1/comments" "$t" "$(jq -nc --arg c "$msg" '{content:$c}')" >/dev/null
}

# --- 종료된 방송 (owner|카테고리|색|길이초|사인파Hz|제목|설명|채팅들(;로 구분, user:내용)) ---
ENDED="game_hoon|게임|#7c3aed|16|330|인디 로그라이크 첫 클리어 도전|보스 3단계까지 갔던 그 게임, 오늘은 진짜 깹니다.|talk_yuna:오 오늘은 깨나요?;dev_jun:보스 패턴 외웠다더니 ㅋㅋ;game_hoon:이번엔 진짜 간다;music_sora:화이팅!!;talk_yuna:방금 회피 미쳤다;trip_minho:클리어 축하해요 👏
game_hoon|게임|#db2777|14|392|주말 랭크 같이 올려요|시청자 참여 랭크전. 디스코드 없이 채팅으로 소통해요.|dev_jun:저 골드인데 껴도 되나요;game_hoon:당연하죠 다음 판 같이 가요;cook_mina:구경만 할게요 ㅎㅎ;talk_yuna:이번 판 캐리각;dev_jun:와 역전승
dev_jun|개발|#0f766e|18|440|Spring Boot로 채팅 서버 만들기 #1|WebSocket 없이 폴링으로 시작해서 점점 개선해 봅니다.|game_hoon:백엔드 입문자인데 따라가도 될까요?;dev_jun:네 처음부터 천천히 할게요;talk_yuna:JPA 설정 부분 다시 한번만요;cook_mina:코드 깃허브에 올려 주시나요?;dev_jun:방송 끝나고 링크 올릴게요;trip_minho:설명 깔끔하다;music_sora:다음 편도 기대돼요
music_sora|음악|#b45309|17|262|새벽 어쿠스틱 기타 커버|조용한 새벽에 듣기 좋은 곡들 연주해요. 신청곡 환영!|trip_minho:목소리 너무 좋다;cook_mina:신청곡 있어요! 잔잔한 걸로;music_sora:다음 곡으로 해 볼게요;talk_yuna:새벽 감성 그 자체;dev_jun:코딩하면서 듣기 딱이네요;game_hoon:소리 깔끔해요
talk_yuna|토크|#be123c|15|349|월요병 극복 수다방|이번 주 각오랑 소소한 일상 얘기 나눠요.|cook_mina:월요일 너무 길어요;talk_yuna:다들 점심 뭐 드셨어요?;dev_jun:저는 김치찌개;music_sora:저는 아직 출근 중 ㅠ;trip_minho:퇴근길에 듣고 있어요;talk_yuna:다들 이번 주도 힘내요!
trip_minho|야외|#ea580c|20|294|한강 노을 산책 라이브|여의도 한강공원에서 해 질 녘 산책 같이 해요.|music_sora:하늘 색 진짜 예쁘다;cook_mina:치킨 시켜 드세요 ㅋㅋ;trip_minho:편의점 라면 먹을 예정;talk_yuna:바람 소리 좋네요;game_hoon:여기 어디쯤이에요?;trip_minho:마포대교 근처예요
cook_mina|일반|#15803d|13|523|10분 자취 요리: 계란볶음밥|재료 세 개로 만드는 초간단 볶음밥. 같이 만들어요.|trip_minho:파기름 먼저 내는 거죠?;cook_mina:네 그게 핵심이에요;dev_jun:오늘 저녁 메뉴 정했다;talk_yuna:굴소스 넣어도 되나요?;cook_mina:한 숟갈 넣으면 더 맛있어요;game_hoon:배고파지는 방송"

while IFS='|' read -r owner cat color secs hz title desc chats; do
  if exists "$title"; then echo "이미 있음: $title"; continue; fi
  t="$(token "$owner")"; [[ -n "$t" ]] || { echo "건너뜀(로그인 불가): $title" >&2; continue; }
  nick="$(grep "^$owner|" <<<"$USERS" | cut -d'|' -f2)"
  "$WORK/render" "$WORK/title.png" "$color" "$title" "$cat · $nick"
  # 정지 제목 화면 + 오른쪽 아래 움직이는 테스트 패턴 + 사인파 소리 (VP8/Opus)
  ffmpeg -nostdin -loglevel error -y -loop 1 -framerate 15 -i "$WORK/title.png" \
    -f lavfi -i "testsrc2=size=240x135:rate=15" -f lavfi -i "sine=frequency=$hz:sample_rate=48000" \
    -filter_complex "[0]scale=960:540[bg];[bg][1]overlay=W-w-30:H-h-30,format=yuv420p[v];[2]volume=0.2[a]" -map "[v]" -map "[a]" \
    -t "$secs" -c:v libvpx -deadline realtime -cpu-used 16 -threads 4 -b:v 500k -c:a libopus -b:a 64k "$WORK/v.webm"
  ffmpeg -nostdin -loglevel error -y -ss 2 -i "$WORK/v.webm" -frames:v 1 -vf scale=640:-2 -q:v 4 "$WORK/thumb.jpg"

  id="$(json POST /api/broadcasts "$t" "$(jq -nc --arg a "$title" --arg d "$desc" --arg c "$cat" '{title:$a,description:$d,category:$c}')" | jq -r .id)"
  json POST "/api/broadcasts/$id/start" "$t" '{"sourceType":"SCREEN"}' >/dev/null
  rid="$(json POST "/api/broadcasts/$id/recordings" "$t" '{"contentType":"video/webm"}' | jq -r .id)"
  api POST "/api/recordings/$rid/chunks?seq=0" "$t" -H 'Content-Type: application/octet-stream' --data-binary "@$WORK/v.webm" >/dev/null
  api POST "/api/broadcasts/$id/thumbnail" "$t" -H 'Content-Type: image/jpeg' --data-binary "@$WORK/thumb.jpg" >/dev/null
  # 다시보기 목록의 길이는 녹화 시작~완료 시각 차이라서, 채팅을 영상 길이에 걸쳐 나눠 보낸 뒤 완료한다.
  IFS=';' read -ra lines <<<"$chats"
  for l in "${lines[@]}"; do sleep $((secs / ${#lines[@]})); chat "$id" "$l"; done
  sleep $((secs % ${#lines[@]}))
  api POST "/api/recordings/$rid/complete" "$t" >/dev/null
  json POST "/api/broadcasts/$id/end" "$t" '{}' >/dev/null
  echo "종료 방송 생성: #$id $title (녹화 #$rid, 채팅 ${#lines[@]}개)"
done <<<"$ENDED"

# --- 방송 예정 (owner|카테고리|제목|설명) ---
READY="dev_jun|개발|React 상태관리 Q&A|Redux, Zustand, Context 언제 뭘 쓸지 질문 받아요.
music_sora|음악|신청곡 받는 금요 라이브|금요일 밤, 채팅으로 신청곡 받아서 바로 연주해요.
trip_minho|야외|북한산 일출 등산 라이브|새벽 등산하면서 정상 일출까지 같이 봐요."

while IFS='|' read -r owner cat title desc; do
  if exists "$title"; then echo "이미 있음: $title"; continue; fi
  t="$(token "$owner")"; [[ -n "$t" ]] || { echo "건너뜀(로그인 불가): $title" >&2; continue; }
  id="$(json POST /api/broadcasts "$t" "$(jq -nc --arg a "$title" --arg d "$desc" --arg c "$cat" '{title:$a,description:$d,category:$c}')" | jq -r .id)"
  echo "예정 방송 생성: #$id $title"
done <<<"$READY"

echo "완료: $BASE"
