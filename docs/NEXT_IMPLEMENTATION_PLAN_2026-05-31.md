# 다음 구현 세부 플랜

작성일: 2026-05-31

## 목표

Android 앱에서 9x9 바둑 대국이 실제 바둑 규칙에 맞게 진행되도록 `shared` 도메인 규칙 projection을 먼저 안정화한다.

이번 작업의 핵심은 엔진 구현이 아니라 앱의 canonical game state다. KataGo는 AI 응수와 분석을 담당하고, 사석 제거/자살수/패 같은 보드 상태 계산은 `shared`가 담당한다.

## 진행 상태

| 단계 | 상태 | 내용 |
| --- | --- | --- |
| 1 | 완료 | 기존 `GameState.play()`와 테스트 범위 확인 |
| 2 | 완료 | `shared`에 liberties/group/capture/suicide/simple ko 구현 |
| 3 | 완료 | capture, multi-capture, suicide, ko unit test 추가 |
| 4 | 완료 | Android debug build로 UI 컴파일 영향 확인 |
| 5 | 완료 | 문서와 히스토리에 구현 결과/제약 반영 |
| 6 | 완료 | Gradle 검증 후 커밋/푸시 |

## 구현 원칙

- `app-android`는 바둑 규칙을 직접 계산하지 않는다.
- `engine-android`는 AI/엔진 통신만 담당한다.
- `shared`의 `GameState`가 move history와 board projection의 기준이다.
- KaTrain은 설계 참고만 하고 코드는 직접 복사하지 않는다.
- 처음부터 완전한 모든 룰을 만들기보다 앱 대국에 필요한 최소 규칙을 테스트로 고정한다.

## 1차 규칙 범위

- 상대 그룹 liberty가 0이면 capture
- 여러 돌 그룹 capture 지원
- suicide 금지
- capture로 인해 liberty가 생기는 착수는 suicide로 보지 않음
- immediate recapture를 막는 simple ko
- captured count 기록

## 후속 후보

- ruleset별 suicide/ko 정책 분리
- SGF import/export와 move replay
- debug build에서 KataGo `showboard`와 local projection 샘플 비교
- `GameState.play()` 결과에 captured coordinates를 포함하는 `MoveApplication` DTO 추가

## 2차 계획: 개발 명령과 엔진 artifact guard

목표는 새 환경 셋업과 인수인계 상황에서 “엔진 산출물이 없는데 앱 빌드를 시도하는 문제”를 명확한 메시지로 막는 것이다.

| 단계 | 상태 | 내용 |
| --- | --- | --- |
| 1 | 완료 | `Makefile` 추가: `doctor`, `test`, `dev`, `dev-stub`, `prebuild-engine`, `release` |
| 2 | 완료 | debug/release engine artifact 존재 여부 guard 추가 |
| 3 | 완료 | `.gitignore`에 engine artifact 폴더 정책 명시 |
| 4 | 완료 | `make doctor`, `make test`, `make dev`, `make release` guard 동작 확인 |
| 5 | 완료 | 문서/히스토리 갱신 후 커밋/푸시 |

## 진행 메모

- `GameState.play()`가 착수 후 상대 인접 그룹의 liberties를 계산하고, liberty가 없는 그룹을 `stones`에서 제거하도록 변경했다.
- suicide move는 금지한다. 단 capture로 liberty가 생기는 착수는 합법으로 처리한다.
- immediate recapture를 막는 simple ko 상태를 `koPoint`, `koForbiddenFor`로 저장한다.
- 잡은 돌 수는 `capturedByBlack`, `capturedByWhite`로 기록한다.
- Android UI는 `GameState.stones`를 직접 그리므로, capture 후 제거된 돌은 별도 UI 로직 없이 board에 반영된다.
- 현재 사람-vs-AI 흐름에서는 특정 capture 수순을 화면에서 재현하기 어렵기 때문에, 1차 검증은 shared unit test와 Android compile check를 기준으로 했다.
- `Makefile`을 추가해 새 환경에서 `make doctor`, `make test`, `make dev`, `make dev-stub`, `make prebuild-engine`, `make release`로 진입할 수 있게 했다.
- `make dev`는 debug engine artifact가 없으면 `make prebuild-engine` 또는 `make dev-stub` 안내와 함께 중단한다.
- `make release`는 release engine artifact가 없으면 release artifact 준비 흐름을 안내하고 중단한다.

## 검증 결과

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:testDebugUnitTest
```

성공.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:assembleDebug :app-android:testDebugUnitTest
```

성공.

최종 clean 검증:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew clean :shared:check :app-android:assembleDebug :app-android:testDebugUnitTest
```

성공. 증분 컴파일 캐시 경고 없이 clean build가 통과했다.

Makefile 검증:

```sh
make doctor
make test
make dev
make release
```

`make doctor`, `make test`, `make dev`는 성공했다. `make release`는 release engine artifact가 없는 현재 상태에서 의도대로 안내 메시지를 출력하고 실패했다.

## 3차 계획: 사석 제거 수동 검증 모드

목표는 사용자가 앱 화면에서 일부러 포획 상황을 만들고 사석 제거 동작을 직접 확인할 수 있게 하는 것이다.

| 단계 | 상태 | 내용 |
| --- | --- | --- |
| 1 | 완료 | 앱에 `AI 대국` / `2P 테스트` 모드 추가 |
| 2 | 완료 | 2P 모드에서 엔진 준비 상태와 무관하게 흑백 번갈아 착수 가능하도록 변경 |
| 3 | 완료 | 잡은 돌 수 표시 추가 |
| 4 | 완료 | AI thinking 중 모드 전환 race 방지 |
| 5 | 완료 | Gradle 빌드/테스트 확인 |
| 6 | 완료 | 에뮬레이터 설치/실행 확인 |

## 3차 진행 메모

- `2P 테스트` 모드는 `GameState.play()`만 사용하므로 엔진 상태와 무관하게 사석 제거, suicide, simple ko를 화면에서 확인할 수 있다.
- `AI 대국` 모드는 기존처럼 사람 Black, 엔진 White 흐름을 유지한다.
- Engine tuning과 Analyze는 AI 대국 모드에서만 활성화한다.
- 화면 상태 영역에 `Captured by Black / White`를 표시한다.
- 첫 설치 시도는 연결된 디바이스가 없어 실패했다.
- `Pixel_7_API_35`를 verbose/headless 옵션으로 실행하니 정상 부팅했다.
- 이후 기존 앱을 제거해 `/data` 여유 공간을 확보했고, 최신 APK 설치와 앱 실행을 확인했다.

## 3차 검증 결과

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:check :app-android:assembleDebug :app-android:testDebugUnitTest
```

성공.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:installDebug
```

실패. 원인은 `No connected devices!`이며, 빌드 문제가 아니다.

후속 재시도:

```sh
/Users/ryan9kim/Library/Android/sdk/emulator/emulator -avd Pixel_7_API_35 -verbose -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-load -log-nofilter
/Users/ryan9kim/Library/Android/sdk/platform-tools/adb uninstall com.worksoc.goaicoach
/Users/ryan9kim/Library/Android/sdk/platform-tools/adb install -r app-android/build/outputs/apk/debug/app-android-debug.apk
/Users/ryan9kim/Library/Android/sdk/platform-tools/adb shell am start -W -n com.worksoc.goaicoach/.MainActivity
```

성공. 첫 설치 재시도는 `/data` 여유 공간 부족으로 실패했으나, 기존 앱 제거 후 설치/실행이 완료됐다.
