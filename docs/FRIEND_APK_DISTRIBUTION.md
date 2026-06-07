# 친구 전달용 엔진 포함 APK

작성일: 2026-06-02

## 목적

개발용 `make seed-engine` 없이 APK 설치만으로 KataGo 엔진이 동작하는 임시 debug APK를 만든다.

이 방식은 마켓 릴리즈 방식이 아니라 지인 테스트용 sideload 산출물이다. 정식 릴리즈는 AAB와 Play Asset Delivery 또는 release assets packaging으로 별도 정리한다.

## 현재 산출물

```text
/Users/ryan9kim/worksoc/go-ai-coach/dist/go-ai-coach-katago-friend.apk
```

현재 빌드 크기:

```text
105MB
```

현재 SHA-256:

```text
c005bb35c5d726a2762b0b33a5e13008ea933934558243913839091089e7bdc9
```

## 빌드 명령

일반 개발 빌드는 모델을 포함하지 않는다.

```sh
make dev
```

외부 전달용 APK가 필요할 때만 다음 명령을 사용한다.

```sh
make friend-apk
```

`make friend-apk`는 로컬 model/config/analysis config를 `app-android/src/friend/assets/katago/`로 복사한 뒤 `:app-android:assembleFriend`를 실행한다. 따라서 평소 `assembleDebug` / `make dev`는 100MB 모델 asset을 처리하지 않는다.

## APK에 포함된 엔진 산출물

```text
lib/arm64-v8a/libkatago.so
assets/katago/model.bin
assets/katago/gtp_learning.cfg
assets/katago/analysis_learning.cfg
```

주의: source asset은 `model.bin.gz`이지만 Android asset merge 과정에서 APK 내부 이름은 `assets/katago/model.bin`으로 들어간다. 앱 bootstrap은 이 파일을 첫 실행 시 `files/katago/model.bin`으로 복사한 뒤 KataGo process adapter에 넘긴다.

## 첫 실행 동작

앱 시작 시 다음 순서로 엔진 산출물을 확인한다.

1. `nativeLibraryDir/libkatago.so` 실행 가능 여부 확인
2. 기존 개발 seed 파일 `files/katago/model.bin.gz` 확인
3. 번들 APK에서 복사한 `files/katago/model.bin` 확인
4. `files/katago/gtp_learning.cfg` 확인
5. `files/katago/analysis_learning.cfg` 확인
6. 누락된 파일이 있고 APK assets에 있으면 첫 실행 시 app files로 복사
7. 준비가 끝나면 `KataGoProcessEngineAdapter`를 선택

첫 실행 때 100MB 안팎의 모델 파일을 복사하므로 잠시 준비 화면이 보일 수 있다.

## 설치 명령

폰에서 USB debugging이 켜져 있고 ADB에 잡힌 상태:

```sh
adb install -r /Users/ryan9kim/worksoc/go-ai-coach/dist/go-ai-coach-katago-friend.apk
adb shell am start -W -n com.worksoc.goaicoach/.MainActivity
```

기존 개발 seed나 오래된 앱 데이터 영향을 피하려면:

```sh
adb uninstall com.worksoc.goaicoach
adb install -r /Users/ryan9kim/worksoc/go-ai-coach/dist/go-ai-coach-katago-friend.apk
adb shell am start -W -n com.worksoc.goaicoach/.MainActivity
```

## 검증 포인트

앱 상단 Mode panel에 다음 진단 문구가 보이면 실제 로컬 KataGo process 경로다.

```text
KataGo assets found. Using local process engine.
Seeded bundled asset katago/model.bin.
Seeded bundled asset katago/gtp_learning.cfg.
Seeded bundled asset katago/analysis_learning.cfg.
```

이미 한 번 복사된 뒤에는 `Seeded bundled asset...` 줄이 보이지 않을 수 있다.

## 제한사항

- debug signing APK이므로 Play Store 배포용이 아니다.
- 현재 APK는 `arm64-v8a` 엔진만 포함한다. 일반적인 최신 Android 폰은 대부분 동작하지만, 32-bit only 기기에서는 동작하지 않는다.
- APK 내부 asset과 앱 내부 copied model이 동시에 존재하므로 설치 후 기기 저장공간을 추가로 사용한다.
- 최소 여유 공간은 APK 105MB, 모델 복사본 약 101MB, 설치 임시공간을 고려해 넉넉히 300MB 이상을 권장한다.
