# 다음 작업 스케줄: KataGo Process Adapter Spike

이 문서는 현재 stub AI 대국 POC 이후 실제 KataGo 연동을 향한 다음 작업 스케줄과 진행 상태를 기록합니다.

## 현재 앱 실행 상태

- 2026-05-31: `Pixel_7_API_35` 에뮬레이터에 최신 debug APK 설치 및 실행 확인.
- 화면 확인 결과:
  - 9x9 보드 표시 정상
  - 사람 Black vs stub AI White 대국 UI 표시 정상
  - 엔진 profile / visits 실시간 조정 UI 표시 정상
  - 하단 엔진 메시지는 화면 높이에 따라 스크롤해서 확인 가능

## 다음 작업 방향

다음 큰 작업은 `KataGo process adapter spike` 수행이 맞다.

목표는 바로 완성형 엔진 packaging을 만드는 것이 아니라, Android 앱의 `EngineAdapter` 뒤에서 실제 KataGo와 통신 가능한지 빠르게 검증하는 것이다.

## 스케줄

| 순서 | 상태 | 작업 | 산출물 |
| --- | --- | --- | --- |
| 1 | 완료 | 최신 Android 앱 실행 확인 | 에뮬레이터 설치/실행 및 화면 캡처 확인 |
| 2 | 완료 | 로컬 KataGo 워크스페이스 조사 | config는 `/Users/ryan9kim/worksoc/katago/config/katago`, 실행 binary는 Homebrew `/opt/homebrew/bin/katago`, model은 Homebrew Cellar에 있음 |
| 3 | 완료 | desktop/local KataGo command smoke test | Homebrew KataGo v1.16.4로 9x9 GTP smoke test 성공. `Black E5` 이후 `White C5` 생성 확인 |
| 4 | 완료 | Android process 실행 가능성 정리 | Android `arm64-v8a` KataGo Eigen 빌드 및 에뮬레이터 GTP smoke test 성공. 단, app data executable은 SELinux로 차단되어 native library packaging 필요 |
| 5 | 완료 | `KataGoProcessEngineAdapter` 설계 세분화 | 실행 파일 경로, config/model 경로, command queue, 기본 GTP command flow를 코드 skeleton으로 반영 |
| 6 | 완료 | 필요 시 adapter skeleton 추가 | `KataGoProcessEngineAdapter`와 `KataGoProcessConfig` 추가. 기본 앱은 계속 stub 사용 |
| 7 | 완료 | 빌드/테스트 검증 | Gradle 검증 성공 |
| 8 | 완료 | 실제 앱 process adapter packaging spike | `libkatago.so`를 debug `jniLibs`에 임시 포함하고 model/config를 app files에 seed해 Android UI에서 `Black E5 -> White C5` 확인 |
| 9 | 진행 중 | 재현 스크립트/문서화 | `scripts/build-katago-android-spike.sh`, `scripts/seed-katago-model-to-app.sh`, `docs/ANDROID_KATAGO_SPIKE_RUNBOOK.md` 추가 |
| 10 | 예정 | process adapter 안정화 | timeout, stderr capture, lifecycle cleanup, Gradle task화 |

## 판단 기준

- 로컬 macOS KataGo binary와 model/config가 있으면 먼저 desktop smoke test를 한다.
- Android용 artifact가 없으면 바로 Android 실행을 시도하지 않고, build 필요 항목을 문서화한다.
- 앱 코드에는 transport 세부사항을 직접 넣지 않고, 반드시 `EngineAdapter` 구현체로 격리한다.
- process 방식이 Android packaging 제약에 막힐 가능성을 계속 열어두고, JNI/native library 및 remote fallback을 대안으로 유지한다.

## 진행 메모

- 2026-05-31: 사용자가 “다음 작업은 스파이크 플랜 수행인가”라고 질문함. 답은 “예, 다음 큰 작업은 KataGo process adapter spike”로 판단함.
- 2026-05-31: 최신 Android 앱을 에뮬레이터에 설치/실행하고 화면 캡처로 확인함.
- 2026-05-31: `/Users/ryan9kim/worksoc/katago`에는 config와 KaTrain 스크립트가 있고, 실행 가능한 KataGo binary/model은 Homebrew 및 KaTrain venv 경로에 있음을 확인함.
- 2026-05-31: 사용할 수 있는 macOS binary는 `/opt/homebrew/bin/katago`, model은 `/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz`.
- 2026-05-31: `printf 'boardsize 9 ... genmove W' | katago gtp ...` 형태의 desktop smoke test 성공. 응답은 `= C5`.
- 2026-05-31: `engine-android`에 `KataGoProcessEngineAdapter` skeleton을 추가함. 현재 앱 runtime 기본값은 안전하게 `StubEngineAdapter` 유지.
- 2026-05-31: `:shared:check :app-android:assembleDebug :app-android:testDebugUnitTest` 성공.
- 2026-05-31 22:06 KST: Android NDK `arm64-v8a` KataGo v1.16.4 Eigen(CPU) 빌드 성공.
- 2026-05-31 22:06 KST: Android 에뮬레이터에서 `/data/local/tmp` 실행 파일과 model/config로 GTP `Black E5 -> White C5` smoke test 성공.
- 2026-05-31 22:06 KST: app private files의 executable은 실제 앱 프로세스에서 SELinux `execute_no_trans`로 차단됨을 확인.
- 2026-05-31 22:06 KST: `nativeLibraryDir/libkatago.so` packaging 경로로 실제 Android 앱 UI에서 KataGo 응수 생성 확인.
- 2026-05-31 22:06 KST: 한 번의 tap이 여러 착수로 증폭될 수 있는 Compose gesture key 문제를 수정함.

## 현재 1차 판단

- 스파이크 플랜 수행이 다음 작업이라는 판단은 맞았다.
- macOS desktop smoke test뿐 아니라 Android `arm64-v8a` build와 에뮬레이터 GTP smoke test도 성공했다.
- 앱에서 실제 process adapter를 켜려면 executable은 app data가 아니라 native library 영역으로 packaging해야 한다.
- model/config는 우선 app files seed 방식으로 충분하다. 단, 배포용으로는 다운로드/검증/버전관리 UX가 필요하다.
- KataGo process 방식은 POC 검증용으로 타당하다. 다만 Play 배포와 장기 안정성까지 고려하면 JNI/native library 방식도 계속 후보로 유지해야 한다.

## 바로 다음 액션 후보

1. process adapter의 timeout/cancel/stderr capture를 보강.
2. 현재 수동 `libkatago.so` 생성 과정을 Gradle task 또는 명확한 developer workflow로 정리.
3. debug-only model/config seed 경로를 더 단순화.
4. `kata-analyze` 스트리밍 parser는 genmove 안정화 뒤 별도 작업으로 분리.
