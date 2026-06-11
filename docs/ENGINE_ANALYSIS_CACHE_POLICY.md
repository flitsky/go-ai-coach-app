# 엔진 분석 Cache 정책

작성일: 2026-06-11  
상태: 기본 비활성. 재도입 전 검토 필요.

## 현재 결정

`AnalysisResultCache`는 기본값을 `Disabled`로 둔다.

- 같은 국면 fingerprint가 있더라도 이전 Top Moves / move review 분석 결과를 재사용하지 않는다.
- 매 사용자 차례 또는 AI 차례의 `TurnAnalysis`는 원칙적으로 새 엔진 호출 결과를 사용한다.
- 현재 화면에 이미 올라온 같은 턴의 `MoveAnalysisSnapshot`을 `Top Moves` 버튼으로 표시하는 것은 유지한다. 이것은 이전 국면 cache 재사용이 아니라 현재 턴 분석 결과 표시다.

## 비활성화 이유

현재 cache 재사용은 다음 위험이 있다.

- 낮은 visits/time cap에서 불안정한 후보가 같은 결론처럼 반복될 수 있다.
- AI 레벨링이 같은 후보 pool을 반복 사용하면 같은 수 선택 경향이 강해진다.
- `fill=SHORT` 결과가 cache에 들어가면 충분하지 않은 분석이 이후 판단을 계속 오염시킬 수 있다.
- 진 판의 불량 판단이 다음 동일 fingerprint 상황에서 재사용될 수 있다.

## 재도입 후보

cache를 다시 켜기 전에 아래 조건을 검토한다.

1. 확률적 재사용: cache hit가 있더라도 50% 정도만 사용하고 나머지는 새 분석을 요청한다.
2. 패배 기반 무효화: cache를 사용한 판에서 패배하거나 큰 손실이 확인되면 관련 cache entry를 제거한다.
3. 안정성 기반 등록: 같은 국면에서 엔진이 3회 연속 같은 `engineOrder`/후보를 반환하고 root visits가 충분할 때만 cache에 등록한다.
4. 품질 게이트: `fill=OK`, root visits 기준 충족, `pointLoss` 존재 같은 조건을 통과한 결과만 저장한다.
5. 목적 분리: AI 착수 선택 cache와 사람 착수 리뷰 cache를 분리한다.
6. TTL/세대 관리: 시간 또는 대국 단위로 cache를 폐기한다.
7. 진단 노출: debug report에 cache mode, hit/miss, entry별 품질 조건을 명확히 남긴다.

## 코드 기준

- 기본 mode: `AnalysisCacheMode.Disabled`
- 위치: `app-android/src/main/java/com/worksoc/goaicoach/application/AnalysisSession.kt`
- UI 진단: `Copy Log`의 `[Runtime] analysisCache=disabled...`

재활성화 시에는 단순히 `Enabled`로 바꾸지 말고 위 품질 게이트를 application 계층에 추가한 뒤 켠다.
