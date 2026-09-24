#!/usr/bin/env python3
"""문서가 가리키는 경로가 실재하는지 확인한다.

`DOCS_INDEX.md`의 **문서 구조 정책** 절이 폴더를 옮길 때 요구하는 마지막 단계
("문서가 가리키는 파일이 실재하는지 기계로 재확인한다")를 손으로 하지 않게 만든 것이다.
백로그 #58에서 이 저장소는 **재편 한 번에 깨진 참조가 32건까지 늘어나는** 것을 겪었고,
그중 상대 링크 18건은 눈으로는 잡히지 않았다.

무엇을 보는가:
  - **맨 파일명 표기** `` `FILE.md` `` — 그 이름의 문서가 저장소에 **있는지**. 이것이
    이 도구의 가장 중요한 검사다(아래 참고).
  - 모든 `.md`의 마크다운 링크 `](경로.md)` — 상대 경로를 파일 위치 기준으로 푼다
  - 모든 `.md`의 백틱 경로 `` `docs/...md` `` 등 저장소 루트 기준 표기
  - 코드·스크립트·`Makefile` 안의 `docs/....md` 문자열 (주석이 문서를 지목하는 경우)
  - `EXEMPT` 밖에서 **마크다운 링크로 문서를 잇는지** — 참조 표기 정책 위반
  - **소스 경로** — 모든 `.md`의 백틱 `` `shared/.../policy/PlayLevel.kt` ``·`` `application/premium/state/PremiumState.kt` ``
    꼴, 그리고 코드·스크립트·`Makefile`과 `.md`의 ``` 코드 블록 안의 같은 꼴 표기(주석,
    `RepoPaths.appAndroid("platform/…kt")` 같은 경로 계약 문자열). 저장소 루트 기준 정확 경로가 아니면
    **꼬리 일치**, 그다음 **모듈 접두어**로 푼다(아래 `SOURCE_EXTS` 절).
    위 `.md` 검사들과 **따로 세고 따로 한 줄 찍는다.**

## ⚠️ 왜 맨 파일명 검사가 핵심인가

2026-08-31에 참조 표기를 **파일명 수준으로** 통일했다(백로그 #58 후속). 경로를 적지 않으니
문서를 옮겨도 참조가 깨지지 않는다 — `grep`이 어디로 갔든 찾아 준다. 대신 **딱 하나가 사각지대로
남는다**: 삭제된 문서를 이름으로 부르면 **경로처럼 깨지지 않고 멀쩡해 보인다.** 그건 "찾기
어렵다"가 아니라 **"없는 문서를 있는 것처럼 말한다"** 이고, 그대로 두면 다음 사람이 그 문서를
찾다가 포기하거나 내용을 처음부터 다시 정한다.

그래서 이 검사가 정책을 성립시키는 조건이다 — 이것을 끄면 파일명 표기 정책은 근거를 잃는다.

⚠️ **ALLOWED에 있는 것은 깨진 것이 아니다.** 세 종류다 —
  ⓐ 삭제된 문서를 "삭제했다"고 적은 히스토리 서술(가리키는 파일이 없는 것이 옳다),
  ⓑ 아직 만들지 않은 **제안** 문서,
  ⓒ 스크립트가 **만들어 낼** 출력 경로(참조가 아니다).
새 항목을 넣을 때는 세 종류 중 어디인지 사유를 함께 적을 것 — 사유 없는 예외가 쌓이면
이 도구는 통과만 하는 장식이 된다.

## ⚠️ 왜 소스 경로까지 보는가 (2026-09-24, refactor backlog #78·#80)

`.md`만 보던 동안 **파일을 옮길 때마다 문서가 가리키는 소스 경로가 조용히 죽었다.** #78은 옮긴 파일을
가리키는 주석을 손으로 고쳤는데 로드맵 문서 한 곳의 8건을 놓쳤고(→ #80), 훑어 보니 살아 있는 문서
12개에 58건이 더 있었다. 이름 grep으로는 못 찾는다 — **파일은 있고 경로만 틀렸기** 때문이다.
소스 경로의 예외 표 `SOURCE_ALLOWED`도 위와 같은 규칙(사유 없는 예외 금지)이고, 종류가 둘(ⓓ·ⓔ) 더 있다.

사용법: `python3 scripts/check-doc-links.py` (저장소 루트에서). 깨진 것이 있으면 종료코드 1.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys

SKIP_DIRS = {".git", "build", ".gradle", "worktrees", ".claude", "node_modules", "dist"}

# 참조 표기 정책의 **유일한 예외 두 곳**(2026-08-31 사용자 확정).
# `DOCS_INDEX.md`는 **지도**이고 `HANDOVER.md`는 **새로 오는 사람의 첫 화면**이라, 위치를 말하지
# 않으면 그 두 문서가 제 역할을 못 한다. 나머지 전부는 파일명만 쓴다.
EXEMPT = {"docs/DOCS_INDEX.md", "docs/HANDOVER.md"}

# 이름만으로는 문서를 식별하지 못하는 파일들 — 여러 폴더에 같은 이름이 있다.
# 이런 문서는 정책의 예외로 **폴더까지** 적는다(`FEATURE_ACCESS_PRINCIPLES.md`).
GENERIC_NAMES = {"README.md", "summary.md", "index.md"}

# ⚠️ **그 이름이 더 이상 유효하지 않다고 밝히는 문장은 깨진 참조가 아니다.** 이 저장소는 보존 정책상
# 문서를 지우는 것이 정상이고, 지운 사실을 본문에 남기는 관행이 이미 있다
# (예: "전체 비교 근거 문서(`STACK_DECISION.md`)는 2026-08-17 …삭제됐다").
# 그래서 **맨 파일명이 있는 줄**에 아래 낱말이 있으면 의도된 서술로 보고 넘긴다.
#
# ⚠️ 이것은 **어림짐작이다** — 우연히 같은 줄에서 "삭제"를 말하는 진짜 사장된 이름은 놓친다.
# 그 오차를 받아들이는 이유: 반대 방향(정상 서술을 매번 깨진 것으로 보고)은 도구를 못 쓰게 만들고,
# 못 쓰는 검사는 결국 꺼진다. 정확히 잡아야 하는 건은 ALLOWED에 개별로 적는다.
# 삭제뿐 아니라 **개명·통합**도 같은 부류다 — 옛 이름이 안 남는 것이 정상인 서술이다
# (#62에서 세 문서를 합치고 한 문서를 개명하면서 이 부류가 필요해졌다).
REMOVAL_WORDS = (
    "삭제", "제거", "removed", "deleted", "아카이브", "git 히스토리",
    "개명", "이름을 바�", "합쳤", "합쳐", "통합",
    # 2026-09-06 추가 — 이 저장소가 이동을 서술하는 실제 낱말이다(통폐합 때 드러났다).
    "옮겼", "옮기", "평탄화", "통폐합",
)

# **이 저장소의 문서가 아닌** 이름들. 상류 프로젝트 문서를 인용할 때 나온다.
# ⚠️ 파일별 예외(ALLOWED)로 넣지 않는 이유: 같은 외부 문서를 다른 문서가 또 인용하면 그때마다
# 예외를 늘려야 하고, 그러면 "왜 예외인가"가 파일 수만큼 흩어진다. 종류로 한 번만 적는다.
EXTERNAL_NAMES = {
    "Analysis_Engine.md": "KataGo 상류 문서 — 원본 URL이 인용 근처에 있다",
}

# 날짜별 갱신 이력 줄은 **그때의 상태**를 적는 것이라 지금 없는 이름이 나오는 게 정상이다.
CHANGELOG_LINE = re.compile(r"^\s*(갱신:|\|\s*20\d\d-\d\d-\d\d\s*\|)")

# (참조하는 파일, 가리키는 경로) → 사유
ALLOWED: dict[tuple[str, str], str] = {
    # ⓐ 삭제 사실을 적은 히스토리 서술
    ("docs/DOCS_INDEX.md", "docs/archive/2026-08-06-refactoring-log-consolidation/README.md"):
        "2026-08-17 보존 정책 전환으로 삭제한 것을 기록한 문장",
    ("docs/DOCS_INDEX.md", "docs/working-260617/implementation_plan.md"):
        "2026-08-06에 삭제한 것을 기록한 문장",
    ("docs/work/history/THREAD_HISTORY.md", "docs/KATRAIN_UX_BACKLOG.md"):
        "그 시점에 있었던 문서를 가리키는 히스토리 서술",
    ("work/plans/GOOGLE_PLAY_LAUNCH_PLAN.md", "design-handoff/README.md"):
        "2026-09-13에 design-handoff/ 자체를 work/play-store-assets/로 흡수하며 삭제한 것을 기록한 문장",
    ("scripts/run-katago-candidate-refine-experiment.py",
     "docs/archive/2026-06-docs-consolidation/ENGINE_BEGINNER_VISITS_BENCHMARK.md"):
        "옛 아카이브 위치를 적어 둔 주석(사실 서술)",
    # ⓑ 아직 만들지 않은 제안 문서
    ("docs/spec/APP_IA_AND_UI_SPEC.md", "docs/spec/UI_DESIGN_TOKENS.md"): "추천 신규 제안(미작성)",
    ("docs/spec/APP_IA_AND_UI_SPEC.md", "docs/spec/SGF_AND_REVIEW_MODE_SPEC.md"): "추천 신규 제안(미작성)",
    ("docs/spec/APP_IA_AND_UI_SPEC.md", "docs/spec/USER_ONBOARDING_GUIDE.md"): "추천 신규 제안(미작성)",
    # ⓑ' 통합된 원본의 옛 이름 — 본문이 서로를 그 이름으로 인용하고, 머리말의 대응표가
    #     "그 셋이 이 문서의 1·2·3절"임을 밝힌다. 본문을 고치면 "원본 그대로"가 깨진다.
    ("docs/engine/ENGINE_STRENGTH_RESEARCH.md", "ENGINE_BEGINNER_VISITS_BENCHMARK.md"): "이 문서 1절로 합쳐진 원본",
    ("docs/engine/ENGINE_STRENGTH_RESEARCH.md", "ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md"): "이 문서 2절로 합쳐진 원본",
    ("docs/engine/ENGINE_STRENGTH_RESEARCH.md", "ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md"): "이 문서 3절로 합쳐진 원본",
    # ⓐ' **봉인된 발송 산출물** — 2026-08-11에 외부 디자이너에게 보낸 그대로 남긴다.
    #     2026-09-06 통폐합으로 세 문서가 옮겨졌지만 **이 파일은 그때의 발송본**이므로 고치지 않는다
    #     (`260830-260831_POST_LAUNCH_ENHANCEMENTS.md`가 같은 이유로 동결 처리해 둔 산출물이다).
    ("design-handoff/export/2026-08-11-v0.1.2/go_ai_coach_handoff.md", "ux-improvement/README.md"):
        "봉인된 발송본 — 지금 위치는 `UX_IMPROVEMENT.md`",
    ("design-handoff/export/2026-08-11-v0.1.2/go_ai_coach_handoff.md", "premium-mode/README.md"):
        "봉인된 발송본 — 지금 위치는 `PREMIUM_MODE.md`",
    ("design-handoff/export/2026-08-11-v0.1.2/go_ai_coach_handoff.md", "auth-onboarding/README.md"):
        "봉인된 발송본 — 지금 위치는 `LOGIN_AND_ACCOUNT_SYSTEM.md`",
    # ⓒ 스크립트가 만들어 낼 출력 경로
    ("scripts/run-katago-candidate-refine-experiment.py",
     "docs/engine/measurements/engine-benchmark/candidate-refine-latest.md"):
        "--out 기본값(실행하면 생성되는 산출물)",
}

# **봉인 문서 안의 옛 경로**는 깨진 것이 아니다 (2026-09-23 신설).
#
# 이 저장소의 로드맵 문서는 `시작일-완결일_이름.md`로 이름 붙고, **완결일 자리가 채워진 것이
# 봉인된 문서다**(비어 있으면 `260923-_ACTIVE_BACKLOG.md`처럼 진행 중이라는 뜻이다).
# 봉인 문서는 **그 시점의 기록이라 고치지 않는 것이 규칙**이다 — 그래서 그 안의 경로는
# 문서가 옮겨지거나 개명될 때마다 죽지만 **아무도 고칠 수 없다.** 2026-09-23 정리에서
# 개명한 스레드가 "참조하는 곳이 남의 담당"이라 멈추는 교착이 실제로 났고, 그 결과
# 깨진 참조가 6 → 10건으로 늘었다. 고칠 수 없는 것을 계속 빨갛게 세면 이 검사는 곧 꺼진다.
#
# ⚠️ **좁게 적용한다 — 넓히면 이 도구가 무용해진다.**
#   ① **봉인 문서 안에서 출발하는 참조에만** 적용한다. 살아 있는 문서가 봉인 문서를
#      잘못 가리키는 것은 그대로 잡는다(고칠 수 있는 쪽이기 때문이다).
#   ② **경로 표기(`docs/…md`·마크다운 링크)에만** 적용한다. **맨 파일명 표기는 계속 본다** —
#      이름은 문서를 옮겨도 살아남게 만든 표기라, 봉인 문서 안에서도 이름이 안 풀린다는 것은
#      "그 문서가 실제로 사라졌다"는 뜻이고 그건 알아야 하는 사실이다(이 파일 맨 위 설명 참고).
# 예외가 몇 건 먹었는지는 실행할 때마다 찍는다 — 조용한 구멍이 되지 않게.
SEALED_DOC = re.compile(r"^\d{6}-\d{6}_.+\.md$")


def is_sealed(rel: str) -> bool:
    """`시작일-완결일_이름.md` — 완결일이 채워진 봉인 문서인가."""
    return bool(SEALED_DOC.match(os.path.basename(rel)))


# 문서가 표기 형태 자체를 설명할 때 쓰는 자리표시자 — 실재하는 파일이 아니다.
PLACEHOLDER = re.compile(r"(FILE\.md|<[^>]+>\.md)")

BARE_NAME = re.compile(r"`([A-Za-z_][A-Za-z0-9_.-]*\.md)`")
MD_LINK = re.compile(r"\]\(([^)\s#]+\.md)(?:#[^)]*)?\)")
# ⚠️ **접두어 화이트리스트였던 것을 2026-09-06에 넓혔다.** 예전 패턴은
# `(docs|scripts|shared|app-android|engine-android)/` 로 시작하는 것만 봤고, 그 결과
# `` `launch-plan/README.md` `` 처럼 **루트의 다른 폴더를 가리키는 표기 226건을 구조적으로 못 봤다.**
# 루트 마스터플랜 폴더 통폐합 때 실측으로 드러났다 — 폴더를 지운 뒤에도 검사가 초록불이었다.
# 이제 **슬래시를 품고 `.md`로 끝나는 백틱 표기 전부**를 실재 여부로 판정한다.
ROOT_PATH = re.compile(r"`([A-Za-z0-9_][A-Za-z0-9_.\-]*(?:/[A-Za-z0-9_.\-]+)+\.md)`")
CODE_PATH = re.compile(r"docs/[A-Za-z0-9_\-/]+\.md")
CODE_EXTS = (".kt", ".kts", ".py", ".sh")

# ── 소스 경로 (2026-09-24 신설, refactor backlog #80) ────────────────────────────────────────
#
# ⚠️ **위 `.md` 검사와 섞지 않는다.** 판정 규칙이 다르고(꼬리 일치), 위 검사의 동작과 출력 줄을
#    한 글자도 바꾸지 않기 위해서다 — 결과는 `소스 경로 점검 …` 한 줄로 따로 찍는다.
#
# 푸는 순서:
#   ① **정확 경로** — 저장소 루트, 그 파일의 폴더, 그 파일의 최상위 폴더 기준. 셋째는 Gradle 테스트가
#      **모듈 폴더에서** 돌기 때문이다(`app-android`의 테스트가 `"../shared/src/…/X.kt"`로 적는다).
#   ② **꼬리 일치** — 저장소 파일 중 `/<표기>`로 끝나는 것이 있으면 산다. `application/premium/X.kt`
#      같은 짧은 꼴이 이것으로 풀린다. `.../`(와 `…`)는 **중간의 임의 경로**다 — `app-android/.../ui/X.kt`.
#   ③ **모듈 접두어** (2026-09-24 추가) — 첫 조각이 Gradle 모듈(`settings.gradle.kts`의 `include`)이면
#      모듈 폴더와 나머지 사이의 `src/<소스셋>/<언어>/<패키지…>/`를 생략한 표기로 본다 —
#      `engine-android/KataGoJsonPositionAnalysisClient.kt`, `shared/match/AiMoveSelectionPolicy.kt`.
#      (a) 모듈 이름이 **패키지가 될 수 없는 모양**(하이픈 — `engine-android`·`app-android`)이면
#          `<모듈>/(?:.*/)?<나머지>`로 푼다. 달리 읽힐 길이 없다.
#      (b) 그 밖(`shared`)은 모듈 뒤에 **디렉터리가 하나 이상** 있을 때만 그렇게 푼다.
#   ⚠️ (b)의 조건이 이 규칙의 핵심이다. `shared`는 모듈이면서 **패키지 이름**(`com.worksoc.goaicoach.shared`)
#      이기도 해서, `shared/PlayLevel.kt`는 "shared 패키지의 PlayLevel.kt"(→ `shared/policy/`로 옮겨서 죽었다)로도,
#      "shared 모듈 어딘가의 PlayLevel.kt"(→ 파일이 모듈을 떠나지 않는 한 늘 참)로도 읽힌다. 둘째로 읽어 주면
#      **모듈 안의 어떤 이동도 못 잡는다** — 그래서 모듈 바로 뒤 파일 이름은 풀지 않는다(짧게 쓰려면
#      `shared/.../PlayLevel.kt`처럼 `.../`를 적는다). 디렉터리가 하나라도 있으면(`shared/match/X.kt`) 그
#      디렉터리 이름까지 맞아야 하므로, 같은 이름의 폴더로 옮길 때만 못 잡는다.
#      ⚠️ 이 사각지대는 **②보다 넓다** — ②만 있었다면 잡았을 이동을 (b)가 놓친다. 재현(검수, 2026-09-24):
#      `shared/diagnostic/X.kt`로 적은 파일을 `application/diagnostic/`으로 옮기면, (b) 전엔 빨갛고 지금은 초록이다.
#      지금 `shared` 모듈 안에서 이름이 겹치는 폴더는 `diagnostic` 하나이고 그 꼴의 표기는 0건이라 받아들였다.
#      좁히려면 (b)의 `(?:.*/)?`를 "패키지 루트 바로 아래"로 제한하면 된다.
#   ⚠️ (a)·(b)를 가르는 것은 **이름의 모양**이지 "지금 그 이름의 패키지 폴더가 있는가"가 아니다 — 후자로
#      가르면 패키지를 비우는 이동 자체가 규칙을 (b)→(a)로 바꿔, 방금 죽은 경로를 그 이동이 살려 낸다.
#   ⚠️ 모듈 목록을 **최상위 폴더로 넓히지 않는다** — `docs/`·`scripts/`·`work/`에는 생략할 `src/…/패키지`가
#      없어서, 넓히면 옮긴 스크립트(`scripts/x.py` → `scripts/old/x.py`)가 살아 보이기만 한다. 지금 넓혀서 더
#      받아들여지는 것은 0건이라(실측) 결과가 아니라 **뜻**으로 고른 것이다 — 모듈은 Gradle이 정한다.
#   실측(2026-09-24, HEAD a28d74e0 트리): ③이 새로 받아들인 것은 4건 — `engine-android/KataGoJson…Client.kt`
#      (ENGINE_STRENGTH_RESEARCH:843) · `shared/match/AiMoveSelectionPolicy.kt`(FAST_BEGINNER_TIER_DESIGN:37·39) ·
#      `app-android/engine/RemoteEngineSessionBootstrap.kt`(DOCS_INDEX:18) — 이고 전부 그 자리에 실재한다(작업
#      트리에서는 새로 적은 REMOTE_ENGINE_AND_LAYERING:77 `engine-android/RemoteEngineCoreApiAdapter.kt`까지 5건).
#      못 푼 채 남은 97건(모듈 이름으로 시작하는 35건 포함)은 하나도 받아들이지 않았고, 그중 `shared/PlayLevel.kt`
#      꼴(모듈 바로 뒤 파일 이름) 14건은 전부 `shared/policy/`·`shared/enginecontract/`로 옮겨서 죽은 것이다.
#
# ⚠️ 확장자는 **실측으로** 골랐다(2026-09-24). `.kt`·`.kts`·`.swift`에 `.py`·`.sh`를 더했다 — 문서 36건·
#    코드 32건이 전부 실재 경로였다(거짓 양성 0). `.json`·`.txt`·`.xml`은 뺐다 — `files/…json`(기기 안
#    앱 폴더)·AAB 내부 경로처럼 **저장소 밖을 가리키는 표기**라 거짓 양성만 나왔다.
SOURCE_EXTS = (".swift", ".kts", ".kt", ".py", ".sh")  # 긴 것 먼저 — `.kts`가 `.kt`에 먹히지 않게
_SEG = r"[A-Za-z0-9_.\-…]"
_SRC_BODY = rf"[A-Za-z0-9_.…]{_SEG}*(?:/{_SEG}+)+\.(?:{'|'.join(e[1:] for e in SOURCE_EXTS)})"
# `.md` 안: 백틱 하나가 통째로 경로인 것. **슬래시가 있어야** 경로다 — 맨 이름(`GoCoachApp.kt`)은
# 옮겨도 안 죽는 표기라 이 검사가 겨누는 것이 아니다. `:123`·`:72-76`·`:159 이름` 같은 꼬리는 떼고 본다.
MD_SOURCE_PATH = re.compile(rf"`({_SRC_BODY})(?::[^`\n]*)?`")
# 코드 안: 백틱이 없으니 경로 모양의 낱말 전체. 앞에 `/`·`$`·`}`가 붙으면 URL 조각이나 문자열
# 템플릿(`"$dir/X.kt"`)이라 건너뛴다.
#
# ⚠️ **코드 안(주석·문자열)도 본다** — 켜 보고 정했다(2026-09-24). 못 푼 것 9건 = 진짜 죽은 경로 1건
#    (옮긴 어댑터를 옛 `ui/`로 가리키던 KDoc) + 모듈 폴더 기준 상대 경로 5건(→ 위 ①의 셋째 기준으로 풀림)
#    + 거짓 양성 3건(→ `SOURCE_ALLOWED` ⓓ·ⓔ). 셋이면 개별로 정확히 다룰 수 있다.
#    `RepoPaths.applicationPath("analysis/X.kt")` 같은 문자열은 **진짜 경로 계약**이라 잡히는 것이 맞다.
CODE_SOURCE_PATH = re.compile(rf"(?<![A-Za-z0-9_.\-/…$}}])({_SRC_BODY})(?![A-Za-z0-9_])")
# 빌드 산출물은 참조가 아니다 — 지우고 다시 만드는 폴더라 있고 없고가 판정 근거가 못 된다.
BUILD_OUTPUT = re.compile(r"(?:^|/)build/")
# 예시용 가짜 이름(`app-android/.../X.kt`, `XxxApplication.kt`) — 위 `PLACEHOLDER`의 소스판.
SOURCE_PLACEHOLDER = re.compile(r"(?:^|/)(?:[A-Z]|Xxx[A-Za-z0-9_]*)\.[a-z]+$")
# 위 ③(b)의 판정 — Kotlin/Java 패키지 한 조각이 될 수 있는 이름인가.
PACKAGE_NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
GRADLE_INCLUDE = re.compile(r"\binclude\b\s*(\([^)]*\)|[^\n]*)")

# ⚠️ **소스 경로의 이력 서술 면제는 토큰에 붙은 괄호 주석 안만 본다** (2026-09-24 — 위 `.md` 검사와 다른 점).
#    `.md`는 한 줄이 한 문단이라, 줄 전체에서 `REMOVAL_WORDS`를 찾으면 한 낱말이 그 문단의 경로를 전부
#    면제한다. 실측(HEAD 트리): 살아 있는 문서의 소스 토큰 246개 중 19개가 그런 줄에 있었고, 그중 못 푼
#    5건의 4건이 **진짜 죽은 경로**였다 — 로드맵:136·170의 "통합", MQ 문서:38의 "MQ로 옮기는"(파일 이동이
#    아닌 말). 나머지 1건(`DOCS_INDEX.md:18`)은 멀쩡한 경로라 위 ③으로 풀린다.
#    의도된 서술은 전부 **토큰 바로 뒤 괄호**였다 — `ui/X.kt`(같은 날 개명 …)·(개명 — 이후 …). 지운 파일에 붙이는
#    `(이후 삭제 — `해시`)`도 같은 꼴이라 같이 받는다(이 꼴은 아직 문서에 0건 — 픽스처로만 확인했다).
#    표본 PREMIUM_MODE.md:238·248·253: 토큰과 괄호 사이 0자, 괄호 안 낱말 위치 1·6·48자, 괄호 길이 67~144자.
#    ⚠️ **거리 창(토큰 앞뒤 N자)으로는 못 가른다** — 의도된 248은 낱말이 토큰 끝 6자 뒤, 죽은 로드맵:136은
#    5자 뒤였다("`…/FeatureAccessPolicy.kt` 하나로 통합했다" — 통합의 **대상**이라 있어야 하는 파일이다).
#    가르는 것은 거리가 아니라 **그 낱말이 토큰을 꾸미는가**이고, 붙은 괄호가 그것을 기계로 알 수 있는 꼴이다.
#    그래서 틈은 `**`와 빈칸 하나까지만 받고(표본은 전부 0자), 괄호는 **그 줄 안에서 닫혀야** 주석으로 본다.
#    괄호 안 위치에는 상한을 두지 않았다 — 아래 오차 표본(4~57자)과 의도된 표본(1~48자)이 겹쳐서 가르지 못한다.
#    치르는 값: 괄호가 파일 **내용**의 변화를 말하는 경우(`BotCharacter.kt`(필드 제거))도 면제된다 — 모든 문서에서
#    6건, 전부 봉인 문서이고 전부 실재한다(그래서 지금 판정은 하나도 바뀌지 않는다).
#    면제한 건수는 따로 찍는다 — 조용한 구멍이 되지 않게. **실재하는 것은 면제보다 먼저 센다**(주석이 붙었어도
#    파일이 있으면 "실재 확인"이다).
SOURCE_ANNOTATION = re.compile(r"\*{0,2} ?[(（]")

# ⚠️ **`.md`의 ``` 코드 블록 안은 코드와 같은 규칙(`CODE_SOURCE_PATH`)으로 본다** (2026-09-24).
#    `MD_SOURCE_PATH`는 백틱 한 쌍이 통째로 경로일 때만 보므로, 코드 블록의 `// shared/X.kt:53-70` 같은 출처
#    주석은 영영 검사 밖이었다 — ENGINE_STRENGTH_RESEARCH.md:803·815가 파일을 옮긴 뒤에도 초록이었다.
#    켜 보고 정했다: 모든 `.md`의 코드 블록 72개에서 7건이 잡히고(README 2·ENGINE_API_CALL_POLICY 1·
#    ENGINE_STRENGTH_RESEARCH 3·ACTIVE_BACKLOG 1), HEAD 트리에서 못 푼 것은 위 2건(진짜 죽은 경로)뿐 —
#    **거짓 양성 0건.** 블록 안에서 `MD_SOURCE_PATH`에 걸리는 것은 0건이라, 그 규칙은 블록 밖에서만 돌린다
#    (한 표기를 두 번 세지 않게). 들여쓰기(4칸) 코드 블록은 보지 않는다 — 목록 안의 이어진 문단과 구분이 안 된다.
#    ⚠️ 남은 사각지대: 경로가 **일부로** 든 인라인 코드(`RepoPaths.applicationPath("analysis/X.kt")`,
#    `python3 scripts/x.py --옵션`)는 여전히 안 본다 — 작업 트리에 6건, 5건은 풀리고 1건은 계획서가 옮기기 전
#    상태를 적은 것이라 켜면 그 서술이 빨개진다. 켤지는 그런 표기가 더 쌓였을 때 다시 잰다.
FENCE = re.compile(r"^[ \t]*(`{3,}|~{3,})", re.M)

# **동결 전문** — 봉인 문서와 같은 이유로 고칠 수 없다(그 문서 머리말 4번: "고치지 않는다").
# 고칠 수 없는 것을 빨갛게 세면 이 검사는 곧 꺼진다. 그래서 봉인 문서와 함께 **세지 않되 건수는 찍는다.**
# ⚠️ **소스 경로에만** 적용한다 — 위 `.md` 검사의 봉인 규칙(`is_sealed`)은 손대지 않았다.
FROZEN_DOCS = {"docs/spec/PITFALLS.md"}

# (참조하는 파일, 가리키는 표기) → 사유. 위 `ALLOWED`의 ⓐ~ⓒ에 더해 둘 —
#   ⓓ 테스트가 **없어야 한다고** 단언하는 경로(옮긴 파일이 옛 자리에 되살아나지 않는지 지킨다),
#   ⓔ 경로가 아닌 표기(여러 파일을 `/`로 줄여 쓴 문구).
_LAYERING_TEST = "app-android/src/test/java/com/worksoc/goaicoach/architecture/LayeringContractTest.kt"
SOURCE_ALLOWED: dict[tuple[str, str], str] = {
    (_LAYERING_TEST, "middleware/HttpRemotePositionAnalysisTransport.kt"):
        "ⓓ `staleAppAndroidPaths` — 260804에 engine-android로 옮긴 뒤 app-android에 없어야 함을 단언",
    (_LAYERING_TEST, "middleware/RemoteEngineCoreApiAdapter.kt"):
        "ⓓ `staleAppAndroidPaths` — 260804에 engine-android로 옮긴 뒤 app-android에 없어야 함을 단언",
    ("app-android/src/test/java/com/worksoc/goaicoach/ui/UiStringsTest.kt", "UiStringsEn/Ja/Zh.kt"):
        "ⓔ `UiStringsEn.kt`·`UiStringsJa.kt`·`UiStringsZh.kt` 셋을 줄여 쓴 실패 메시지",
}


def line_of(text: str, pos: int) -> str:
    """`pos`가 든 한 줄. 히스토리 서술 면제를 판정하는 데 쓴다."""
    start = text.rfind("\n", 0, pos) + 1
    end = text.find("\n", pos)
    return text[start:end if end != -1 else len(text)]


def walk(root: str):
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            yield os.path.join(base, name)


def source_index(root: str) -> dict[str, list[str]]:
    """저장소의 소스 파일 — 이름 → 저장소 루트 기준 경로들. 소스 경로를 이것 하나로 판정한다.

    ⚠️ 파일 시스템이 아니라 `git ls-files`(추적 + 무시되지 않은 새 파일)를 쓴다 — 훑으면 `.gitignore`된
    로컬 전용 파일까지 잡혀 **이 기계에서만 초록**이 된다. git이 없을 때만 파일 시스템으로 물러선다.
    """
    try:
        out = subprocess.run(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
            cwd=root, capture_output=True, check=True).stdout.decode("utf-8")
        paths = [p for p in out.split("\0") if p]
    except (OSError, subprocess.CalledProcessError, UnicodeDecodeError):
        paths = [os.path.relpath(p, root).replace(os.sep, "/") for p in walk(root)]
    index: dict[str, list[str]] = {}
    for p in paths:
        # 지운 채 아직 커밋 안 한 파일(`--cached`에 남는다)은 없는 것으로 친다.
        if (p.endswith(SOURCE_EXTS) and not SKIP_DIRS.intersection(p.split("/")[:-1])
                and os.path.isfile(os.path.join(root, p))):
            index.setdefault(p.rsplit("/", 1)[-1], []).append(p)
    return index


def gradle_modules(root: str) -> tuple[str, ...]:
    """`settings.gradle(.kts)`의 `include(":a", ":b:c")` → 모듈 폴더(`a`, `b/c`). 긴 것 먼저(겹칠 때 안쪽이 이긴다).

    ⚠️ 하드코딩하지 않는다 — 모듈을 더하거나 빼면 위 ③이 따라가야 한다. 폴더가 없는 `include`는 버린다.
    """
    modules: set[str] = set()
    for fname in ("settings.gradle.kts", "settings.gradle"):
        try:
            text = open(os.path.join(root, fname), encoding="utf-8").read()
        except OSError:
            continue
        for stmt in GRADLE_INCLUDE.finditer(text):
            for name in re.findall(r"""["']:([A-Za-z0-9_.\-:]+)["']""", stmt.group(1)):
                path = name.replace(":", "/")
                if os.path.isdir(os.path.join(root, path)):
                    modules.add(path)
    return tuple(sorted(modules, key=len, reverse=True))


def _path_regex(token: str) -> str:
    """표기 → 정규식 조각. `.../`는 중간의 임의 경로(없어도 된다), `…`는 임의 문자열."""
    return re.escape(token).replace(re.escape(".../"), "(?:.*/)?").replace("…", ".*")


def source_resolves(token: str, rel: str, index: dict[str, list[str]],
                    modules: tuple[str, ...] = ()) -> bool:
    """소스 경로 표기가 실재하는 파일 하나 이상에 닿는가 — 정확 경로, 꼬리 일치, 모듈 접두어 순."""
    name = token.rsplit("/", 1)[-1]
    # 이름에 `…`가 없으면 같은 이름의 파일만 후보다(전부 훑지 않는다).
    candidates = index.get(name, []) if "…" not in name else [p for ps in index.values() for p in ps]
    top = rel.split("/", 1)[0] if "/" in rel else ""
    for base in ("", os.path.dirname(rel), top):
        if os.path.normpath(os.path.join(base, token)).replace(os.sep, "/") in candidates:
            return True
    tail = re.compile(r"(?:^|/)" + _path_regex(token) + "$")
    if any(tail.search(p) for p in candidates):
        return True
    # ③ 모듈 접두어 — 규칙과 이유는 위 `SOURCE_EXTS` 절.
    for module in modules:
        if not token.startswith(module + "/"):
            continue
        rest = token[len(module) + 1:]
        if "/" not in rest and PACKAGE_NAME.fullmatch(module.rsplit("/", 1)[-1]):
            return False  # (b) `shared/PlayLevel.kt` — 모듈로도 패키지로도 읽히는 꼴은 풀지 않는다
        head = re.compile("^" + re.escape(module) + "/(?:.*/)?" + _path_regex(rest) + "$")
        return any(head.search(p) for p in candidates)
    return False


def annotated_as_gone(line: str, end: int) -> bool:
    """줄 안 `end`에서 끝나는 토큰 바로 뒤의 괄호 주석이 삭제·개명·이동을 말하는가(위 `SOURCE_ANNOTATION` 절)."""
    opening = SOURCE_ANNOTATION.match(line, end)
    if not opening:
        return False
    depth = 0
    for k in range(opening.end() - 1, len(line)):
        if line[k] in "(（":
            depth += 1
        elif line[k] in ")）":
            depth -= 1
            if depth == 0:
                return any(word in line[opening.end():k] for word in REMOVAL_WORDS)
    return False  # 그 줄 안에서 안 닫힌 괄호는 주석으로 보지 않는다


def fence_spans(text: str) -> list[tuple[int, int]]:
    """``` / ~~~ 코드 블록의 본문 구간들(여는 줄 다음부터 닫는 줄 앞까지). 안 닫히면 문서 끝까지."""
    marks = list(FENCE.finditer(text))
    spans: list[tuple[int, int]] = []
    i = 0
    while i < len(marks):
        fence = marks[i].group(1)
        body = text.find("\n", marks[i].end())
        body = len(text) if body == -1 else body + 1
        j = i + 1
        while j < len(marks) and not (marks[j].group(1)[0] == fence[0]
                                      and len(marks[j].group(1)) >= len(fence)):
            j += 1
        spans.append((body, marks[j].start() if j < len(marks) else len(text)))
        i = j + 1
    return spans


def main() -> int:
    root = os.getcwd()
    broken: list[tuple[str, str, str]] = []
    sealed_skipped = 0
    source_broken: list[tuple[str, int, str, str]] = []
    source_ok = source_frozen_skipped = source_annotated = 0
    src_index = source_index(root)
    modules = gradle_modules(root)
    # 저장소에 실재하는 문서의 이름 → 경로들. 맨 파일명 표기를 이것으로 판정한다.
    by_name: dict[str, list[str]] = {}
    for path in walk(root):
        if path.endswith(".md"):
            by_name.setdefault(os.path.basename(path), []).append(os.path.relpath(path, root))

    for path in walk(root):
        rel = os.path.relpath(path, root)
        name = os.path.basename(path)
        # 자기 자신은 건너뛴다 — 위 ALLOWED 표가 일부러 없는 경로를 적고 있다.
        if rel == "scripts/check-doc-links.py":
            continue
        is_md = name.endswith(".md")
        is_code = name.endswith(CODE_EXTS) or name == "Makefile"
        if not (is_md or is_code):
            continue
        try:
            text = open(path, encoding="utf-8").read()
        except (OSError, UnicodeDecodeError):
            continue

        found: list[tuple[str, str, str]] = []
        if is_md:
            for match in MD_LINK.finditer(text):
                target = match.group(1)
                if target.startswith(("http", "mailto")):
                    continue
                found.append(("link", os.path.relpath(
                    os.path.normpath(os.path.join(os.path.dirname(path), target)), root),
                    line_of(text, match.start())))
            found += [("path", m.group(1), line_of(text, m.start())) for m in ROOT_PATH.finditer(text)]
        else:
            found += [("code", m.group(0), line_of(text, m.start())) for m in CODE_PATH.finditer(text)]

        for kind, target, line in found:
            if PLACEHOLDER.search(target):
                continue
            # ⚠️ 옮기거나 지운 사실을 적은 서술은 깨진 참조가 아니다 — 맨 파일명 검사와 같은 규칙을
            #    경로 표기에도 적용한다(2026-09-06). 그러지 않으면 이력 줄이 전부 빨개진다.
            if any(word in line for word in REMOVAL_WORDS) or CHANGELOG_LINE.match(line):
                continue
            if (rel, target) in ALLOWED:
                continue
            # ⚠️ **두 기준으로 푼다.** `DOCS_INDEX.md`는 자기 표에서 `spec/APP_IA_AND_UI_SPEC.md`처럼
            #    **자기 폴더 기준**으로 적고, 코드 주석은 `docs/…`처럼 **저장소 루트 기준**으로 적는다.
            #    한쪽만 보면 멀쩡한 표기가 전부 빨개진다(2026-09-06 검사 확장에서 실측).
            here = os.path.dirname(os.path.join(root, rel))
            if not (os.path.exists(os.path.join(root, target))
                    or os.path.exists(os.path.join(here, target))):
                # ⚠️ 봉인 문서 안의 옛 경로 — 봉인 시점엔 유효했고, 그 문서는 고치지 않는다.
                #    **실재 검사를 통과하지 못한 것만** 센다(멀쩡한 표기까지 세면 건수가 거짓이 된다).
                if is_sealed(rel):
                    sealed_skipped += 1
                    continue
                row = (rel, kind, target)
                if row not in broken:
                    broken.append(row)

        # ⚠️ 파일명 표기 정책의 사각지대 — 없는 문서를 이름으로 부르는 것.
        # ⚠️ **2026-09-06부터 코드 주석도 본다.** 그전에는 `.md`만 봤는데, 코드가 문서를
        #    파일명으로 가리키는 주석 30여 곳이 **개명 뒤에도 영구히 검사 밖**이었다.
        for match in BARE_NAME.finditer(text):
            name = match.group(1)
            if PLACEHOLDER.search(name) or (rel, name) in ALLOWED:
                continue
            if name == os.path.basename(rel):
                continue  # 자기 자신을 언급하는 것은 정상
            line = text[text.rfind("\n", 0, match.start()) + 1:
                        (text.find("\n", match.end()) + 1 or len(text)) - 1]
            if name in EXTERNAL_NAMES:
                continue
            if any(word in line for word in REMOVAL_WORDS) or CHANGELOG_LINE.match(line):
                continue
            hits = by_name.get(name, [])
            if not hits:
                row = (rel, "없는 문서를 이름으로 부름", name)
            elif len(hits) > 1 and name not in GENERIC_NAMES:
                row = (rel, f"이름이 겹쳐 어느 것인지 모름({len(hits)}곳)", name)
            else:
                continue
            if row not in broken:
                broken.append(row)

        # 참조 표기 정책: 예외 두 곳 밖에서는 문서를 마크다운 링크로 잇지 않는다.
        if is_md and rel not in EXEMPT:
            for match in MD_LINK.finditer(text):
                target = match.group(1)
                if target.startswith(("http", "mailto")) or PLACEHOLDER.search(target):
                    continue
                row = (rel, "링크 대신 파일명만 쓸 것(정책)", target)
                if row not in broken:
                    broken.append(row)

        # ⚠️ 소스 경로 — 옮긴 파일을 옛 경로로 가리키는 것(#80). 규칙은 위 `SOURCE_EXTS` 절.
        #    `.md`는 코드 블록 밖에서 백틱 경로를, 코드 블록 안에서 코드 규칙을 본다(위 `FENCE` 절).
        if is_md:
            fences = fence_spans(text)
            hits = [m for m in MD_SOURCE_PATH.finditer(text)
                    if not any(a <= m.start() < b for a, b in fences)]
            for a, b in fences:
                hits += CODE_SOURCE_PATH.finditer(text, a, b)
        else:
            hits = list(CODE_SOURCE_PATH.finditer(text))
        for match in hits:
            token = match.group(1)
            if SOURCE_PLACEHOLDER.search(token) or BUILD_OUTPUT.search(token):
                continue
            if (rel, token) in SOURCE_ALLOWED:
                continue
            if source_resolves(token, rel, src_index, modules):
                source_ok += 1
                continue
            # ⚠️ 이력 서술 면제는 **토큰에 붙은 괄호 주석 안의 낱말만** 본다 — 위 `.md` 검사의 줄 전체
            #    규칙과 다르다(이유와 측정은 위 `SOURCE_ANNOTATION` 절). 면제한 것도 센다.
            line_start = text.rfind("\n", 0, match.start()) + 1
            line = line_of(text, match.start())
            if annotated_as_gone(line, match.end() - line_start):
                source_annotated += 1
                continue
            # 봉인·동결 문서와 갱신 이력 줄 — **못 푼 것만** 센다(위 봉인 규칙과 같은 이유).
            # ⚠️ 갱신 이력 줄(`CHANGELOG_LINE`)은 **면제가 아니라 봉인과 같은 취급**으로 바꿨다(2026-09-24).
            #    그 줄은 그때의 사실이라 옛 경로가 나오는 게 정상이고 고치지 않는다 — 봉인 문서와 같다. 그런데
            #    예전처럼 풀어 보기도 전에 건너뛰면 멀쩡한 경로도 죽은 경로도 **소리 없이** 사라진다.
            #    실측: 소스 토큰이 이력 줄에 3개(DOCS_INDEX.md:15·18·138) 있고, 위 ③ 이후 셋 다 풀린다.
            if is_sealed(rel) or rel in FROZEN_DOCS or CHANGELOG_LINE.match(line):
                source_frozen_skipped += 1
                continue
            row = (rel, text.count("\n", 0, match.start()) + 1, "문서" if is_md else "코드", token)
            if row not in source_broken:
                source_broken.append(row)

    sealed_note = f" · 봉인 문서 안의 옛 경로 {sealed_skipped}건" if sealed_skipped else ""
    if not broken:
        print(f"문서 링크 점검 통과 (허용 예외 {len(ALLOWED)}건{sealed_note})")
    else:
        print(f"깨진 참조 {len(broken)}건 (허용 예외 {len(ALLOWED)}건{sealed_note})")
        for src, kind, target in sorted(broken):
            print(f"  [{kind}] {src} → {target}")

    # ⚠️ 소스 경로는 **따로 한 줄** — 위 줄은 이 검사가 생기기 전과 글자 하나 다르지 않다.
    annotated_note = f" · 이력 주석이 붙은 옛 소스 경로 {source_annotated}건" if source_annotated else ""
    frozen_note = (f" · 봉인·동결 문서·갱신 이력 줄 안의 옛 소스 경로 {source_frozen_skipped}건"
                   if source_frozen_skipped else "")
    tally = f"실재 확인 {source_ok}건 · 허용 예외 {len(SOURCE_ALLOWED)}건{annotated_note}{frozen_note}"
    if not source_broken:
        print(f"소스 경로 점검 통과 ({tally})")
    else:
        print(f"깨진 소스 경로 {len(source_broken)}건 ({tally})")
        for src, lineno, kind, token in sorted(source_broken):
            # 고치는 사람을 위해 같은 이름의 파일이 지금 어디 있는지 붙인다(셋 이하일 때만).
            now = src_index.get(token.rsplit("/", 1)[-1], [])
            hint = f"  (같은 이름: {', '.join(now)})" if 0 < len(now) <= 3 else ""
            print(f"  [{kind}] {src}:{lineno} → {token}{hint}")
    return 1 if broken or source_broken else 0


if __name__ == "__main__":
    sys.exit(main())
