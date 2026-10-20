# 스플래시 2차 — 새 자세가 필요하다 (2026-10-20)

사용자(2026-10-20): *"캐릭터 두 명이 대국하는 자세를 취하게 하고, 바둑판 들고 있는 이미지가 그대로 반영될 게 아니라 바둑판은 없는 상태로 앉아서 대국하는 모습이어야 합니다.
다른 캐릭터들도 자연스럽게 두 캐릭터가 앉아서 대국 중인 주변으로 서서 관전하는 모습으로 3개 추가로 만들기"*.

**왜 합성으로 안 되나**: 지금 캐릭터 그림(`work/artwork/캐릭터N_transparent.png`)은 자세가 하나씩이고, 판다 · 반상 · 천원은 바둑판을 들고 있다.
앉아서 두는 자세 · 서서 보는 자세는 **그림을 새로 그려야** 한다 — 스크립트는 있는 그림을 옮기고 겹칠 뿐이다.

| 번호 | 캐릭터 | 원본 |
| --- | --- | --- |
| 1 | 문하생 판다 | `work/artwork/캐릭터1_transparent.png` |
| 2 | 문하생 돌뫼 | `work/artwork/캐릭터2_transparent.png` |
| 3 | 수제자 반상 | `work/artwork/캐릭터3_transparent.png` |
| 4 | 사범 꼬북 | `work/artwork/캐릭터4_transparent.png` |
| 5 | 관장 천원 | `work/artwork/캐릭터5_transparent.png` |

구도 안내도: `guides/guide_A_dojo.png` · `guides/guide_B_circle.png` · `guides/guide_C_spotlight.png`(빨간 화살표 = 바라보는 쪽).

## 길 ① — 장면을 통째로 만든다 (권장: 앉은 몸과 판, 관전자의 시선이 한 그림 안에서 맞는다)

이미지 생성 도구에 **원본 다섯 장 + 안내도 한 장**을 함께 붙이고 아래 프롬프트를 넣는다. 결과는 세로 9:16(1080x1920 이상).

**공통(모든 장면 앞에)**
> 첨부한 다섯 캐릭터를 **똑같은 그림체 · 얼굴 · 옷 · 색**으로 그린다(부드러운 3D 채색 치비 스타일). 캐릭터가 들고 있던 작은 바둑판 · 돌통은 **들지 않는다**.
> 사범 꼬북(초록 거북, 바둑판 무늬 삿갓)과 관장 천원(부엉이, 남색 바둑판 무늬 모자)이 **19줄 바둑판(다리 달린 두꺼운 판)을 사이에 두고 방석에 무릎 꿇고 앉아** 대국 중이다 —
> 꼬북은 검은 돌을 집어 판에 놓으려 손을 뻗고, 천원은 날개 끝을 턱에 대고 판을 내려다보며 생각한다. 판 위에는 흑백 돌이 중반쯤 놓여 있다.
> 문하생 판다 · 문하생 돌뫼 · 수제자 반상은 **판 뒤에 서서** 몸을 살짝 숙이고 판을 내려다본다(뒷짐 · 턱 괴기 · 무릎에 손 짚기처럼 셋이 다른 자세). 셋 다 바둑판을 들지 않는다.
> 글자 · 로고 · 워터마크 없음. 캐릭터가 화면 가운데 2/3 안에 모이게(위아래는 잘려도 되는 배경).

- **A 도장 대국(낮)**: 따뜻한 낮빛의 바둑 도장 — 장지문 벽, 먹으로 그린 원 족자, 나무 마루. 정면에서 조금 내려다본 시점. (안내도 A)
- **B 둘러선 관전**: 크림색 바탕에 은은한 원형 빛, 판을 위에서 비스듬히 크게 — 대국자는 판 앞 양쪽 모서리, 관전자는 판 뒤 세 방향. 밝고 귀여운 분위기. (안내도 B)
- **C 스포트라이트(밤)**: 어두운 남색 배경, 위에서 판으로 떨어지는 따뜻한 원뿔 조명, 빛 알갱이. 대국자 둘은 빛 속, 관전자 셋은 반쯤 어둠 속. 긴장감. (안내도 C)

<details><summary>영어 프롬프트(도구가 영어를 더 잘 따를 때)</summary>

**Common**
> Draw the five attached characters in **exactly the same art style, faces, outfits and colors** (soft 3D-painted chibi). They do **not** hold their small Go boards or stone bowls.
> Sabeom Kkobuk (green turtle with a Go-board-patterned straw hat) and Gwanjang Cheonwon (owl with a navy Go-board-patterned cap) **kneel on cushions facing each other across a thick
> 19x19 Go board on legs**, mid-game — the turtle reaches out to place a black stone, the owl rests a wingtip on its chin, studying the board. Black and white stones are scattered mid-game.
> The panda, the stone figure (Dolmoe) and the boy in a headband (Bansang) **stand behind the board**, leaning in to watch, each in a different pose (hands behind back / hand on chin /
> hands on knees). None of them hold a Go board. No text, no logo, no watermark. Keep the characters within the central two thirds; portrait 9:16.

- **A**: a warm daylight Go dojo — shoji wall, a hanging scroll with an ink ensō circle, wooden floor; slightly elevated front view.
- **B**: cream background with a soft circular glow; the board seen large from a high angle; players at the two front corners, the three spectators around the back. Bright and cute.
- **C**: dark navy background, a warm cone of light falling on the board with floating dust motes; the players in the light, the spectators half in shadow. Tense mood.
</details>

## 길 ② — 자세만 따로 만들고 합성은 스크립트가

다섯 장을 각각 **배경 투명 · 전신 · 땅 그림자 없음 · 1024px 이상**으로 만들어 `work/splash/poses/`에 아래 이름으로 넣으면, `scripts/make-splash-images.py`의
배경(도장 · 원형 · 스포트라이트)과 바둑판 위에 안내도 자리대로 얹는다.

| 파일 | 자세 |
| --- | --- |
| `kkobuk_seated.png` | 사범 꼬북 — 방석에 무릎 꿇고 앉아 **오른쪽을 보며** 검은 돌을 놓으려 손을 뻗는다(3/4 측면) |
| `cheonwon_seated.png` | 관장 천원 — 방석에 무릎 꿇고 앉아 **왼쪽을 보며** 날개 끝을 턱에 대고 생각한다(3/4 측면) |
| `panda_standing.png` | 문하생 판다 — 서서 뒷짐 지고 아래를 내려다본다 |
| `dolmoe_standing.png` | 문하생 돌뫼 — 서서 한 손을 턱에 대고 아래를 내려다본다 |
| `bansang_standing.png` | 수제자 반상 — 무릎에 손 짚고 몸을 숙여 신기하다는 듯 내려다본다 |

공통: 원본과 같은 그림체 · 얼굴 · 옷 · 색, 바둑판 · 돌통을 들지 않는다, 글자 없음.
