# 새 캐릭터 그림 요청서 — 문하생 반수선 · 문하생 삼삼 · 사범 화점

> **이 파일을 이미지 생성 에이전트에게 그대로 건넨다**(백로그 #301 — 2026-11-02).
> **함께 첨부할 것**: 기존 캐릭터 다섯 장 `캐릭터1.png` ~ `캐릭터5.png`(이 폴더 — 화풍의 기준) · 반수선을 그릴 때는 장기 앱의 `bansuseon.png`(`jg-v2.0/release/rn-app/assets/images/`).
> 영어 프롬프트는 5절 — **공통 문단 + 캐릭터 문단**을 이어 붙여 쓴다.

---

## 1. 앱과 캐릭터

「포켓 바둑 코치」는 혼자 AI와 바둑을 두는 앱이다. 상대 캐릭터는 **한 바둑 도장의 식구**이고, 칭호가 곧 실력 서열이다 —
급은 **문하생**, 1급 한 명만 **수제자**, 단부터 **사범**, 최고수가 **관장**.

| 급수 | 이름 | 정체 | 그림 |
| --- | --- | --- | --- |
| 18급 | 문하생 초롱이 | 청사초롱 정령 | 장기 앱에서 가져온다(6절) |
| 15급 | 문하생 판다 | 판다 | 있음 — `캐릭터1.png` |
| 12급 | **문하생 반수선** | 동자승(권고) | **이번 요청** |
| 9급 | 문하생 돌뫼 | 돌 정령 | 있음 — `캐릭터2.png` |
| 6급 | 문하생 한수선 | 동자승 | 장기 앱에서 가져온다(6절) |
| 3급 | **문하생 삼삼** | 삼색 고양이(권고) | **이번 요청** |
| 1급 | 수제자 반상 | 사람 아이 | 있음 — `캐릭터3.png` |
| 3단 | 사범 꼬북 | 거북 | 있음 — `캐릭터4.png` |
| 5단 | **사범 화점** | 꽃사슴(권고) | **이번 요청** |
| 7단 | 관장 천원 | 부엉이 | 있음 — `캐릭터5.png` |

**구성의 결**: 급(문하생)은 정령 · 아이 · 귀여운 동물이고, 단(사범 · 관장)은 십장생 · 지혜의 동물(거북 · 사슴 · 부엉이)이다.
권고대로면 열 명은 정령 2 · 사람 3 · 동물 5가 되고, 같은 종이 겹치지 않는다.

## 2. 공통 화풍 — 기존 다섯 장 옆에 두면 한 식구로 보여야 한다

- **3D 렌더 치비**(약 2.5등신). 말랑한 비닐 장난감 · 점토 같은 질감, 부드러운 스튜디오 조명. **외곽선을 쓰지 않는다.**
- 크고 반짝이는 검은 눈, 작은 미소. 무섭거나 화난 표정은 쓰지 않는다.
- 옷은 **한복 계열**(저고리 · 도포 · 조끼 · 띠 · 매듭)이다. 색은 아이보리 · 크림, 남색, 작은 빨강 포인트, 나무색이다.
- 손에 **바둑 도구를 하나 이상** 든다. 나무 바둑판(격자 + 흑백 돌 몇 개) · 흑돌이나 백돌 · 나무 바둑통 중에서 고른다. **장기 말이나 체스 말은 넣지 않는다.**
- **서열은 모자와 옷의 격식으로 보인다**:
  - 문하생: 모자가 없거나 소박하다(판다는 맨머리, 돌뫼는 돌 두건).
  - 수제자: 머리띠(반상).
  - 사범: 바둑판 무늬 모자 + 도포 + 조끼(꼬북은 바둑판 삿갓).
  - 관장: 남색 정장 관모 + 빨간 술(천원).
- 글자 · 로고 · 워터마크는 넣지 않는다.

## 3. 규격 — 앱이 배경을 자동으로 지우고 원 안에 넣는다

- **정사각형 1:1**, 1024×1024(또는 768×768 — 앱에서 768로 맞춘다).
- 배경은 **평평한 오프화이트 단색**(#F8F5F2 근처)이다. 그라데이션 · 비네트 · 빛 번짐 · 그림자 판을 넣지 않는다. 배경이 고르지 않으면 지운 뒤에 얼룩이 남는다.
- **전신**을 정면이나 3/4 각도로, 가운데에 둔다. 앱은 **그림 전체를 원 안에** 그린다 → 사방에 10% 넘게 여백을 둔다. 소품이 그림 밖으로 잘리면 안 된다.
- 아주 작게도 쓰인다(내 정보 줄의 얼굴은 24dp). 줄여도 **실루엣과 얼굴이 읽혀야 한다** — 얼굴을 크게, 소품은 단순하게 그린다.
- 윤곽 가장자리에 배경과 같은 순백이 넓게 닿으면 지우기가 그 부분까지 먹는다. 흰 털 · 흰 옷의 가장자리는 살짝 크림 · 회색 톤으로 둔다.

## 4. 캐릭터별 — 왜 그 모습인가

### 4.1 문하생 반수선 (12급) — 권고: 동자승(사람)

- **왜**: 장기 앱에 **같은 이름의 동자승 그림**이 있다(종이학을 든 아이). 6급 한수선도 동자승이다. 그래서 「반 수 → 한 수」 두 동자승이 짝으로 읽힌다. 반수선이 더 어리고 작다.
- **생김**: 아주 어린 동자승 · 민머리 · 통통한 볼 · 해맑게 웃는 얼굴. 황토색 승복에 남색 띠, 나무 염주 팔찌를 한다.
- **소품**: 한 손에 흰 종이학(장기 앱의 표지)을, 다른 손바닥에 흰 바둑돌 하나를 든다.
- **주의**: 장기 앱 그림은 **생김새만** 참고한다. 화풍은 2절대로 다시 그린다.
- 다른 안(동물을 원하면): 아기 다람쥐 — 볼주머니에 바둑돌을 물고 있다.

### 4.2 문하생 삼삼 (3급) — 권고: 삼색 고양이(동물)

- **왜**: 「삼삼」은 바둑판 귀의 3·3 자리이자 3급이다. 삼색 고양이(「삼색이」)의 **검정 · 흰 · 주황 얼룩이 흑백 바둑돌을 닮아** 이름과 그림이 한 번에 이어진다.
- **성격**: 수제자 바로 아래의 날렵하고 영리한 문하생이다. 자신감 있는 눈매지만 귀엽게 그린다.
- **생김**: 얼굴 아래 · 가슴 · 배는 흰색, 머리 · 등 · 꼬리에 검정과 주황 얼룩. 얼룩 몇 개는 **바둑돌처럼 동그랗다**. 꼬리 끝이 살짝 말려 있다.
- **옷**: 문하생이라 모자는 없다. 남색 짧은 저고리 · 크림색 바지 · 빨간 허리끈.
- **소품**: 작은 나무 바둑판을 두 앞발로 든다. 판의 **오른쪽 위 귀 3·3 자리에 흑돌 하나**가 놓여 있다.
- 다른 안: 아기 여우 · 수달.

### 4.3 사범 화점 (5단) — 권고: 꽃사슴(동물)

- **왜**: 「화점」(花點)은 바둑판의 별 자리, 곧 **꽃 + 점**이다. 꽃사슴은 이름에 「꽃」이 있고 등에 흰 「점」이 있다. 그 점을 **바둑판 화점처럼 3×3으로 가지런히** 놓는다.
  사슴은 거북(사범 꼬북)과 같은 십장생이라 사범 둘이 한 결로 읽힌다. 관장 천원(부엉이) 바로 아래의 기품을 준다.
- **성격**: 차분하고 우아한 선생님. 온화하고 지혜로운 눈, 엷은 미소.
- **생김**: 따뜻한 갈색 털, 등에 흰 점 3×3, 작고 부드러운 뿔.
- **옷**: 사범의 격식으로 입힌다 — 크림색 도포 · 남색 조끼 · 빨간 매듭 노리개. 챙에 바둑판 격자가 은은히 든 **갓**을 쓰고, 뿔이 갓 양옆으로 나온다. 꼬북의 둥근 삿갓과 모양이 겹치지 않게 한다.
- **소품**: 한 손은 흑돌 하나를 두 손가락에 쥐고 막 한 수를 가르치려는 듯 들어 올린다. 다른 손에는 반쯤 펼친 기보 두루마리를 든다.
- 다른 안: 학(두루미)도 십장생 · 선비 같은 기품이다. 다만 다리가 가늘어 작은 아바타에서 실루엣이 약하다.

## 5. 영어 프롬프트 — 공통 문단 + 캐릭터 문단

**공통(매번 앞에 붙인다)**

```text
Cute 3D-rendered chibi character for a Korean Go (baduk) app. Match the attached reference characters exactly in style:
soft vinyl-toy / clay look, smooth soft studio lighting, no outlines, big glossy black eyes, gentle smile, about 2.5 heads tall.
Korean traditional hanbok-style clothing in ivory/cream and navy blue with small red accents, warm wood tones.
Holding Go equipment (a wooden Go board with a grid and a few black and white stones, a single Go stone, or a wooden Go stone bowl) — no chess or janggi pieces.
Full body, centered, front or three-quarter view, at least 10% empty margin on every side so the whole figure fits inside a circle.
Plain flat off-white background (#F8F5F2) — no gradient, no vignette, no glow, no shadow panel. No text, no logo, no watermark.
Square 1:1, 1024x1024.
```

**반수선**

```text
Character: "Bansuseon", a very young Buddhist child monk, a pupil at a Go school and the younger partner of another child-monk character.
Shaved head, chubby rosy cheeks, bright cheerful open-mouthed smile, small and round.
Ochre monk robe with a navy sash and a wooden prayer-bead bracelet.
Holds a small white paper crane in one hand and a single white Go stone on the other palm. Sitting cross-legged.
Use the attached janggi-app image only for the character's look; render it in the Go-app style described above.
```

**삼삼**

```text
Character: "Samsam", a clever calico cat, the strongest pupil at a Go school.
Calico fur: white face, chest and belly; black and orange patches on the head, back and tail; a few patches are perfectly round like Go stones. Tail curled at the tip.
Confident, bright, slightly mischievous eyes, but still very cute. No hat.
Short navy jeogori jacket, cream trousers, red waist tie.
Holds a small wooden Go board with both paws, with a single black stone on the 3-3 point of the upper-right corner. Standing upright.
```

**화점**

```text
Character: "Hwajeom", a graceful sika deer who is a Go instructor, one step below the grandmaster owl.
Warm brown fur with neat white spots on the back arranged in a tidy 3x3 grid, like the nine star points of a Go board. Small soft antlers.
Calm, kind, wise eyes and a gentle smile.
Instructor attire: cream long robe (dopo) with a navy vest and a red knotted tassel ornament; a traditional Korean gat hat with a subtle Go-grid pattern on the brim, antlers peeking out on either side.
Raises a black Go stone between two fingers as if about to teach a move; holds a half-unrolled paper scroll of a Go diagram in the other hand. Standing upright.
```

**받은 뒤 확인할 것**: 기존 다섯 장 옆에 나란히 놓아 한 식구로 보이는가 · 24px로 줄여도 얼굴이 보이는가 · 배경이 단색인가 · 사방 여백이 있는가. 캐릭터마다 두세 장을 받아 고른다.

## 6. 장기 앱에서 가져올 둘(초롱이 · 한수선) — 참고

장기 앱 그림은 **화풍이 다르다**. 2D에 굵은 외곽선, 어두운 회갈색 그라데이션 배경, 1024px다. 한수선은 장기 말을 들고 있다.
그대로 쓰면 열 명 가운데 둘만 다른 앱 그림처럼 보이고, 어두운 배경은 앱의 배경 지우기(3절)와도 맞지 않는다.
권고: 장기 앱 그림을 참고로 첨부해 **같은 생김새를 2절 화풍 · 3절 규격으로** 다시 그리고, 장기 말은 바둑돌로 바꾼다. 생김새 설명은 이 파일에 적지 않는다(사용자 2026-11-02).
