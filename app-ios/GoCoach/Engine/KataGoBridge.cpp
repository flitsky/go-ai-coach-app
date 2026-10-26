#include "KataGoBridge.h"

#include <atomic>
#include <exception>
#include <iostream>
#include <streambuf>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

// KataGo의 main.cpp에 있는 `main` — `scripts/build-katago-ios.sh`가 이 이름으로 바꿔 `libkatago.a`에 넣는다.
int katago_main(int argc, const char* const* argv);

namespace {

// 파이프 한쪽 끝(fd)을 읽는 std::streambuf — KataGo의 `getline(cin, line)`이 여기서 읽는다.
class FdInBuf : public std::streambuf {
 public:
  explicit FdInBuf(int fd) : fd_(fd) {}

 protected:
  int_type underflow() override {
    ssize_t n = ::read(fd_, buf_, sizeof(buf_));
    if(n <= 0)
      return traits_type::eof();
    setg(buf_, buf_, buf_ + n);
    return traits_type::to_int_type(*gptr());
  }

 private:
  int fd_;
  char buf_[4096];
};

// 파이프 한쪽 끝(fd)에 쓰는 std::streambuf — KataGo의 `cout << … << endl`이 여기로 온다(endl이 sync를 부른다).
class FdOutBuf : public std::streambuf {
 public:
  explicit FdOutBuf(int fd) : fd_(fd) { setp(buf_, buf_ + sizeof(buf_)); }

 protected:
  int_type overflow(int_type c) override {
    if(!flushBuffer())
      return traits_type::eof();
    if(!traits_type::eq_int_type(c, traits_type::eof())) {
      *pptr() = traits_type::to_char_type(c);
      pbump(1);
    }
    return traits_type::not_eof(c);
  }

  int sync() override { return flushBuffer() ? 0 : -1; }

 private:
  bool flushBuffer() {
    char* p = pbase();
    while(p < pptr()) {
      ssize_t n = ::write(fd_, p, static_cast<size_t>(pptr() - p));
      if(n <= 0)
        return false;
      p += n;
    }
    setp(buf_, buf_ + sizeof(buf_));
    return true;
  }

  int fd_;
  char buf_[4096];
};

std::atomic<bool> running{false};

}  // namespace

int katago_bridge_start(int argc, const char* const* argv, int* toEngineFd, int* fromEngineFd) {
  bool expected = false;
  if(!running.compare_exchange_strong(expected, true))
    return -1;

  int commandPipe[2];  // 호출자가 쓴다 → KataGo가 std::cin으로 읽는다
  int responsePipe[2];  // KataGo가 std::cout으로 쓴다 → 호출자가 읽는다
  if(::pipe(commandPipe) != 0) {
    running = false;
    return -2;
  }
  if(::pipe(responsePipe) != 0) {
    ::close(commandPipe[0]);
    ::close(commandPipe[1]);
    running = false;
    return -2;
  }

  std::vector<std::string> args(argv, argv + argc);
  int engineIn = commandPipe[0];
  int engineOut = responsePipe[1];
  std::thread([args, engineIn, engineOut]() {
    FdInBuf inBuf(engineIn);
    FdOutBuf outBuf(engineOut);
    std::streambuf* oldIn = std::cin.rdbuf(&inBuf);
    std::streambuf* oldOut = std::cout.rdbuf(&outBuf);
    std::cin.clear();

    std::vector<const char*> cArgs;
    for(const std::string& arg : args)
      cArgs.push_back(arg.c_str());
    // ⚠️ 앱 안에서 돌므로 KataGo가 던진 예외가 여기서 새면 **앱 전체가 죽는다**(프로세스였다면 엔진만 죽었다).
    //    잡아서 stderr에 남기고, 파이프를 닫아 호출자에게는 「엔진이 끝났다」(EOF)로 보이게 한다 — 안드로이드에서 프로세스가 죽은 것과 같은 모양.
    try {
      katago_main(static_cast<int>(cArgs.size()), cArgs.data());
    }
    catch(const std::exception& e) {
      std::cerr << "KataGo stopped with an exception: " << e.what() << std::endl;
    }
    catch(...) {
      std::cerr << "KataGo stopped with an unknown exception" << std::endl;
    }

    std::cout.flush();
    std::cin.rdbuf(oldIn);
    std::cout.rdbuf(oldOut);
    ::close(engineIn);
    ::close(engineOut);  // 호출자는 EOF를 읽는다
    running = false;
  }).detach();

  *toEngineFd = commandPipe[1];
  *fromEngineFd = responsePipe[0];
  return 0;
}
