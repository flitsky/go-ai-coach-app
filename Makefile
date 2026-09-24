SHELL := /bin/bash

ANDROID_HOME ?= /Users/ryan9kim/Library/Android/sdk
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 17 2>/dev/null)
GRADLEW := ./gradlew

# Target device selection:
# - TARGET: preset keyword ('emu' or 'phone')
# - ANDROID_SERIAL: exact adb serial (or auto-resolved from connected devices)
#
# When multiple devices are attached, doctor will guide you to specify TARGET or ANDROID_SERIAL.
# If a configured ANDROID_SERIAL is disconnected and exactly 1 other device is available,
# it will automatically fall back to the active device.
TARGET ?=
ANDROID_SERIAL ?=

# Auto-resolve target device serial when possible
RESOLVED_SERIAL := $(shell TARGET="$(TARGET)" ANDROID_SERIAL="$(ANDROID_SERIAL)" ANDROID_HOME="$(ANDROID_HOME)" ./scripts/resolve-target-device.sh --get-serial 2>/dev/null)
ifneq ($(strip $(RESOLVED_SERIAL)),)
ANDROID_SERIAL := $(RESOLVED_SERIAL)
export ANDROID_SERIAL
else ifneq ($(strip $(ANDROID_SERIAL)),)
export ANDROID_SERIAL
endif

# app-android/build.gradle.kts에서 직접 읽어온다 — 하드코딩하면 applicationId가 바뀔 때마다
# (예: Firebase 패키지명 정정) adb 타겟이 옛 패키지를 계속 가리키는 채로 조용히 어긋난다.
# MainActivity 컴포넌트명은 namespace 기준이다 — applicationId(설치 패키지)와
# namespace(Kotlin 패키지/R·BuildConfig 생성 위치)가 서로 다를 수 있기 때문에 둘 다 필요하다.
APP_PACKAGE := $(shell grep -m1 'applicationId = ' app-android/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
APP_NAMESPACE := $(shell grep -m1 'namespace = ' app-android/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')

ENGINE_ABI ?= arm64-v8a
DEBUG_ENGINE_BINARY := app-android/src/debug/jniLibs/$(ENGINE_ABI)/libkatago.so
# ⚠️ **이름이 `FRIEND_`인 것은 역사다 — 지금 이것은 "번들에 싣는 엔진 에셋"이다.**
# friend 빌드타입은 2026-09-06에 없어졌지만(마켓 오픈으로 지인 배포 채널의 역할이 끝났다) 이
# 경로는 **release·playInternal AAB의 엔진 공급원**이라 그대로 남는다.
# ⚠️ **개명하지 말 것** — `BundledEngineAssetContractTest`가 이 **변수 이름 자체를** 정규식으로
# 읽어 "APK 안에 들어갈 이름"을 만든다. 바꾸면 그 계약이 깨진다.
FRIEND_ASSET_DIR := app-android/src/friend/assets/katago
PLAY_INTERNAL_AAB := dist/go-ai-coach-play-internal.aab
RELEASE_AAB := dist/go-ai-coach-release.aab
FRIEND_MODEL_PATH ?= /opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz
FRIEND_CONFIG_PATH ?= /Users/ryan9kim/worksoc/katago/config/katago/gtp_learning.cfg
FRIEND_ANALYSIS_CONFIG_PATH ?= /Users/ryan9kim/worksoc/katago/config/katago/analysis_learning.cfg
ENGINE_MATCH_GAMES ?= 50
ENGINE_MATCH_OUT ?= docs/engine/measurements/engine-match/matrix-20260610
ENGINE_MATCH_ARGS ?=
ENGINE_DEVICE_BENCHMARK_SAMPLES ?= 10
ENGINE_DEVICE_BENCHMARK_OUT ?= docs/engine/measurements/engine-benchmark/mac-20260610
ENGINE_DEVICE_BENCHMARK_ARGS ?=
ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES ?= 5
ENGINE_SEARCH_MODE_BENCHMARK_OUT ?= docs/engine/measurements/engine-benchmark/search-mode-mac-20260613
ENGINE_SEARCH_MODE_BENCHMARK_ARGS ?=
ENGINE_PHONE_BENCHMARK_SERIAL ?= $(ANDROID_SERIAL)
ENGINE_PHONE_SEARCH_MODE_BENCHMARK_OUT ?= docs/engine/measurements/engine-benchmark/search-mode-phone-latest
ENGINE_PHONE_SEARCH_MODE_BENCHMARK_ARGS ?= --time-cap-ms 10000

# release/play-internal-aab/bundle-aab 실행 시 `make bundle-aab VERSION=0.2.0`처럼 넘기면
# version.properties의 VERSION_NAME이 그 값으로 바뀐다. 비워두면(기본값) 패치 자리만 1
# 증가한다 — 두 경우 모두 VERSION_CODE는 항상 1 증가한다(scripts/bump-version.sh 참고).
VERSION ?=

export ANDROID_HOME
export JAVA_HOME

.DEFAULT_GOAL := help

.PHONY: help doctor test test-ios test-device test-remote-analysis-server dev dev-stub install-dev install-dev-engine reinstall-dev-engine seed-engine launch play-internal-aab bundle-aab verify-admob-keys bump-version prepare-friend-assets engine-level-benchmark engine-device-benchmark engine-search-mode-benchmark engine-search-mode-benchmark-phone release ensure-debug-engine prebuild-engine clean

help:
	@echo "=========================================================================="
	@echo "  Go AI Coach - Makefile Commands"
	@echo "=========================================================================="
	@echo ""
	@echo " [Development & Installation]"
	@echo "  make dev                 - Build Debug APK (requires debug engine binary)"
	@echo "  make dev-stub            - Build Debug APK in Stub-only mode"
	@echo "  make install-dev         - Build & Install Debug APK to target device"
	@echo "  make install-dev-engine  - Build/Install Debug APK + Seed KataGo model + Launch app"
	@echo "                             (Supports TARGET=emu or TARGET=phone, or ANDROID_SERIAL=<serial>)"
	@echo "  make reinstall-dev-engine- Uninstall app, re-install, seed model & launch"
	@echo "  make seed-engine         - Seed KataGo model & configs to target device"
	@echo "  make launch              - Force-stop & launch app on target device"
	@echo ""
	@echo " [Environment & Testing]"
	@echo "  make doctor              - Check JDK 17, ANDROID_HOME, and adb target device"
	@echo "                             (Supports TARGET=emu/phone or auto-resolution)"
	@echo "  make test                - Run unit tests for shared, engine, and app modules"
	@echo "  make test-ios            - Compile-only iOS targets check (see 함정 75; NOT part of make test)"
	@echo "  make test-device         - Run instrumented (androidTest) smoke tests on TARGET=emu/phone"
	@echo "                             (needs a connected device; NOT part of make test, NOT wired into make release)"
	@echo "  make test-remote-analysis-server - Check run-katago-remote-analysis-server.py's query builder"
	@echo "                             (no KataGo/device needed; NOT part of make test — see Makefile comment)"
	@echo ""
	@echo " [Build & Engine Prebuild]"
	@echo "  make play-internal-aab   - Build release-signed AAB (debug engine + bundled assets) for Play Console internal testing"
	@echo "  make bundle-aab          - Build release-signed AAB (real release engine + assets) for production Play Console upload"
	@echo "  make verify-admob-keys   - Check local.properties has real AdMob keys (release/bundle-aab run this first)"
	@echo "  make prebuild-engine     - Compile native KataGo engine binary (libkatago.so)"
	@echo "  make release             - Assemble Release APK"
	@echo "                             (release/play-internal-aab/bundle-aab all bump version.properties first;"
	@echo "                              pass VERSION=x.y.z to set an exact version, otherwise the patch digit +1)"
	@echo "  make clean               - Clean Gradle build outputs"
	@echo ""
	@echo " [Benchmarks]"
	@echo "  make engine-level-benchmark            - Run KataGo level matrix benchmarks"
	@echo "  make engine-device-benchmark           - Run device benchmarks"
	@echo "  make engine-search-mode-benchmark      - Run search mode benchmarks on local machine"
	@echo "  make engine-search-mode-benchmark-phone- Run search mode benchmarks on phone"
	@echo "=========================================================================="

doctor:
	@echo "Checking local Android development environment..."
	@test -n "$(JAVA_HOME)" || (echo "JDK 17 not found. Install JDK 17 or set JAVA_HOME." && exit 1)
	@test -x "$(JAVA_HOME)/bin/java" || (echo "JAVA_HOME does not point to a valid JDK: $(JAVA_HOME)" && exit 1)
	@test -d "$(ANDROID_HOME)" || (echo "ANDROID_HOME does not exist: $(ANDROID_HOME)" && exit 1)
	@test -x "$(ANDROID_HOME)/platform-tools/adb" || (echo "adb not found under ANDROID_HOME/platform-tools." && exit 1)
	@test -x "$(GRADLEW)" || (echo "Gradle wrapper is missing or not executable." && exit 1)
	@TARGET="$(TARGET)" ANDROID_SERIAL="$(ANDROID_SERIAL)" ANDROID_HOME="$(ANDROID_HOME)" ./scripts/resolve-target-device.sh --doctor
	@if [ -n "$(ANDROID_SERIAL)" ]; then \
		if "$(ANDROID_HOME)/platform-tools/adb" shell pm path $(APP_PACKAGE) >/dev/null 2>&1; then \
			if ! "$(ANDROID_HOME)/platform-tools/adb" shell run-as $(APP_PACKAGE) test -s files/katago/model.bin.gz >/dev/null 2>&1; then \
				echo ""; \
				echo "⚠️  KataGo model not seeded on the connected device/emulator for $(APP_PACKAGE)."; \
				echo "   The app will silently fall back to the stub AI (instant, non-KataGo moves)"; \
				echo "   until you run: make seed-engine"; \
				echo "   (uninstall/reinstall wipes app files, so re-run after those too.)"; \
				echo ""; \
			fi; \
		fi; \
	fi
	@echo "JAVA_HOME=$(JAVA_HOME)"
	@echo "ANDROID_HOME=$(ANDROID_HOME)"
	@echo "Environment check passed."

# ⚠️ `:app-android:compileDebugAndroidTestKotlin`은 **컴파일만** 한다(기기 불필요, 약 5초).
# 계기 테스트를 돌리려는 게 아니라 **그 소스셋이 썩는 것을 막으려는 것**이다 — 실제로
# 2026-08-30부터 6일간 컴파일조차 되지 않는 채로 방치됐고(`GoCoachApp`에 필수 파라미터
# `engineMode`가 늘었는데 호출부 둘을 안 고침), 이 명령이 그 트리를 건드리지 않아 아무도 몰랐다.
test: doctor
	$(GRADLEW) :shared:check :engine-android:testDebugUnitTest :app-android:assembleDebug :app-android:testDebugUnitTest :app-android:compileDebugAndroidTestKotlin :app-android:lintDebug

# ⚠️ 별도 타깃이다 — `test`에 합치지 마라(refactor backlog #11, 함정 75).
# iOS 타깃은 `shared/build.gradle.kts`의 `enableIosTargets` 게이트 뒤에 숨어 있어(기본 false)
# 평소 `make test`는 이 코드를 전혀 컴파일하지 않는다. 2026-08-24에 정확히 이 사각지대로
# commonMain에 iOS에 없는 API가 49개 컴파일 에러로 쌓였는데도 모든 초록불이 켜져 있었다.
# 그렇다고 `test`에 합치면 안 되는 이유: **iOS는 아무것도 출하하지 않는 타깃**이라, 안드로이드
# 릴리스가 출하하지 않는 타깃의 컴파일 실패로 막히면 안 된다(그 타깃이 gitignore된 개인
# 실험이거나, 아직 손대는 사람이 없는 코드일 수도 있다). 그래서 "존재는 확인하되 릴리스를
# 막지는 않는" 별도 타깃으로 둔다 — 필요할 때 사람이 직접 돌려서 사각지대를 스스로 확인한다.
# compileKotlinIosSimulatorArm64만 돌리는 이유: 컴파일만으로 충분하다(시뮬레이터 부팅이나
# Xcode 프로젝트가 필요 없다) — 여기서 잡으려는 것은 "iOS에 없는 API를 commonMain에 썼다"는
# 사실 자체지, 실제 iOS 런타임 동작이 아니다. 세 iOS 타깃(iosX64/iosArm64/iosSimulatorArm64)
# 전부를 컴파일할 필요는 없다 — commonMain 코드가 플랫폼별로 갈라지지 않는 한(현재 없음)
# 셋 다 같은 expect/actual 집합을 보므로 하나만 컴파일해도 API 누락은 동일하게 드러난다.
test-ios:
	$(GRADLEW) :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true

# ⚠️ 별도 타깃이다 — `test`에 합치지 마라(refactor backlog #13ⓑ). `make test`가 지키는 "빠른
# 루프"(에뮬레이터/실기기 없이 몇 초~몇 분 안에 결과)를 계기 테스트가 깨뜨린다 — 에뮬레이터
# 부팅·앱 설치·Compose idle sync를 기다려야 해서 몇 배는 느리고, 기기가 아예 안 붙어 있으면
# `doctor`에서부터 막힌다(함정: doctor는 기기 둘 이상이면 Error 1 — TARGET=emu로 하나만
# 골라라). `app-android/src/androidTest`의 세 스모크 테스트(AppLaunchSmokeTest 등)를 돈다.
#
# `make release`/`make bundle-aab`에 자동으로 물리지 않는 이유: 그 타깃들은 지금도 unit
# test(`test`)를 선행 조건으로 두지 않는다(release/bundle-aab의 의존 그래프 참고 — doctor →
# verify-admob-keys → bump-version → ensure-debug-engine → prepare-friend-assets뿐이다).
# 계기 테스트만 새로 강제하면 "unit test는 안 막는데 계기 테스트는 막는다"는 일관성 없는
# 규칙이 생기고, 무엇보다 릴리스를 만드는 기계에 항상 에뮬레이터/기기가 붙어 있다는 보장이
# 없다 — 없으면 출하 자체가 막힌다. 사람이 릴리스 전에 직접 돌리는 수동 단계로 남긴다.
test-device: doctor
	$(GRADLEW) :app-android:connectedDebugAndroidTest

# ⚠️ 별도 타깃이다 — `test`에 합치지 마라(refactor backlog #90). `scripts/run-katago-remote-analysis-server.py`는
# dev-only 스파이크다 — debug 빌드에 REMOTE_ENGINE_URL이 있을 때만 쓰이고(`app-android/build.gradle.kts`:
# debug만 값을 채우고 release·playInternal은 빈 문자열), 프로덕션 사용자·앱 배포 어느 쪽에도 닿지 않는다.
# `make test`(릴리스 게이트)는 JDK+Android SDK만 있으면 도는 것이 지금까지의 전제였고(`doctor`가 그 둘만
# 확인한다), 여기에 python3을 새 필수 의존으로 얹으면 그 전제가 이 스크립트 하나 때문에 깨진다 — 얻는
# 것(파이썬 스크립트 버그 조기 발견)에 비해 잃는 것(모든 릴리스 빌드 환경에 python3 가용성 요구)이 크다.
# `engine-android`의 JVM 테스트에서 `subprocess`로 python3을 부르는 안도 기각했다 — 그건 이 문제를
# `test-ios`/`test-device`처럼 별도 타깃으로 빼는 대신 `make test`가 이미 도는 스위트 **안에** 몰래 심는
# 것이라 같은 문제를 우회로만 옮긴다. 대신 이 스크립트를 고칠 때 사람이 직접 돌리는 수동 게이트로 둔다 —
# 변경 빈도가 낮고(dev 스파이크), KataGo 바이너리 없이도 0.01초 안에 돈다.
test-remote-analysis-server:
	python3 scripts/test_run_katago_remote_analysis_server.py -v

dev: doctor ensure-debug-engine
	$(GRADLEW) :app-android:assembleDebug

dev-stub: doctor
	$(GRADLEW) :app-android:assembleDebug

install-dev: dev
	$(GRADLEW) :app-android:installDebug

install-dev-engine: install-dev seed-engine launch

reinstall-dev-engine: doctor ensure-debug-engine
	-$(ANDROID_HOME)/platform-tools/adb uninstall $(APP_PACKAGE)
	$(GRADLEW) :app-android:installDebug
	$(MAKE) seed-engine
	$(MAKE) launch

seed-engine: doctor
	PACKAGE="$(APP_PACKAGE)" ANDROID_HOME="$(ANDROID_HOME)" ./scripts/seed-katago-model-to-app.sh

launch: doctor
	$(ANDROID_HOME)/platform-tools/adb shell am force-stop $(APP_PACKAGE)
	$(ANDROID_HOME)/platform-tools/adb shell am start -W -n $(APP_PACKAGE)/$(APP_NAMESPACE).MainActivity

# version.properties의 VERSION_CODE를 1 증가시킨다(Play Console은 한 번 올린 versionCode를
# 절대 재사용할 수 없다 — "이미 사용된 버전 코드" 오류를 구조적으로 방지). VERSION_NAME은
# VERSION= 인자가 있으면 그 값, 없으면 패치 자리만 1 증가한다. release/play-internal-aab/
# bundle-aab 셋 다 이 타겟을 거쳐 항상 최신 미사용 버전으로 빌드된다.
bump-version:
	@./scripts/bump-version.sh "$(VERSION)"

# playInternal은 friend와 같은 debug KataGo 엔진/에셋을 재사용하지만 release keystore로
# 서명한다(PREMIUM_MODE.md Step 4 후속 — Play Console 인앱 상품 등록에는 결제 권한이
# 포함된 서명된 빌드 업로드가 선행 조건). local.properties의 release.* 키가 없으면 서명 없이
# 빌드되어 이 타겟이 실패한다.
play-internal-aab: doctor bump-version ensure-debug-engine prepare-friend-assets
	$(GRADLEW) :app-android:bundlePlayInternal
	@mkdir -p dist
	@cp app-android/build/outputs/bundle/playInternal/app-android-playInternal.aab "$(PLAY_INTERNAL_AAB)"
	@ls -lh "$(PLAY_INTERNAL_AAB)"
	@shasum -a 256 "$(PLAY_INTERNAL_AAB)"

# play-internal-aab과 달리 실제 release 빌드 타입을 그대로 번들링한다 — local.properties의 실제
# AdMob/Play Billing 값을 쓴다는 점이 다르지만, KataGo 엔진/모델 에셋은 friend/playInternal과
# 동일하게 이미 검증된 debug 엔진을 재사용한다(별도 "release 전용" 엔진을 새로 준비하지 않기로
# 결정, app-android/build.gradle.kts의 release sourceSets 참고). Play Console 정식 공개
# 출시(프로덕션 트랙) 업로드용 — 내부 테스트 트랙에는 빠른 반복이 필요하므로 계속
# play-internal-aab을 쓴다.
# ⚠️ **verify-admob-keys가 bump-version보다 먼저다**(백로그 #90). 순서를 바꾸지 말 것 —
# bump-version은 `version.properties`를 **고쳐 쓰므로**, 키가 없어 Gradle이 나중에 실패하면
# 올리지도 못한 버전만 올라간 채 남는다. 검사를 앞에 두면 아무것도 건드리기 전에 멈춘다.
bundle-aab: doctor verify-admob-keys bump-version ensure-debug-engine prepare-friend-assets
	$(GRADLEW) :app-android:bundleRelease
	@mkdir -p dist
	@cp app-android/build/outputs/bundle/release/app-android-release.aab "$(RELEASE_AAB)"
	@ls -lh "$(RELEASE_AAB)"
	@shasum -a 256 "$(RELEASE_AAB)"

# 실제 AdMob 키가 준비됐는지만 본다. ⚠️ **규칙을 여기 옮겨 적지 말 것** — 정본은
# `app-android/build.gradle.kts`의 `verifyReleaseAdmobKeys`이고, 두 곳에 적으면 반드시 어긋난다.
verify-admob-keys:
	$(GRADLEW) :app-android:verifyReleaseAdmobKeys

engine-level-benchmark:
	python3 scripts/run-katago-level-matrix.py --games-per-matchup "$(ENGINE_MATCH_GAMES)" --out-dir "$(ENGINE_MATCH_OUT)" $(ENGINE_MATCH_ARGS)

engine-device-benchmark:
	python3 scripts/run-katago-device-benchmark.py --samples "$(ENGINE_DEVICE_BENCHMARK_SAMPLES)" --out-dir "$(ENGINE_DEVICE_BENCHMARK_OUT)" $(ENGINE_DEVICE_BENCHMARK_ARGS)

engine-search-mode-benchmark:
	python3 scripts/run-katago-search-mode-benchmark.py --samples "$(ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES)" --out-dir "$(ENGINE_SEARCH_MODE_BENCHMARK_OUT)" $(ENGINE_SEARCH_MODE_BENCHMARK_ARGS)

engine-search-mode-benchmark-phone:
	@test -n "$(ENGINE_PHONE_BENCHMARK_SERIAL)" || (echo "Set ENGINE_PHONE_BENCHMARK_SERIAL=<adb serial> or ANDROID_SERIAL=<adb serial>." && exit 2)
	python3 scripts/run-katago-search-mode-benchmark.py --samples "$(ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES)" --out-dir "$(ENGINE_PHONE_SEARCH_MODE_BENCHMARK_OUT)" --adb-serial "$(ENGINE_PHONE_BENCHMARK_SERIAL)" $(ENGINE_PHONE_SEARCH_MODE_BENCHMARK_ARGS)

prepare-friend-assets:
	@test -f "$(FRIEND_MODEL_PATH)" || (echo "Friend APK model not found: $(FRIEND_MODEL_PATH)" && exit 1)
	@test -f "$(FRIEND_CONFIG_PATH)" || (echo "Friend APK config not found: $(FRIEND_CONFIG_PATH)" && exit 1)
	@test -f "$(FRIEND_ANALYSIS_CONFIG_PATH)" || (echo "Friend APK analysis config not found: $(FRIEND_ANALYSIS_CONFIG_PATH)" && exit 1)
	@rm -rf "$(FRIEND_ASSET_DIR)"
	@mkdir -p "$(FRIEND_ASSET_DIR)"
	@cp "$(FRIEND_MODEL_PATH)" "$(FRIEND_ASSET_DIR)/model.bin.gz"
	@cp "$(FRIEND_CONFIG_PATH)" "$(FRIEND_ASSET_DIR)/gtp_learning.cfg"
	@cp "$(FRIEND_ANALYSIS_CONFIG_PATH)" "$(FRIEND_ASSET_DIR)/analysis_learning.cfg"
	@echo "Prepared friend APK assets in $(FRIEND_ASSET_DIR)"

# ⚠️ `bundle-aab`과 같은 이유로 verify-admob-keys가 bump-version보다 앞이다(백로그 #90).
release: doctor verify-admob-keys bump-version ensure-debug-engine prepare-friend-assets
	$(GRADLEW) :app-android:assembleRelease

ensure-debug-engine:
	@test -f "$(DEBUG_ENGINE_BINARY)" || ( \
		echo "Missing debug engine artifact: $(DEBUG_ENGINE_BINARY)"; \
		echo "Run 'make prebuild-engine' to build the pinned local artifact, or use 'make dev-stub' for stub-only UI work."; \
		exit 2; \
	)

prebuild-engine: doctor
	ANDROID_HOME="$(ANDROID_HOME)" ./scripts/build-katago-android-spike.sh
	@test -f "$(DEBUG_ENGINE_BINARY)" || (echo "Engine prebuild finished but debug artifact was not created: $(DEBUG_ENGINE_BINARY)" && exit 1)
	@echo "Debug engine artifact ready: $(DEBUG_ENGINE_BINARY)"

clean:
	$(GRADLEW) clean
