# KataGo 엔진 연동 Spike 계획

이 문서는 현재 stub AI 대국 POC 다음 단계로, 실제 KataGo 엔진을 `EngineAdapter` 뒤에 붙이기 위한 검증 계획입니다.

## 목표

Android 앱에서 9x9 대국 중 다음 흐름을 실제 엔진으로 검증한다.

1. 앱이 엔진 adapter를 초기화한다.
2. 9x9 새 판을 엔진에 전달한다.
3. 사람 Black 착수를 엔진에 전달한다.
4. 엔진이 White 응수를 생성한다.
5. 앱이 응수를 보드에 반영한다.

## 우선 접근

1차는 JNI가 아니라 process 기반 adapter로 검증한다.

이유:

- `EngineAdapter` 경계를 유지하면서 transport만 교체할 수 있다.
- stdin/stdout command loop를 먼저 검증하면 UI, DTO, timeout, lifecycle 문제를 빠르게 볼 수 있다.
- JNI는 Android packaging과 ABI, native crash 디버깅 부담이 더 크므로 process 방식이 실패하거나 한계를 확인한 뒤 선택하는 편이 낫다.

## Android Packaging 검증 포인트

Android에서는 desktop처럼 임의 위치에 복사한 실행 파일을 항상 실행할 수 있다고 가정하면 위험하다.

확인할 항목:

1. `arm64-v8a`용 KataGo artifact를 어떻게 빌드할지.
2. 실행 파일을 APK/AAB에 어떤 형태로 포함할지.
3. `ProcessBuilder`가 실제 기기에서 해당 artifact를 실행할 수 있는지.
4. model 파일을 assets, app-specific storage, 다운로드 캐시 중 어디에 둘지.
5. 앱 업데이트 시 engine/model version mismatch를 어떻게 감지할지.
6. 엔진 프로세스 종료, timeout, 앱 background 전환 시 cleanup이 안정적인지.

주의:

- assets에서 app data directory로 복사한 뒤 chmod로 실행하는 방식은 최신 Android에서 막히거나 배포 정책과 충돌할 수 있다.
- process 방식이 불안정하면 JNI/native library 방식 또는 remote fallback을 유지해야 한다.

## Adapter 설계 초안

`engine-android` 안에 다음 구현체를 추가한다.

```text
EngineAdapter
  - StubEngineAdapter
  - KataGoProcessEngineAdapter
```

`KataGoProcessEngineAdapter` 책임:

- `initialize`: 실행 파일 및 model 경로 확인, process 시작
- `configure`: 난이도 profile, visits, time limit, model/server mode 설정 반영
- `newGame`: 9x9 board/rules 초기화 명령 전달
- `playMove`: 사람 착수를 엔진 notation으로 변환해 전달
- `genMove`: White 응수 요청 후 결과를 `MoveResult`로 변환
- `analyze`: 제한 visits/time으로 후보 수 반환
- `stop`: 진행 중 분석 중단 및 process 종료

## 최소 검증 시나리오

1. 앱 시작 후 엔진 초기화 성공 메시지를 표시한다.
2. New game 버튼으로 9x9 새 판을 만든다.
3. 중앙에 Black 착수한다.
4. 엔진이 White 응수를 반환한다.
5. 앱을 background로 보냈다가 돌아와도 process 상태가 깨지지 않는다.
6. New game을 여러 번 눌러도 process가 누수되지 않는다.
7. 엔진이 응답하지 않을 때 timeout 후 UI가 다시 조작 가능 상태로 돌아온다.
8. 대국 중 profile 또는 visits 변경 시 다음 `genMove` / `analyze` 요청부터 새 설정이 적용된다.

## 2026-05-31 진행 결과

- macOS Homebrew KataGo 확인:
  - binary: `/opt/homebrew/bin/katago`
  - version: `KataGo v1.16.4`
  - backend: Metal
  - model: `/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz`
- KaTrain venv 내장 항목 확인:
  - binary: `/Users/ryan9kim/.local/pipx/venvs/katrain/lib/python3.12/site-packages/katrain/KataGo/katago`
  - 이 binary는 Linux x86-64 ELF라 현재 macOS/Android 실행 대상에 직접 적합하지 않음.
  - model: `/Users/ryan9kim/.local/pipx/venvs/katrain/lib/python3.12/site-packages/katrain/models/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz`
- desktop GTP smoke test 성공:
  - `boardsize 9`
  - `komi 6.5`
  - `clear_board`
  - `play B E5`
  - `genmove W`
  - 응답: `C5`
- `engine-android`에 `KataGoProcessEngineAdapter` skeleton을 추가함.
- 1차 adapter skeleton 범위:
  - `initialize`
  - `configure`
  - `newGame`
  - `playMove`
  - `genMove`
  - `stop`
- `analyze`는 아직 미지원으로 명시했다. `kata-analyze`는 스트리밍 parser와 cancellation path가 필요하므로 별도 작업으로 분리한다.

## 1차 결론

process adapter 방향은 desktop smoke test 기준으로 타당하다.
다만 Android 앱에서 실행하려면 Android용 `arm64-v8a` KataGo artifact와 model/config packaging 전략이 먼저 필요하다.
현재 확인된 Homebrew binary는 macOS Mach-O라 Android에서 실행할 수 없다.

## 2026-05-31 Android 실행 spike 결과

- KataGo v1.16.4 source와 Eigen 3.4.0으로 Android NDK `arm64-v8a` 빌드 성공.
- CMake configure 조건:
  - `USE_BACKEND=EIGEN`
  - `NO_GIT_REVISION=1`
  - `ANDROID_PLATFORM=android-26`
  - `BYTE_ORDER/LITTLE_ENDIAN/BIG_ENDIAN` define 명시 필요
- strip 후 실행 파일 크기: 약 4.6MB.
- `/data/local/tmp`에 executable/model/config를 올린 뒤 GTP smoke test 성공:
  - `boardsize 9`
  - `komi 6.5`
  - `play B E5`
  - `genmove W`
  - 응답: `C5`
- app private files에 둔 executable은 실제 앱 프로세스에서 SELinux `execute_no_trans`로 거부됨.
- debug `jniLibs/arm64-v8a/libkatago.so`로 packaging하고 `useLegacyPackaging=true`로 native library를 추출하면 앱 프로세스에서 실행 가능함을 확인.
- Android 앱 화면에서 실제 KataGo adapter가 선택되어 `Black E5 -> White C5` 흐름을 확인함.

## 갱신된 결론

`목표 6번까지 마무리 후 엔진 빌드/연동 테스트`라는 순서는 적절했다.

이유:

- stub skeleton이 먼저 있었기 때문에 실제 engine transport를 UI/도메인에 섞지 않고 `KataGoProcessEngineAdapter`만 추가할 수 있었다.
- 엔진 성능 조절 UI도 이미 `EngineAdapter.configure()` 뒤에 있어, KataGo의 `kata-set-param maxVisits/maxTime`으로 자연스럽게 연결됐다.
- 실제 Android 제약은 “빌드 가능 여부”보다 “어디에 executable을 둘 것인가”였고, 이 문제는 skeleton 이후에 spike하는 편이 더 빨랐다.

현재 기준 다음 리스크는 build 자체가 아니라 packaging/lifecycle이다.

## 보류할 항목

아래 항목은 process adapter가 실제 기기에서 돌아가는지 확인한 뒤 진행한다.

- JNI wrapper
- model 다운로드/업데이트 UX
- strength profile과 visits/time tuning
- 13x13/19x19 확장
- SGF import/export
- capture/ko/scoring의 완전한 로컬 룰 구현
