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

사용법: `python3 scripts/check-doc-links.py` (저장소 루트에서). 깨진 것이 있으면 종료코드 1.
"""
from __future__ import annotations

import os
import re
import sys

SKIP_DIRS = {".git", "build", ".gradle", "worktrees", ".claude", "node_modules", "dist"}

# 참조 표기 정책의 **유일한 예외 두 곳**(2026-08-31 사용자 확정).
# `DOCS_INDEX.md`는 **지도**이고 `HANDOVER.md`는 **새로 오는 사람의 첫 화면**이라, 위치를 말하지
# 않으면 그 두 문서가 제 역할을 못 한다. 나머지 전부는 파일명만 쓴다.
EXEMPT = {"docs/DOCS_INDEX.md", "docs/HANDOVER.md"}

# 이름만으로는 문서를 식별하지 못하는 파일들 — 여러 폴더에 같은 이름이 있다.
# 이런 문서는 정책의 예외로 **폴더까지** 적는다(`feature-access-principles/README.md`).
GENERIC_NAMES = {"README.md", "summary.md", "index.md"}

# ⚠️ **삭제된 문서를 "삭제됐다"고 적는 문장은 깨진 참조가 아니다.** 이 저장소는 보존 정책상
# 문서를 지우는 것이 정상이고, 지운 사실을 본문에 남기는 관행이 이미 있다
# (예: "전체 비교 근거 문서(`STACK_DECISION.md`)는 2026-08-17 …삭제됐다").
# 그래서 **맨 파일명이 있는 줄**에 아래 낱말이 있으면 의도된 서술로 보고 넘긴다.
#
# ⚠️ 이것은 **어림짐작이다** — 우연히 같은 줄에서 "삭제"를 말하는 진짜 사장된 이름은 놓친다.
# 그 오차를 받아들이는 이유: 반대 방향(정상 서술을 매번 깨진 것으로 보고)은 도구를 못 쓰게 만들고,
# 못 쓰는 검사는 결국 꺼진다. 정확히 잡아야 하는 건은 ALLOWED에 개별로 적는다.
REMOVAL_WORDS = ("삭제", "제거", "removed", "deleted", "아카이브", "git 히스토리")

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
    # ⓒ 스크립트가 만들어 낼 출력 경로
    ("scripts/run-katago-candidate-refine-experiment.py",
     "docs/engine/measurements/engine-benchmark/candidate-refine-latest.md"):
        "--out 기본값(실행하면 생성되는 산출물)",
}

# 문서가 표기 형태 자체를 설명할 때 쓰는 자리표시자 — 실재하는 파일이 아니다.
PLACEHOLDER = re.compile(r"(FILE\.md|<[^>]+>\.md)")

BARE_NAME = re.compile(r"`([A-Za-z_][A-Za-z0-9_.-]*\.md)`")
MD_LINK = re.compile(r"\]\(([^)\s#]+\.md)(?:#[^)]*)?\)")
ROOT_PATH = re.compile(r"`((?:docs|scripts|shared|app-android|engine-android)/[^`\s]+\.md)`")
CODE_PATH = re.compile(r"docs/[A-Za-z0-9_\-/]+\.md")
CODE_EXTS = (".kt", ".kts", ".py", ".sh")


def walk(root: str):
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            yield os.path.join(base, name)


def main() -> int:
    root = os.getcwd()
    broken: list[tuple[str, str, str]] = []
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

        found: list[tuple[str, str]] = []
        if is_md:
            for match in MD_LINK.finditer(text):
                target = match.group(1)
                if target.startswith(("http", "mailto")):
                    continue
                found.append(("link", os.path.relpath(
                    os.path.normpath(os.path.join(os.path.dirname(path), target)), root)))
            found += [("path", m.group(1)) for m in ROOT_PATH.finditer(text)]
        else:
            found += [("code", m.group(0)) for m in CODE_PATH.finditer(text)]

        for kind, target in found:
            if PLACEHOLDER.search(target):
                continue
            if (rel, target) in ALLOWED:
                continue
            if not os.path.exists(os.path.join(root, target)):
                row = (rel, kind, target)
                if row not in broken:
                    broken.append(row)

        if not is_md:
            continue

        # ⚠️ 파일명 표기 정책의 사각지대 — 없는 문서를 이름으로 부르는 것.
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
        if rel not in EXEMPT:
            for match in MD_LINK.finditer(text):
                target = match.group(1)
                if target.startswith(("http", "mailto")) or PLACEHOLDER.search(target):
                    continue
                row = (rel, "링크 대신 파일명만 쓸 것(정책)", target)
                if row not in broken:
                    broken.append(row)

    if not broken:
        print(f"문서 링크 점검 통과 (허용 예외 {len(ALLOWED)}건)")
        return 0
    print(f"깨진 참조 {len(broken)}건")
    for src, kind, target in sorted(broken):
        print(f"  [{kind}] {src} → {target}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
