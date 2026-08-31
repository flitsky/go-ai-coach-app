# AI 착수 전 clear_cache 적용 검증

## 변경

`EngineAdapter`에 `clearSearchCache()`를 추가하고, KataGo process adapter는 GTP `clear_cache`를 호출하도록 했다.

AI가 실제 착수 후보를 분석하기 직전에 다음 경로에서 cache를 비운다.

- 사람 착수 후 AI 응수 분석
- AI vs AI 자동대국의 각 AI 착수 분석

Top Moves 표시나 과거 분석 cache 재사용 정책과 섞이지 않도록, AI 착수 분석 직전에만 명시적으로 호출한다.

## 실기기 검증

- 기기: SM-S908N
- Black: `중급 5단계` (`B64`, `4500ms`)
- White: `빠른 초급 3단계` (`B16`, `2000ms`)
- Auto delay: `0ms`

23수 관찰 결과:

| 타입 | 건수 | engine min | engine avg | engine max | 100ms 미만 | root avg | fill |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| B16 | 11 | 2095ms | 2158.7ms | 2185ms | 0 | 13.5 | OK 1 / SHORT 10 |
| B64 | 12 | 4510ms | 4577.4ms | 4696ms | 0 | 22.6 | SHORT 12 |

## 결론

`clear_cache` 적용 후 B16이 1~4ms로 반복 반환되는 현상은 재현되지 않았다.

따라서 이번 초고속 자동대국의 주 원인은 앱 레벨 `AnalysisResultCache`가 아니라 KataGo process 내부 검색 트리/NN cache 재사용으로 보는 것이 타당하다.

단, B16/B64 모두 폰에서 여전히 `fill=SHORT`가 많이 발생한다. 이는 time cap 안에서 요청 visits를 충분히 채우지 못한다는 별도 성능/품질 이슈로 남겨둔다.
