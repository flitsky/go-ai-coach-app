# KaTrain UX 참고 백로그

작성일: 2026-06-05

## 목적

KaTrain의 UX 중 Go AI Coach에 가져올 만한 항목을 효과와 구현 난이도 기준으로 정리한다.

이 문서는 “KaTrain을 그대로 복제”하기 위한 목록이 아니다. 현재 앱은 Android-first 모바일 앱이고, 도메인/엔진 경계를 `EngineAdapter` 뒤에 숨기는 방향을 유지한다. 따라서 KaTrain의 Kivy UI 코드를 직접 가져오기보다, 사용자가 대국 중 바로 체감하는 정보 구조와 상호작용 패턴만 선별해 Compose UI와 shared 도메인 모델로 재구현한다.

## 참고한 KaTrain 지점

- 로컬 KaTrain 설치본: `/Users/ryan9kim/.local/pipx/venvs/katrain/lib/python3.12/site-packages/katrain`
- `core/game_node.py`: 후보수 `candidate_moves`, `pointsLost`, `winrateLost`, `policy`, `ownership`, 분석 저장 구조
- `gui/badukpan.py`: 후보수 spot, 점수 손실 label, PV 표시, ghost stone, ownership/policy overlay
- `gui/widgets/graph.py`: 점수/승률 그래프
- `gui/widgets/movetree.py`: 변화도/분기 관리
- `config.json`, `gui.kv`: hints, show dots, top move 표시 옵션, policy/ownership 토글, 엔진 상태/분석 컨트롤

## 현재 앱에 이미 반영된 항목

- `Top Moves` 버튼 기반 후보수 표시
- 사람 차례 백그라운드 pre-move analysis에서 현재 합법 착점 수만큼 후보 데이터 확보
- 후보수별 green/yellow/red/unknown spot
- 후보수별 점수 lead label
- 착수 후 해당 돌 중앙에 착수 당시 평가 색상 dot 유지
- Top Moves 표시 여부와 무관한 백그라운드 pre-move analysis
- 3D 질감의 흑/백 바둑알 렌더링
- pass 2회 이후 계가 처리
- `Copy Log` 기반 디버그 리포트 수집
- 한수 무르기
- 2P 테스트 모드
- KaTrain UX 옵션 패널
- 옵션 기반 보드 좌표 표시
- 옵션 기반 수순 번호 표시
- 옵션 기반 마지막 수 ring 표시
- 옵션 기반 spot 색상 범례
- 옵션 기반 엔진 상태 badge
- 옵션 기반 차례/포획/마지막 수 status strip
- 옵션 기반 ownership heatmap overlay
- 옵션 기반 score graph panel
- `Copy Log` 실행 시 clipboard toast 표시

## 1순위: 간단하면서 임팩트 큰 항목

| 항목 | 가져올 UX | 현재 앱 대비 구현 범위 | 기대 효과 | 난이도 |
| --- | --- | --- | --- | --- |
| 보드 좌표 표시 토글 | KaTrain의 좌표/수순 표시 토글처럼 A-J, 1-9 좌표를 켜고 끄기 | `GoBoard` Canvas에 좌표 label 옵션 추가 | 사용자가 로그와 화면을 대조하기 쉬움. 현재 디버그 리포트의 `A1` 좌표와 실제 화면 연결성이 좋아짐 | 낮음 |
| 마지막 수/수순 번호 표시 토글 | 마지막 수 강조와 move number 표시 | 현재 착수 평가 dot과 충돌하지 않도록 작은 수순 번호 또는 마지막 수 ring 추가 | 실기기 테스트 중 “방금 어디에 뒀는지” 확인이 쉬워짐 | 낮음 |
| 후보수 디버그 텍스트 | 보드 spot과 별도로 후보수 목록을 `좌표 / 손실 / visits / prior`로 출력 | 하단 engine response/debug 영역과 `Copy Log`에 `candidateText`로 출력 | 보드 중심 화면을 유지하면서 필요 시 숫자 정보를 확인 가능 | 완료 |
| 색상 범례 | green/yellow/red/gray-blue 의미를 한 줄로 표시 | `Spot legend` 메뉴 옵션으로 작은 legend 추가 | 사용자와 테스트 참여자가 점 색상을 바로 이해함 | 낮음 |
| 엔진 상태 badge | KaTrain의 engine ready/busy/down dot처럼 명확한 상태 표시 | `engineReady`, `engineBusy`, fallback 상태를 별도 badge로 표시 | “목업으로 바뀐 것 같다” 같은 혼란을 줄임 | 낮음 |
| 분석 중 표시와 취소 버튼 | 분석이 길어질 때 spinner/progress와 cancel 제공 | `EngineAdapter`에 취소까지 바로 넣기 어렵다면 우선 UI busy/cooldown 표시부터 적용 | 느린 Top Moves 분석 시 앱이 멈춘 것처럼 보이는 문제 완화 | 낮음-중간 |
| 잡은 돌/차례 표시 강화 | KaTrain의 prisoner/active player UI처럼 흑백 캡처와 다음 차례를 더 눈에 띄게 표시 | 현재 텍스트 표시를 compact score strip으로 변경 | 계가 논의와 테스트에서 포획 수 확인이 쉬워짐 | 낮음 |
| 디버그 리포트 공유 UX | 현재 `Copy Log`를 유지하되, 제목/요약/클립보드 성공 상태를 더 명확히 표시 | 복사 완료 toast/snackbar, 리포트 파일 저장 옵션 검토 | 사용자 테스트 중 오류 수집 품질 향상 | 낮음 |

## 2순위: 중간 난이도지만 효과 큰 항목

| 항목 | 가져올 UX | 현재 앱 대비 구현 범위 | 기대 효과 | 난이도 |
| --- | --- | --- | --- | --- |
| Ownership overlay | KaTrain의 ownership 시각화처럼 각 교차점/영역의 흑백 지배도를 색으로 표시 | `ScoreEstimate.ownership` 또는 KataGo raw NN `whiteOwnership`을 보드 heatmap으로 렌더링 | “집이 어디에 형성되는지”, “왜 우세한지”를 즉시 이해 가능 | 중간 |
| 점수/승률 미니 그래프 | KaTrain `ScoreGraph`처럼 수순별 score lead/winrate 추이 표시 | move별 `ScoreSnapshot` 저장 모델과 작은 line chart 추가 | 대국 흐름에서 어느 수가 판세를 바꿨는지 확인 가능 | 완료 |
| 착수 리뷰 문장 개선 | KaTrain이 `pointsLost`로 착수를 분류하듯 `좋음/부정확/실수/큰 실수` 문구 제공 | 현재 `pointLoss` threshold를 사용자 친화적 문장으로 변환 | 색상 dot만으로 부족한 설명을 보강 | 중간 |
| 후보수 상세 모드 | 후보 spot label을 `점수 손실`, `승률`, `visits`, `prior` 중 선택 | `top_moves_show`류 옵션을 단순 segmented control로 구현 | 초급자는 점수, 디버깅은 visits/prior 중심으로 볼 수 있음 | 중간 |
| 수동 “더 깊게 분석” | KaTrain의 extra/deeper analysis처럼 현재 국면만 visits/time을 임시 상향 | Top Moves 분석을 한 번 더 강하게 요청하는 버튼으로 노출하고 완료 후 원복 | 평소 AI는 가볍게, 필요 시 강한 후보수 확보 | 중간 |
| 최종 계가 확인 화면 | pass 2회 후 바로 결과만 표시하지 않고 board + 영역 + 사석 후보를 확인 | 우선 Chinese area 기준 overlay, 이후 dead stone marking으로 확장 | 사용자가 계가 오류를 인지/보고하기 쉬움 | 중간 |
| 착수 preview | KaTrain ghost stone처럼 터치 위치에 반투명 돌 preview | 모바일에서는 long press 또는 drag 중 preview로 제한 | 오터치 감소, 9x9 이후 13x13/19x19 확장 대비 | 중간 |

## 3순위: 임팩트는 크지만 구조 투자가 필요한 항목

| 항목 | 가져올 UX | 필요한 선행 작업 | 기대 효과 | 난이도 |
| --- | --- | --- | --- | --- |
| PV/변화도 preview | 후보수를 탭하면 예상 진행 수순을 반투명 돌로 표시 | KataGo `pv` 파싱을 `CandidateMove` DTO에 추가, board overlay 레이어 확장 | “왜 이 수가 좋은가”를 직관적으로 보여줌 | 중간-높음 |
| 실수 찾기/다음 실수 이동 | KaTrain의 find mistake처럼 큰 `pointLoss`가 발생한 수로 이동 | 전체 수순 분석 기록, move review history, navigation state | 복기 앱으로서 가치가 커짐 | 높음 |
| SGF export/import | KaTrain처럼 대국 기록을 SGF로 저장/불러오기 | shared game record 직렬화, Android 파일/share UX | 친구/다른 앱/KaTrain과 기록 교환 가능 | 높음 |
| 전체 대국 리포트 | KaTrain game report처럼 수순별 주요 실수와 추천수 요약 | batch analysis, 리포트 데이터 모델, 결과 화면 | 코칭 앱의 핵심 차별화 기능 가능 | 높음 |
| 착수 분기/변화도 트리 | KaTrain `MoveTree`처럼 후보 변화와 분기를 관리 | 현재 선형 `GameState`를 game tree 모델로 확장 | 복기/학습 모드에 필수적인 기반 | 높음 |
| dead stone marking | 종료 후 사용자가 죽은 돌을 지정하고 재계가 | 영역 계산 UI, dead/alive 상태 모델, KataGo `final_status_list` 참고 검증 | 현재 계가 혼란을 줄이는 실전 기능 | 높음 |

## 4순위: 가져오기는 어렵고 현재 임팩트가 낮은 항목

| 항목 | 보류 이유 |
| --- | --- |
| KaTrain식 전체 데스크톱 패널 구조 | 모바일 첫 화면에는 정보 밀도가 과도함. 현재는 보드 중심 UX가 더 적절함 |
| 복잡한 설정/테마/자산 선택 | 바둑알 질감은 이미 개선됨. 세부 테마 옵션은 제품 방향 확정 후가 적절함 |
| 전체 키보드/마우스 단축키 UX | Android-first 앱에서는 초기 우선순위가 낮음 |
| 기여/분석 공유용 고급 엔진 화면 | 첫 릴리즈 전 사용자 가치가 낮고 엔진 운영 복잡도가 큼 |
| 지역 선택 분석 ROI | 강력하지만 모바일 조작이 까다롭고, 기본 후보수/PV/ownership 이후에 검토하는 것이 맞음 |
| 고급 AI 스타일 전체 이식 | KaTrain의 AI profile은 풍부하지만, 현재는 낮은 난이도/Top Moves 품질/엔진 안정성이 더 중요함 |

## 권장 적용 순서

1. **보드 가독성 정리**
   - 좌표 표시 토글: 구현됨
   - 마지막 수 표시: 기존 red dot 유지, 옵션 ring 구현됨
   - 수순 번호 표시 토글: 구현됨
   - 잡은 돌/차례 표시 강화: 옵션 status strip 구현됨

2. **Top Moves UX 완성**
   - 후보수 compact list 패널: 화면 집중을 위해 제거됨. 상세 후보 정보는 debug text와 `Copy Log`로 확인한다.
   - Top Moves 버튼: 구현됨. 사람 차례마다 현재 합법 착점 수 기준으로 pre-move analysis를 수행하고, 버튼 클릭 시 cache를 보드에 표시한다.
   - 색상 범례: 구현됨
   - 후보 label 표시 모드: 미구현
   - 엔진 busy/ready/fallback badge: 구현됨

3. **형세 이해 보강**
   - ownership overlay: 구현됨. `Eval` 실행 후 `Ownership` 옵션을 켜면 KataGo `whiteOwnership` 값을 보드 heatmap으로 표시한다.
   - 점수/승률 미니 그래프: 구현됨. `ScoreSnapshot`/`ScoreTimeline` 도메인 모델에 수순별 score lead/winrate를 기록하고, Android `ScoreGraphPanel`이 해당 데이터를 시각화한다.
   - 착수 리뷰 문장 개선

4. **복기 기능 확장**
   - PV preview
   - 실수 찾기
   - SGF export/import
   - 전체 대국 리포트

5. **종국 UX 고도화**
   - 계가 확인 화면
   - dead stone marking
   - 사용자가 조정한 최종 계가 결과를 리포트에 포함

## 다음 구현 후보 5개

지금 앱 상태에서 가장 빠르게 체감 효과를 만들 수 있는 순서는 다음이 적절하다.

1. `GoBoard` 좌표 표시 토글과 마지막 수 ring 추가: 완료
2. 후보수 compact list 패널: 제거됨. 상세 후보 정보는 debug text와 `Copy Log`로 확인
3. green/yellow/red/unknown 색상 범례 추가: 완료
4. 엔진 상태 badge를 상단 상태 영역에 고정 표시: 완료
5. ownership overlay를 `Eval` 결과와 연결해 보드 위에 시각화: 완료

## 구현 원칙

- KaTrain 코드를 직접 복사하지 않는다.
- KaTrain의 데이터 흐름은 참고하되, 앱 canonical state는 계속 `shared` game state와 move history로 유지한다.
- 엔진에서 온 정보는 `EngineAdapter` DTO로만 UI에 노출한다.
- UI 기능이 늘어나도 board/rules/engine/domain 경계가 섞이지 않도록 `app-android`의 Compose state와 `shared` 도메인 모델을 분리한다.
- 모바일 9x9 POC에서는 한 화면에서 바로 이해되는 정보만 기본 노출하고, 고급 분석은 토글/패널로 숨긴다.
