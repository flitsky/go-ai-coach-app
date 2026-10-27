import BackgroundAssets
import Foundation
import SharedUI
import System

/// 엔진 모델 팩(백로그 #293 — 사용자 2026-10-27: 설치 뒤 Apple이 호스팅하는 Background Assets로 받는다, 최소 iOS 26).
/// Kotlin `IosEngineModelSource`의 구현 — 팩 `katago-models` 하나에 네 파일이 `katago/<파일>`로 든다(`scripts/ios-package-model-pack.sh`가 같은 경로로 묶는다).
/// 받기 진행 · 실패 · 다시 시도는 Kotlin 쪽(`awaitIosModelPack`)이 안드로이드 Play 팩과 같은 화면으로 그린다.
///
/// ⚠️ 시뮬레이터에서는 쓰지 않는다 — Apple 호스팅 팩 시험이 시뮬레이터에서 깨져 있고(Xcode 26.5 알려진 문제 173742496), 서명 팀 없이
///    `AssetPackManager.shared`를 건드리면 앱이 멈출 수 있다. 시뮬레이터는 맥의 모델 파일(`GOCOACH_KATAGO_*`, `make ios-run`)로 돈다.
final class AppleHostedModelSource: NSObject, IosEngineModelSource, @unchecked Sendable {
    static let packID = "katago-models"
    static let appGroupID = "group.com.zenit9hub.ai.baduk"

    /// 쓸 수 있을 때만 만든다 — 실기기이고 앱 그룹이 열려 있을 때(그룹이 잘못되면 `AssetPackManager.shared`가 던지지 않고 앱을 멈춘다).
    static func makeIfAvailable() -> AppleHostedModelSource? {
        #if targetEnvironment(simulator)
        return nil
        #else
        guard FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) != nil else { return nil }
        return AppleHostedModelSource()
        #endif
    }

    func localFilePath(fileName: String) -> String? {
        // `url(for:)`는 파일이 없어도 그럴듯한 주소를 준다 — 있는지는 따로 본다. 주소는 프로세스 밖으로 남기지 않는다(실행마다 다시 묻는다).
        guard let url = try? AssetPackManager.shared.url(for: FilePath("katago/\(fileName)")) else { return nil }
        return FileManager.default.fileExists(atPath: url.path) ? url.path : nil
    }

    func startDownload(listener: IosEngineModelDownloadListener) {
        Task.detached {
            let manager = AssetPackManager.shared
            do {
                let pack = try await manager.assetPack(withID: Self.packID)
                let total = Int64(pack.downloadSize)
                // 진행을 먼저 듣는다 — `ensureLocalAvailability` 뒤에 걸면 앞의 소식을 놓친다.
                let watcher = Task {
                    for await update in manager.statusUpdates(forAssetPackWithID: Self.packID) {
                        if case .downloading(_, let progress) = update {
                            listener.onProgress(bytesDownloaded: Int64(progress.fractionCompleted * Double(total)), totalBytes: total)
                        }
                    }
                }
                defer { watcher.cancel() }
                if #available(iOS 26.4, *) {
                    try await manager.ensureLocalAvailability(of: pack, requireLatestVersion: false)
                } else {
                    try await manager.ensureLocalAvailability(of: pack)
                }
                watcher.cancel()
                listener.onCompleted()
            } catch {
                listener.onFailed(message: String(describing: error))
            }
        }
    }
}
