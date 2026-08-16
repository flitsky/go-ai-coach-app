#!/usr/bin/env python3
"""GameSessionStateHolder -> :shared 이전 작업 전용 도구.

application/ 파일을 shared/commonMain으로 옮긴 뒤, 컴파일 에러가 지목하는
internal 선언만 골라 public으로 넓히는 과정을 자동으로 수렴시킨다.
docs/refactoring/GAMESESSION_SHARED_MIGRATION_KICKOFF_PLAN_260816_1808.md
의 방법론(6절)을 그대로 구현한 것 — 사전 감사가 아니라 컴파일러가 실제로
지목하는 것만 고치고, 그린이 될 때까지 반복한다.

사용법 (각 웨이브 파일 이동 직후, 반드시 이 순서로 3번 실행):
    python3 scripts/converge-shared-migration-visibility.py shared
    python3 scripts/converge-shared-migration-visibility.py app-main
    python3 scripts/converge-shared-migration-visibility.py app-test

세 타깃 각각 별도로 internal 위젠이 필요할 수 있다(:shared 자체 컴파일은
모듈 내부라 관대하고, app-android 메인/테스트가 실제 모듈 경계를 넘는
지점이라 거기서 대부분 걸린다) — 하나가 그린이라고 다음 것도 그린이라고
가정하지 말 것.

알려진 한계(수동 확인 필요한 경우, 계획서 7·9절 참고):
- "Unresolved reference 'X'"만 나오고 위젠 대상이 안 잡히면, X가
  application/ 트리 밖(다른 최상위 패키지, 예: middleware/)의 아직 안
  옮긴 파일을 가리킬 수 있다 — 그 파일이 이식 가능한지 확인 후 함께 이전.
- "Smart cast to 'X' is impossible, because 'Y' is a public API property
  declared in different module" 에러는 internal 문제가 아니라 Kotlin의
  크로스모듈 스마트캐스트 제약 — 지역 val로 캡처해서 해결(코드 자체 수정
  필요, 이 스크립트로는 못 잡음).
- 테스트 파일이 "Unresolved reference"를 내면 그 테스트가 아직 안 옮긴
  다른 production 파일까지 테스트하고 있을 수 있다 — 그 테스트만 원위치로
  되돌릴 것(make test로 확인).
"""
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

TARGETS = {
    "shared": ":shared:compileDebugKotlinAndroid",
    "app-main": ":app-android:compileDebugKotlin",
    "app-test": ":app-android:compileDebugUnitTestKotlin",
}

ROOT = REPO_ROOT / "shared/src/commonMain/kotlin/com/worksoc/goaicoach/application"

# "fun interface"(SAM) 선언을 kw 그룹에서 bare "fun"보다 먼저 시도하도록
# 명시하지 않으면, mods 알터네이션이 "fun interface"를 통째로 수정자로
# 잘못 먹어버려 이름을 못 찾는다(260816 웨이브 3에서 발견).
DECL_LINE_RE = re.compile(
    r"^(?P<indent>\s*)"
    r"(?P<pre>(?:@\w+(?:\([^)]*\))?\s*)*)"
    r"(?P<mods>(?:public|internal|private|protected|abstract|open|sealed|data|enum|annotation|inline|noinline|crossinline|suspend|external|expect|actual|final|const|tailrec|value)\s+)*"
    r"(?P<kw>class|interface|object|fun\s+interface|fun|val|var|typealias)\s+"
    r"(?P<recv>(?:<[^>]*>\s+)?(?:[\w.<>,? ]+\.)?)?"
    r"(?P<name>\w+)"
)

SIG_NAME_RE = re.compile(
    r"(?:class|interface|object|typealias)\s+(\w+)"
    r"|"
    r"fun\s+(?:<[^>]*>\s*)?(?:[\w<>,.? ]+\.)?(\w+)\s*[(<]"
    r"|"
    r"(?:val|var)\s+(?:<[^>]*>\s*)?(?:[\w<>,.? ]+\.)?(\w+)\s*:"
)


def widen(symbols):
    changed = []
    for f in sorted(ROOT.rglob("*.kt")):
        text = f.read_text()
        lines = text.split("\n")
        file_changed = False
        for i, line in enumerate(lines):
            m = DECL_LINE_RE.match(line)
            if not m or m.group("indent") != "":
                continue
            if m.group("name") not in symbols:
                continue
            # m.group("mods")는 반복 그룹의 마지막 항목만 남으므로(파이썬 re의
            # 함정) "internal" 존재 여부는 kw 앞의 원문에서 직접 검사한다.
            prefix_text = line[: m.start("kw")]
            if not re.search(r"\binternal\b", prefix_text):
                continue
            new_line = re.sub(r"\binternal\s+", "", line, count=1)
            if new_line != line:
                lines[i] = new_line
                file_changed = True
                changed.append((str(f.relative_to(REPO_ROOT)), i + 1, line.strip(), new_line.strip()))
        if file_changed:
            f.write_text("\n".join(lines))
    return changed


def compile_target(gradle_task):
    r = subprocess.run(
        ["./gradlew", gradle_task],
        capture_output=True, text=True, timeout=300, cwd=REPO_ROOT,
    )
    return r.returncode == 0, r.stdout + r.stderr


def extract_symbols(log):
    names = set()
    for m in re.finditer(r"Cannot access '([^']+)'", log):
        sm = SIG_NAME_RE.search(m.group(1))
        if sm:
            names.add(sm.group(1) or sm.group(2) or sm.group(3))
    for m in re.finditer(
        r"exposes its 'internal'[a-z ]*(?:type argument|type containing declaration|return type|parameter type|type) '([^']+)'",
        log,
    ):
        names.add(m.group(1))
    return names


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in TARGETS:
        print(f"usage: {sys.argv[0]} <{'|'.join(TARGETS)}>", file=sys.stderr)
        sys.exit(1)
    gradle_task = TARGETS[sys.argv[1]]

    max_rounds = 20
    history = []
    for round_no in range(1, max_rounds + 1):
        ok, log = compile_target(gradle_task)
        if ok:
            print(f"ROUND {round_no}: BUILD SUCCESSFUL ({gradle_task})")
            break
        symbols = extract_symbols(log)
        unresolved_refs = set(re.findall(r"Unresolved reference '([^']+)'", log))
        error_count = log.count("\ne: ")
        print(
            f"ROUND {round_no}: {error_count} errors, {len(symbols)} visibility symbols, "
            f"{len(unresolved_refs)} unresolved-reference names (see docstring: may be an "
            f"out-of-application-tree dependency or a test needing to be reverted)"
        )
        if not symbols:
            print("  No widenable symbols found — stopping, needs manual look. Sample errors:")
            for line in log.splitlines():
                if line.startswith("e:"):
                    print("   ", line)
            break
        changed = widen(symbols)
        print(f"  widened {len(changed)} declaration(s):")
        for fp, ln, old, new in changed:
            print(f"    {fp}:{ln}\n      - {old}\n      + {new}")
        if not changed:
            print("  Symbols found but none matched a top-level internal declaration (likely")
            print("  member-level, or a Kotlin construct this script's regex doesn't parse yet).")
            print("  Unmatched symbols:", sorted(symbols))
            break
        history.append((round_no, error_count, len(changed)))
    else:
        print(f"Stopped after {max_rounds} rounds without convergence — needs manual look.")

    if history:
        print("\n=== round summary (round, error_count_before, symbols_widened) ===")
        for h in history:
            print(" ", h)


if __name__ == "__main__":
    main()
