# Android KataGo Spike Runbook

이 문서는 Android 에뮬레이터에서 실제 KataGo process adapter를 검증한 절차와 재현 명령을 기록한다.

## 검증 결론

- Android NDK `arm64-v8a`용 KataGo v1.16.4 Eigen(CPU) backend 빌드 성공.
- 빌드 산출물은 Android ELF 64-bit PIE 실행 파일이며, strip 후 약 4.6MB.
- 에뮬레이터 shell에서는 `/data/local/tmp` 실행이 가능했고, GTP `Black E5 -> White C5` smoke test도 성공.
- 실제 앱 프로세스에서는 app private data의 실행 파일 실행이 SELinux `execute_no_trans`로 거부됨.
- 따라서 앱에서 process 방식으로 실행하려면 executable을 app data가 아니라 APK native library 영역에 넣고 extract되게 해야 한다.
- `app-android`는 `nativeLibraryDir/libkatago.so`가 있고 `files/katago/model.bin.gz`, `files/katago/gtp_learning.cfg`가 있을 때 `KataGoProcessEngineAdapter`를 선택한다.
- `libkatago.so`를 `app-android/src/debug/jniLibs/arm64-v8a/`에 임시 포함하고 model/config를 app files에 seed한 뒤, Android 앱 UI에서 실제 KataGo 대국 루프를 확인했다.

## 분리 원칙

이 문서의 빌드 절차는 제품 앱 개발 루프가 아니라 spike 재현 절차다.

장기적으로 앱 repo는 KataGo source tree와 native build cache를 소유하지 않는다. 엔진 빌드는 별도 artifact pipeline에서 수행하고, 앱은 확정된 binary/model/config 산출물을 Gradle task나 release artifact fetch 단계로 참조한다.

앱 코드의 의존 방향은 다음으로 고정한다.

- `shared`: 바둑 규칙, move history, engine DTO/interface
- `engine-android`: `EngineAdapter` 구현체와 Android 실행 방식 캡슐화
- `app-android`: UI와 lifecycle, engine 구현 세부사항 비의존

따라서 `scripts/build-katago-android-spike.sh`는 초기 검증용이며, 엔진 결정이 안정화되면 별도 repo/CI/artifact 생성 절차로 이동하는 것이 바람직하다.

## 빌드 명령

```sh
ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./scripts/build-katago-android-spike.sh
```

이 스크립트는 다음을 수행한다.

- KataGo v1.16.4 source tarball 다운로드
- Eigen 3.4.0 다운로드
- Android NDK `27.1.12297006`, ABI `arm64-v8a`, platform `android-26`으로 CMake configure
- `USE_BACKEND=EIGEN`, `NO_GIT_REVISION=1`, Android little-endian define 적용
- `katago` target build
- strip 후 `app-android/src/debug/jniLibs/arm64-v8a/libkatago.so` 생성

`*.so`는 `.gitignore` 대상이다. repo에는 빌드 스크립트와 문서만 추적하고, native binary는 로컬 생성 산출물로 둔다.

## 앱에 model/config seed

앱이 한 번 이상 설치되어 있어야 `run-as com.worksoc.goaicoach`가 동작한다.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk \
./gradlew :app-android:installDebug

ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./scripts/seed-katago-model-to-app.sh
```

기본 model/config 경로:

- model: `/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz`
- config: `/Users/ryan9kim/worksoc/katago/config/katago/gtp_learning.cfg`

다른 파일을 쓰려면 환경변수로 바꿀 수 있다.

```sh
MODEL_PATH=/path/to/model.bin.gz \
CONFIG_PATH=/path/to/gtp.cfg \
ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk \
./scripts/seed-katago-model-to-app.sh
```

## 앱 실행 검증

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk \
./gradlew :app-android:installDebug

/Users/ryan9kim/Library/Android/sdk/platform-tools/adb shell am force-stop com.worksoc.goaicoach
/Users/ryan9kim/Library/Android/sdk/platform-tools/adb shell am start -W -n com.worksoc.goaicoach/.MainActivity
```

성공 시 화면 상단 문구가 다음처럼 보인다.

```text
9x9 match: human Black vs KataGo White
```

확인된 앱 내 실제 응답:

```text
KataGo accepted Black E5
KataGo generated White C5
KataGo process response: C5
```

## 직접 GTP smoke test

에뮬레이터 shell 기준:

```sh
printf 'boardsize 9\nkomi 6.5\nclear_board\nplay B E5\ngenmove W\nquit\n' |
/Users/ryan9kim/Library/Android/sdk/platform-tools/adb shell run-as com.worksoc.goaicoach \
  files/katago/katago gtp \
  -model files/katago/model.bin.gz \
  -config files/katago/gtp_learning.cfg \
  -override-config maxVisits=1,numSearchThreads=1,logDir=files/katago/logs,homeDataDir=files/katago/home,logToStderr=false,logAllGTPCommunication=false,logSearchInfo=false,allowResignation=false,startupPrintMessageToStderr=false
```

주의: 위 직접 GTP smoke test는 `run-as` 셸 검증용이다. 실제 앱 프로세스에서는 app data의 executable 실행이 막히므로 `nativeLibraryDir/libkatago.so` packaging 경로를 사용해야 한다.

## 발견한 문제와 조치

- 문제: app private data에 둔 executable은 실제 앱 프로세스 `ProcessBuilder`에서 `Permission denied`로 실패.
  - 조치: process adapter 자동 선택 경로를 `applicationInfo.nativeLibraryDir/libkatago.so`로 변경.
- 문제: `pointerInput(gameState, inputEnabled)`가 착수마다 재시작되어 한 번의 tap이 여러 착수로 증폭될 수 있음.
  - 조치: gesture key를 `gameState.boardSize, inputEnabled`로 축소.
- 문제: process 초기화 실패가 앱 crash로 이어질 수 있음.
  - 조치: 초기화 실패를 화면 메시지로 표시하고 입력을 비활성화.

## 다음 작업

1. `libkatago.so`를 수동 복사하지 않고 Gradle task로 생성/검증하는 흐름 정리.
2. model/config seed를 앱 내부 debug 화면 또는 Gradle/ADB task로 단순화.
3. `KataGoProcessEngineAdapter`에 command timeout, process cleanup, stderr capture 추가.
4. `kata-analyze` streaming parser는 genmove loop 안정화 뒤 별도 구현.
