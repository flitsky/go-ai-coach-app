SHELL := /bin/bash

ANDROID_HOME ?= /Users/ryan9kim/Library/Android/sdk
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 17 2>/dev/null)
GRADLEW := ./gradlew

ENGINE_ABI ?= arm64-v8a
DEBUG_ENGINE_BINARY := app-android/src/debug/jniLibs/$(ENGINE_ABI)/libkatago.so
RELEASE_ENGINE_BINARY := app-android/src/main/jniLibs/$(ENGINE_ABI)/libkatago.so
FRIEND_ASSET_DIR := app-android/src/friend/assets/katago
FRIEND_APK := dist/go-ai-coach-katago-friend.apk
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

export ANDROID_HOME
export JAVA_HOME

.PHONY: doctor test dev dev-stub install-dev install-dev-engine reinstall-dev-engine seed-engine launch friend-apk prepare-friend-assets engine-level-benchmark engine-device-benchmark engine-search-mode-benchmark release ensure-debug-engine ensure-release-engine prebuild-engine clean

doctor:
	@echo "Checking local Android development environment..."
	@test -n "$(JAVA_HOME)" || (echo "JDK 17 not found. Install JDK 17 or set JAVA_HOME." && exit 1)
	@test -x "$(JAVA_HOME)/bin/java" || (echo "JAVA_HOME does not point to a valid JDK: $(JAVA_HOME)" && exit 1)
	@test -d "$(ANDROID_HOME)" || (echo "ANDROID_HOME does not exist: $(ANDROID_HOME)" && exit 1)
	@test -x "$(ANDROID_HOME)/platform-tools/adb" || (echo "adb not found under ANDROID_HOME/platform-tools." && exit 1)
	@test -x "$(GRADLEW)" || (echo "Gradle wrapper is missing or not executable." && exit 1)
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
	-$(ANDROID_HOME)/platform-tools/adb uninstall com.worksoc.goaicoach
	$(GRADLEW) :app-android:installDebug
	$(MAKE) seed-engine
	$(MAKE) launch

seed-engine: doctor
	ANDROID_HOME="$(ANDROID_HOME)" ./scripts/seed-katago-model-to-app.sh

launch: doctor
	$(ANDROID_HOME)/platform-tools/adb shell am force-stop com.worksoc.goaicoach
	$(ANDROID_HOME)/platform-tools/adb shell am start -W -n com.worksoc.goaicoach/.MainActivity

friend-apk: doctor ensure-debug-engine prepare-friend-assets
	$(GRADLEW) :app-android:assembleFriend
	@mkdir -p dist
	@cp app-android/build/outputs/apk/friend/app-android-friend.apk "$(FRIEND_APK)"
	@ls -lh "$(FRIEND_APK)"
	@shasum -a 256 "$(FRIEND_APK)"

engine-level-benchmark:
	python3 scripts/run-katago-level-matrix.py --games-per-matchup "$(ENGINE_MATCH_GAMES)" --out-dir "$(ENGINE_MATCH_OUT)" $(ENGINE_MATCH_ARGS)

engine-device-benchmark:
	python3 scripts/run-katago-device-benchmark.py --samples "$(ENGINE_DEVICE_BENCHMARK_SAMPLES)" --out-dir "$(ENGINE_DEVICE_BENCHMARK_OUT)" $(ENGINE_DEVICE_BENCHMARK_ARGS)

engine-search-mode-benchmark:
	python3 scripts/run-katago-search-mode-benchmark.py --samples "$(ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES)" --out-dir "$(ENGINE_SEARCH_MODE_BENCHMARK_OUT)" $(ENGINE_SEARCH_MODE_BENCHMARK_ARGS)

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

release: doctor ensure-release-engine
	$(GRADLEW) :app-android:assembleRelease

ensure-debug-engine:
	@test -f "$(DEBUG_ENGINE_BINARY)" || ( \
		echo "Missing debug engine artifact: $(DEBUG_ENGINE_BINARY)"; \
		echo "Run 'make prebuild-engine' to build the pinned local artifact, or use 'make dev-stub' for stub-only UI work."; \
		exit 2; \
	)

ensure-release-engine:
	@test -f "$(RELEASE_ENGINE_BINARY)" || ( \
		echo "Missing release engine artifact: $(RELEASE_ENGINE_BINARY)"; \
		echo "Release builds must use a prepared and verified engine artifact. Run the release artifact preparation flow before 'make release'."; \
		exit 2; \
	)

prebuild-engine: doctor
	ANDROID_HOME="$(ANDROID_HOME)" ./scripts/build-katago-android-spike.sh
	@test -f "$(DEBUG_ENGINE_BINARY)" || (echo "Engine prebuild finished but debug artifact was not created: $(DEBUG_ENGINE_BINARY)" && exit 1)
	@echo "Debug engine artifact ready: $(DEBUG_ENGINE_BINARY)"

clean:
	$(GRADLEW) clean
