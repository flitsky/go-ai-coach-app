import BackgroundAssets
import ExtensionFoundation
import StoreKit

// 엔진 모델 팩을 받는 확장(백로그 #293 — Apple이 호스팅하는 Background Assets, 사용자 2026-10-27). 시스템이 설치 · 업데이트 때와 앱이 부를 때
// 이 확장으로 팩을 받는다. 할 일은 없다 — 모든 팩을 받는다(`shouldDownload` 기본값). ⚠️ 이 확장을 빼면 앱의 `AssetPackManager.shared`가 앱을 멈춘다.
@main
struct DownloaderExtension: StoreDownloaderExtension {}
