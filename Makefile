SHELL := /bin/bash

ANDROID_HOME ?= /Users/ryan9kim/Library/Android/sdk
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 17 2>/dev/null)
GRADLEW := ./gradlew

ENGINE_ABI ?= arm64-v8a
DEBUG_ENGINE_BINARY := app-android/src/debug/jniLibs/$(ENGINE_ABI)/libkatago.so
RELEASE_ENGINE_BINARY := app-android/src/main/jniLibs/$(ENGINE_ABI)/libkatago.so

export ANDROID_HOME
export JAVA_HOME

.PHONY: doctor test dev dev-stub install-dev install-dev-engine reinstall-dev-engine seed-engine launch release ensure-debug-engine ensure-release-engine prebuild-engine clean

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
	$(GRADLEW) :shared:check :app-android:assembleDebug :app-android:testDebugUnitTest

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
