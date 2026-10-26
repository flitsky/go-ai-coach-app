// KataGo를 iOS 앱 안에서 부르는 다리 — Kotlin 엔진 코드(`:engine-android`의 iosMain, cinterop)와 iOS 앱(구현:
// `app-ios/GoCoach/Engine/KataGoBridge.cpp` + `libkatago.a`) 사이의 약속이다(백로그 #293).
//
// iOS 앱은 자식 프로세스를 띄울 수 없다 — 안드로이드처럼 `katago gtp …` · `katago analysis …`를 프로세스로 띄우는 대신, 같은
// 명령줄로 KataGo의 `main`(이름만 `katago_main` — `scripts/build-katago-ios.sh`)을 **앱 안 스레드에서** 부른다.
// GTP 루프와 분석 루프는 각자의 스트림을 쓴다(빌드 스크립트가 KataGo의 두 파일을 고친다) — 그래서 두 종류가 **함께** 돈다
// (사용자 2026-10-26: 안드로이드처럼 대국용 · 분석용 둘 다). 스트림은 파이프로 돌려 호출자에게 다른 끝을 준다 — 호출자는
// 프로세스의 stdin / stdout과 똑같이 한 줄씩 쓰고 읽는다.
#ifndef KATAGO_BRIDGE_H
#define KATAGO_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

/// 엔진 종류 — 종류마다 한 번에 하나가 돈다(안드로이드의 GTP 슬롯 · analysis 슬롯과 같다).
#define KATAGO_BRIDGE_GTP 0
#define KATAGO_BRIDGE_ANALYSIS 1

/// `katago <argv[1]> …`을 앱 안 스레드에서 시작한다(argv[0]은 프로그램 이름 자리, argv[1]은 `gtp` · `analysis`).
/// 성공하면 이 엔진의 세션 번호(양수)를 돌려주고 `toEngineFd`(명령을 쓸 곳) · `fromEngineFd`(응답을 읽을 곳)를 채운다.
/// 같은 종류의 엔진이 아직 돌고 있으면 -1 — 옛 엔진은 지금 명령을 끝내고 명령 파이프의 EOF를 읽으면 스스로 내려간다.
/// 파이프를 만들지 못하면 -2, 종류가 틀리면 -3.
/// 엔진이 끝나면(`quit` · 명령 파이프 EOF · KataGo의 예외) `fromEngineFd`에서 EOF를 읽는다.
/// ⚠️ 처음 부를 때 SIGPIPE를 무시하게 한다 — 이미 닫힌 파이프에 쓰면 앱이 죽는 대신 쓰기가 오류(EPIPE)를 돌려준다.
int katago_bridge_start(int kind, int argc, const char* const* argv, int* toEngineFd, int* fromEngineFd);

/// [sessionId]의 엔진 스레드가 아직 도는가(1) · 끝났는가(0). 끝난 세션 번호는 다시 쓰이지 않는다.
int katago_bridge_session_running(int sessionId);

#ifdef __cplusplus
}
#endif

#endif
