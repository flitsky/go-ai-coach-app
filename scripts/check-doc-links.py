#!/usr/bin/env python3
"""문서가 가리키는 경로가 실재하는지 확인한다.

`docs/DOCS_INDEX.md`의 **문서 구조 정책** 절이 폴더를 옮길 때 요구하는 마지막 단계
("문서가 가리키는 파일이 실재하는지 기계로 재확인한다")를 손으로 하지 않게 만든 것이다.
백로그 #58에서 이 저장소는 **재편 한 번에 깨진 참조가 32건까지 늘어나는** 것을 겪었고,
그중 상대 링크 18건은 눈으로는 잡히지 않았다.

무엇을 보는가:
  - 모든 `.md`의 마크다운 링크 `](경로.md)` — 상대 경로를 파일 위치 기준으로 푼다
  - 모든 `.md`의 백틱 경로 `` `docs/...md` `` 등 저장소 루트 기준 표기
  - 코드·스크립트·`Makefile` 안의 `docs/....md` 문자열 (주석이 문서를 지목하는 경우)

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

# (참조하는 파일, 가리키는 경로) → 사유
ALLOWED: dict[tuple[str, str], str] = {
    # ⓐ 삭제 사실을 적은 히스토리 서술
    ("docs/DOCS_INDEX.md", "docs/archive/2026-08-06-refactoring-log-consolidation/README.md"):
        "2026-08-17 보존 정책 전환으로 삭제한 것을 기록한 문장",
    ("docs/DOCS_INDEX.md", "docs/working-260617/implementation_plan.md"):
        "2026-08-06에 삭제한 것을 기록한 문장",
    ("docs/history/THREAD_HISTORY.md", "docs/KATRAIN_UX_BACKLOG.md"):
        "그 시점에 있었던 문서를 가리키는 히스토리 서술",
    ("scripts/run-katago-candidate-refine-experiment.py",
     "docs/archive/2026-06-docs-consolidation/ENGINE_BEGINNER_VISITS_BENCHMARK.md"):
        "옛 아카이브 위치를 적어 둔 주석(사실 서술)",
    # ⓑ 아직 만들지 않은 제안 문서
    ("docs/spec/APP_IA_AND_UI_SPEC.md", "docs/spec/UI_DESIGN_TOKENS.md"): "추천 신규 제안(미작성)",
    ("docs/spec/APP_IA_AND_UI_SPEC.md", "docs/spec/SGF_AND_REVIEW_MODE_SPEC.md"): "추천 신규 제안(미작성)",
    ("docs/spec/APP_IA_AND_UI_SPEC.md", "docs/spec/USER_ONBOARDING_GUIDE.md"): "추천 신규 제안(미작성)",
    # ⓒ 스크립트가 만들어 낼 출력 경로
    ("scripts/run-katago-candidate-refine-experiment.py",
     "docs/measurements/engine-benchmark/candidate-refine-latest.md"):
        "--out 기본값(실행하면 생성되는 산출물)",
}

# 문서가 표기 형태 자체를 설명할 때 쓰는 자리표시자 — 실재하는 파일이 아니다.
PLACEHOLDER = re.compile(r"(FILE\.md|<[^>]+>\.md)")

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

    if not broken:
        print(f"문서 링크 점검 통과 (허용 예외 {len(ALLOWED)}건)")
        return 0
    print(f"깨진 참조 {len(broken)}건")
    for src, kind, target in sorted(broken):
        print(f"  [{kind}] {src} → {target}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
