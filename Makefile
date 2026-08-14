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
FRIEND_ASSET_DIR := app-android/src/friend/assets/katago
FRIEND_APK := dist/go-ai-coach-katago-friend.apk
PLAY_INTERNAL_AAB := dist/go-ai-coach-play-internal.aab
RELEASE_AAB := dist/go-ai-coach-release.aab
FRIEND_MODEL_PATH ?= /opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz
FRIEND_CONFIG_PATH ?= /Users/ryan9kim/worksoc/katago/config/katago/gtp_learning.cfg
FRIEND_ANALYSIS_CONFIG_PATH ?= /Users/ryan9kim/worksoc/katago/config/katago/analysis_learning.cfg
ENGINE_MATCH_GAMES ?= 50
ENGINE_MATCH_OUT ?= docs/engine-match-logs/matrix-20260610
ENGINE_MATCH_ARGS ?=
ENGINE_DEVICE_BENCHMARK_SAMPLES ?= 10
ENGINE_DEVICE_BENCHMARK_OUT ?= docs/engine-benchmark-logs/mac-20260610
ENGINE_DEVICE_BENCHMARK_ARGS ?=
ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES ?= 5
ENGINE_SEARCH_MODE_BENCHMARK_OUT ?= docs/engine-benchmark-logs/search-mode-mac-20260613
ENGINE_SEARCH_MODE_BENCHMARK_ARGS ?=
ENGINE_PHONE_BENCHMARK_SERIAL ?= $(ANDROID_SERIAL)
ENGINE_PHONE_SEARCH_MODE_BENCHMARK_OUT ?= docs/engine-benchmark-logs/search-mode-phone-latest
ENGINE_PHONE_SEARCH_MODE_BENCHMARK_ARGS ?= --time-cap-ms 10000

# release/play-internal-aab/bundle-aab 실행 시 `make bundle-aab VERSION=0.2.0`처럼 넘기면
# version.properties의 VERSION_NAME이 그 값으로 바뀐다. 비워두면(기본값) 패치 자리만 1
# 증가한다 — 두 경우 모두 VERSION_CODE는 항상 1 증가한다(scripts/bump-version.sh 참고).
VERSION ?=

export ANDROID_HOME
export JAVA_HOME

.DEFAULT_GOAL := help

.PHONY: help doctor test dev dev-stub install-dev install-dev-engine reinstall-dev-engine seed-engine launch friend-apk play-internal-aab bundle-aab bump-version prepare-friend-assets engine-level-benchmark engine-device-benchmark engine-search-mode-benchmark engine-search-mode-benchmark-phone release ensure-debug-engine prebuild-engine clean

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
	@echo ""
	@echo " [Build & Engine Prebuild]"
	@echo "  make friend-apk          - Build Friend APK with bundled KataGo assets"
	@echo "  make play-internal-aab   - Build release-signed AAB (friend/debug assets) for Play Console internal testing"
	@echo "  make bundle-aab          - Build release-signed AAB (real release engine + assets) for production Play Console upload"
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

test: doctor
	$(GRADLEW) :shared:check :engine-android:testDebugUnitTest :app-android:assembleDebug :app-android:testDebugUnitTest

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

friend-apk: doctor ensure-debug-engine prepare-friend-assets
	$(GRADLEW) :app-android:assembleFriend
	@mkdir -p dist
	@cp app-android/build/outputs/apk/friend/app-android-friend.apk "$(FRIEND_APK)"
	@ls -lh "$(FRIEND_APK)"
	@shasum -a 256 "$(FRIEND_APK)"

# version.properties의 VERSION_CODE를 1 증가시킨다(Play Console은 한 번 올린 versionCode를
# 절대 재사용할 수 없다 — "이미 사용된 버전 코드" 오류를 구조적으로 방지). VERSION_NAME은
# VERSION= 인자가 있으면 그 값, 없으면 패치 자리만 1 증가한다. release/play-internal-aab/
# bundle-aab 셋 다 이 타겟을 거쳐 항상 최신 미사용 버전으로 빌드된다.
bump-version:
	@./scripts/bump-version.sh "$(VERSION)"

# playInternal은 friend와 같은 debug KataGo 엔진/에셋을 재사용하지만 release keystore로
# 서명한다(premium-mode/README.md Step 4 후속 — Play Console 인앱 상품 등록에는 결제 권한이
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
bundle-aab: doctor bump-version ensure-debug-engine prepare-friend-assets
	$(GRADLEW) :app-android:bundleRelease
	@mkdir -p dist
	@cp app-android/build/outputs/bundle/release/app-android-release.aab "$(RELEASE_AAB)"
	@ls -lh "$(RELEASE_AAB)"
	@shasum -a 256 "$(RELEASE_AAB)"

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

release: doctor bump-version ensure-debug-engine prepare-friend-assets
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
