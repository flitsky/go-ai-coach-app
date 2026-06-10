# SM-S908N 실제 대국/벤치마크 비교 로그

일시: 2026-06-11 07:06-07:20 KST  
기기: `SM-S908N` via Wi-Fi ADB `192.168.35.191:42353`  
목적: benchmark가 느린데 `빠른 초급 3단계` 실제 대국은 쾌적한 이유 확인

## 결론

느린 benchmark와 쾌적한 실제 대국은 서로 다른 호출 예산을 사용한다.

- `빠른 초급 3단계` 실제 대국: `16 visits / 250ms`, fast GTP path
- startup benchmark: B16/B32/B64 모두 `timeCapMs=5000`, 진단용 장시간 호출

따라서 benchmark 결과를 그대로 사용자 체감 대국 속도로 해석하면 안 된다. benchmark는 느린 기기에서 visits 충족 여부와 장시간 호출 비용을 보는 진단값이고, 실제 대국은 250ms 경량 호출이라 빠르게 진행된다.

## 코드 보강

이번 조사 중 기존 앱은 GTP fast path의 root visits / elapsed time을 debug report에 남기지 않는 문제가 있었다. 이를 보강했다.

- `KataGoAnalysisParser.parseRootVisitsEstimate()` 추가
- GTP `kata-search_analyze` 경로 summary에 `Visit diagnostics` 추가
- `Copy Log` 실행 시 같은 report를 앱 내부 `files/last_debug_report.txt`에도 저장

## 실제 대국 로그

저장된 debug report: `debug-report-after-diagnostic-9-moves.txt`

대국 조건:

- Black: KataGo `빠른 초급 3단계`
- White: 플레이어 일반
- `analysisLimit=visits:16, timeMillis:250, candidates:8`
- 9수까지 직접 진행

수순:

1. Black E5
2. White C5
3. Black G6
4. White G4
5. Black F3
6. White H6
7. Black H7
8. White E2
9. Black E3

9수 후 사람 차례 분석 진단:

```text
KataGo search analysis with 16 visits / 250ms.
Visit diagnostics: request=16, root=2, elapsedMs=397, timeCapMs=250, fill=SHORT.
Showing 1/1 scored spot(s).
```

해석:

- 실제 대국 경로는 250ms 기반이라 빠르게 반환된다.
- 이 샘플에서는 root estimate가 2라 `SHORT`였지만, 앱은 현재 fast best-1 대국 리듬을 우선한다.
- `SHORT`가 잦으면 기력/정확도는 낮을 수 있지만, 체감 속도는 매우 쾌적하다.

## Benchmark 재측정

저장된 profile: `engine_benchmark_profile_after_diagnostics.json`

조건:

- `measurementVersion=5`
- `benchmarkPositionName=b16-best-3-variants`
- `samplesPerVisit=5`
- `timeCapMs=5000`

| Visits | Min | Avg | Max | Root Avg | Fill |
| --- | ---: | ---: | ---: | ---: | --- |
| B16 | 410.439ms | 2656.835ms | 3233.408ms | 15.0 | SHORT 5 |
| B32 | 3010.444ms | 3173.179ms | 3220.545ms | 31.0 | SHORT 5 |
| B64 | 5025.483ms | 5104.116ms | 5203.314ms | 56.4 | SHORT 5 |

해석:

- benchmark는 `5000ms` 진단 호출이라 실제 대국보다 훨씬 느리다.
- B16/B32/B64 모두 요청 visits에 약간 못 미쳐 `SHORT`로 기록됐다.
- B64는 5초 상한에 가까워지며, 이 기기에서 실시간 대국용 기본값으로 쓰기엔 부담이 크다.

## 확보 파일

- `engine_benchmark_profile_before_gameplay.json`: 진단 보강 전 기존 benchmark profile
- `engine_benchmark_profile_after_diagnostics.json`: 진단 보강 후 benchmark profile
- `debug-report-after-diagnostic-9-moves.txt`: 9수 진행 후 앱 debug report
- `shared_prefs_go_ai_coach_session.xml`: 9수 저장 대국 snapshot
- `katago-20260611-070637-811388FD.log`: KataGo process 로그
- `screen-*.png`: 실제 폰 조작 중 화면 캡처
- `logcat-*.txt`: 조작 구간 logcat tail

## 다음 판단

1. `빠른 초급`은 현재처럼 250ms 경량 호출을 유지해도 체감 대국에는 적합하다.
2. benchmark는 대국 속도 예측용이 아니라 진단용으로 문구를 바꿔야 한다.
3. 기력 정확도를 높이고 싶으면 별도 설정으로 `빠른 초급 정확도 우선` 같은 profile을 추가하고, 250ms보다 큰 time cap을 쓰는 방향이 낫다.
4. 실전 기본값 자동 보정은 benchmark 평균을 그대로 적용하지 말고, `실제 대국용 fast-call micro benchmark`를 별도로 만들어야 한다.
