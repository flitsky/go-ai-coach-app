# Pixel 7 에뮬레이터 벤치마크 로그

일시: 2026-06-10 22:23 KST  
대상: `Pixel_7_API_35` 에뮬레이터, Android 15  
앱 패키지: `com.worksoc.goaicoach`

## 수행 내용

- `make install-dev-engine`로 debug APK 설치 및 KataGo 모델/config seed를 수행했다.
- 에뮬레이터 `/data` 공간 부족으로 최초 설치가 실패하여 기존 `com.worksoc.goaicoach` 앱을 제거하고 cache/tmp 파일을 정리한 뒤 재설치했다.
- 앱 실행 후 startup engine benchmark가 완료될 때까지 대기했다.
- 앱 내부 `files/engine_benchmark_profile.json`, KataGo 로그, logcat tail을 로컬 문서 폴더로 복사했다.

## 저장 파일

- `engine_benchmark_profile.json`: 앱 내부 benchmark 저장 원본
- `20260610-222131-3F392515.log`: KataGo process 로그
- `logcat-tail.txt`: 설치/실행/benchmark 구간 logcat tail

## Benchmark 조건

- `measurementVersion=5`
- `benchmarkPositionName=b16-best-3-variants`
- `benchmarkRuleset=Japanese`
- `samplesPerVisit=5`
- `timeCapMs=5000`
- prefix 수순: `Black E5`, `White C4`, `Black E3`

## 결과 요약

| Visits | Min | Avg | Max | Fill |
| --- | ---: | ---: | ---: | --- |
| B16 | 1073.444ms | 3980.669ms | 5058.076ms | UNKNOWN 5 |
| B32 | 3790.214ms | 4355.943ms | 4967.754ms | UNKNOWN 5 |
| B64 | 5046.995ms | 5115.025ms | 5246.744ms | UNKNOWN 5 |

## 해석

- 설치와 benchmark 실행은 정상 완료됐다.
- `fillStatus=UNKNOWN`인 이유는 현재 v5 benchmark가 경량 GTP fast path를 사용하고, 이 경로의 `AnalysisResult.summary`에는 JSON analysis 경로와 달리 `Visit diagnostics: request=..., root=..., fill=...` 문자열이 포함되지 않기 때문이다.
- 따라서 이번 로그는 elapsed time 근거로는 사용할 수 있지만, root visits 충족 여부를 판단하는 근거로는 부족하다.
- 다음 개선 후보는 GTP fast path에서도 root visit 추정치를 summary에 포함시키거나, benchmark 전용으로 JSON analysis diagnostics를 선택적으로 쓰는 것이다.

## 기타 관찰

- 에뮬레이터 `/data` 여유 공간이 benchmark 후 약 253MB 수준으로 낮다. 추후 설치 실패가 반복되면 기존 앱 삭제 또는 AVD storage 정리가 필요하다.
- logcat에는 Android system storage warning이 일부 남았지만, Go AI Coach 앱의 명확한 fatal crash는 확인되지 않았다.
