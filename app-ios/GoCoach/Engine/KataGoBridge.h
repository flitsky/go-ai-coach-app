// KataGo를 iOS 앱 안에서 부르는 다리(백로그 #293).
//
// iOS 앱은 자식 프로세스를 띄울 수 없다 — 안드로이드처럼 `katago gtp …`를 프로세스로 띄우는 대신, 같은 명령줄로
// KataGo의 `main`(이름만 `katago_main` — `scripts/build-katago-ios.sh`)을 **앱 안 스레드에서** 부른다.
// KataGo는 표준 입출력(std::cin / std::cout)으로 말하므로, 그 둘을 파이프로 돌려 호출자에게 파이프의 다른 끝을 준다 —
// 호출자는 프로세스의 stdin / stdout과 똑같이 한 줄씩 쓰고 읽으면 된다(GTP · analysis의 대화는 안드로이드와 같다).
#ifndef KATAGO_BRIDGE_H
#define KATAGO_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

/// `katago <argv[1]> …`을 앱 안 스레드에서 시작한다(argv[0]은 프로그램 이름 자리).
/// 성공하면 0을 돌려주고 `toEngineFd`(명령을 쓸 곳) · `fromEngineFd`(응답을 읽을 곳)를 채운다.
/// ⚠️ std::cin / std::cout은 프로세스에 하나라 **한 번에 한 엔진**만 돈다 — 돌고 있는 동안 다시 부르면 -1.
/// 엔진이 끝나면(`quit`) `fromEngineFd`에서 EOF를 읽는다. 그 뒤에는 다시 부를 수 있다.
int katago_bridge_start(int argc, const char* const* argv, int* toEngineFd, int* fromEngineFd);

#ifdef __cplusplus
}
#endif

#endif
