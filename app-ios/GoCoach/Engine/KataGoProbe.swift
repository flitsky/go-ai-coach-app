import Foundation

#if DEBUG
/// KataGo가 iOS 앱 **안에서** 도는지 보는 확인(백로그 #293) — 시뮬레이터용, 디버그 빌드에만 있다.
///
/// 환경 변수 `KATAGO_PROBE_MODEL` · `KATAGO_PROBE_CONFIG`가 있을 때만 돈다(`make ios-engine-probe`가 넣는다 —
/// 시뮬레이터 앱은 맥의 파일 경로를 읽을 수 있어서 모델을 앱에 싣지 않고 맥에 있는 것을 쓴다. **실기기에서는 안 된다**).
/// 안드로이드와 같은 명령줄(`gtp -model … -config … -override-config …`)로 띄우고, GTP 몇 마디를 주고받아 걸린 시간과 함께 찍는다.
/// `KATAGO_PROBE_HUMAN_MODEL`이 있으면 주 모델을 `quit`한 뒤 **같은 앱 안에서** 사람 모델로 다시 띄운다 — 안드로이드의 「갈아 올리기」.
/// 진짜 엔진 연결은 Kotlin 쪽 어댑터가 맡는다(#293의 다음 단계) — 이 파일은 그때 지운다.
enum KataGoProbe {
    static func runIfRequested() {
        let env = ProcessInfo.processInfo.environment
        guard let model = env["KATAGO_PROBE_MODEL"], let config = env["KATAGO_PROBE_CONFIG"] else { return }
        let humanModel = env["KATAGO_PROBE_HUMAN_MODEL"]
        let exitWhenDone = env["KATAGO_PROBE_EXIT"] == "1"
        Thread.detachNewThread {
            run(label: "main", model: model, config: config, extraOverrides: [],
                commands: ["name", "version", "boardsize 9", "clear_board", "genmove b", "genmove w", "showboard", "quit"])
            if let humanModel {
                // 사람 모델만 올린 KataGo는 프로필이 있어야 답한다 — 안드로이드처럼 판정용 프로필로 띄우고, 요청마다 바꾼다.
                run(label: "human", model: humanModel, config: config, extraOverrides: ["humanSLProfile=rank_9d"],
                    commands: ["boardsize 9", "clear_board", "kata-set-param humanSLProfile rank_15k", "genmove b", "quit"])
            }
            if exitWhenDone { exit(0) }
        }
    }

    private static func run(label: String, model: String, config: String, extraOverrides: [String], commands: [String]) {
        let tmp = NSTemporaryDirectory()
        // KataGo는 로그 · 홈 폴더를 한 단계만 만든다 — 안드로이드처럼 미리 만들어 둔다(`EngineBootstrap.kt`의 mkdirs).
        for dir in ["katago/logs", "katago/home"] {
            try? FileManager.default.createDirectory(atPath: tmp + dir, withIntermediateDirectories: true)
        }
        // 안드로이드의 기동 인자(`EngineBootstrap.kt`의 startupOverrides)와 같게 — 로그 · 홈은 앱의 임시 폴더로.
        let overrides = ([
            "numSearchThreads=1",
            "logDir=\(tmp)katago/logs",
            "homeDataDir=\(tmp)katago/home",
            "logToStderr=false",
            "logAllGTPCommunication=false",
            "logSearchInfo=false",
            "allowResignation=false",
            "startupPrintMessageToStderr=false",
            "maxVisits=64",
        ] + extraOverrides).joined(separator: ",")
        let args = ["katago", "gtp", "-model", model, "-config", config, "-override-config", overrides]

        let started = Date()
        var toEngine: Int32 = -1
        var fromEngine: Int32 = -1
        let cArgs = args.map { UnsafePointer<CChar>(strdup($0)) }
        var rc: Int32 = -1
        // 앞 엔진의 스레드가 파이프를 닫고 물러나는 사이(-1 = 아직 돌고 있음)는 잠깐 기다린다.
        for _ in 0..<50 {
            rc = cArgs.withUnsafeBufferPointer { katago_bridge_start(Int32(args.count), $0.baseAddress, &toEngine, &fromEngine) }
            if rc != -1 { break }
            Thread.sleep(forTimeInterval: 0.1)
        }
        cArgs.forEach { free(UnsafeMutablePointer(mutating: $0)) }
        guard rc == 0 else {
            log("[\(label)] katago_bridge_start failed: \(rc)", since: started)
            return
        }
        log("[\(label)] engine thread started in-process (pid \(getpid()), no child process)", since: started)

        let input = FileHandle(fileDescriptor: toEngine, closeOnDealloc: true)
        let output = FileHandle(fileDescriptor: fromEngine, closeOnDealloc: true)
        for command in commands {
            let sent = Date()
            input.write(Data((command + "\n").utf8))
            let response = readResponse(output) ?? "(EOF)"
            log("[\(label)] \(command) -> \(response) [\(String(format: "%.2f", Date().timeIntervalSince(sent)))s]", since: started)
        }
        // `quit` 뒤에는 엔진 스레드가 파이프를 닫는다 — EOF까지 읽어 끝난 것을 확인한다.
        while !output.readData(ofLength: 1).isEmpty {}
        log("[\(label)] engine finished (EOF)", since: started)
    }

    /// GTP 응답 하나 — 빈 줄로 끝난다(`= …\n\n` · `? …\n\n`).
    private static func readResponse(_ handle: FileHandle) -> String? {
        var data = Data()
        while true {
            let chunk = handle.readData(ofLength: 1)
            if chunk.isEmpty { return data.isEmpty ? nil : String(decoding: data, as: UTF8.self) }
            data.append(chunk)
            if data.count >= 2 && data.suffix(2) == Data("\n\n".utf8) {
                return String(decoding: data.dropLast(2), as: UTF8.self)
            }
        }
    }

    private static func log(_ message: String, since start: Date) {
        print("[KataGoProbe +\(String(format: "%.2f", Date().timeIntervalSince(start)))s] \(message)")
        fflush(stdout)
    }
}
#endif
