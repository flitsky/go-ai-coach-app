package com.worksoc.goaicoach.ui

import android.widget.Toast
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.runConsumableGrant
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardSet
import com.worksoc.goaicoach.persistence.BotCollectionStore
import com.worksoc.goaicoach.application.attendance.isRewardedTier
import com.worksoc.goaicoach.application.attendance.runAttendanceDevDayRewind
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.runReleaseResetAgain
import com.worksoc.goaicoach.persistence.DeveloperModeStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 개발자 테스트 섹션(1차·2차) — 백로그 #102에서 `SettingsScreen`에서 떼어냈다.
 *
 * ## 왜 떼어냈나 — 분리 자체가 목적이 아니다
 * `SettingsScreen.kt`가 1003줄이었고 그 **3분의 1이 이 섹션**이었다. 예산이 `GoCoachApp.kt`
 * 하나만 지키고 있어 이쪽은 무방비였다(함정 3번). 목적은 *"조립만 하는 셸은 상태를 소유하지
 * 않는다"* 를 **역할 단위로** 성립시키는 것이다 — 그래서 이 파일은 자기 상태를 **스스로 갖는다.**
 * 설정 화면은 이제 이 섹션이 무엇을 읽고 쓰는지 알 필요가 없다.
 *
 * ## 이 파일이 소유하는 것 / 밖에서 받는 것
 * 소유: 빌드 정보 탭 수, 저장소·소모품·캐릭터·프리미엄·출석 표시값. 전부 여기서만 쓴다.
 * 받는 것은 **셋뿐**이고, 셋 다 이 섹션 **바깥에 사는 것**들이다 —
 * 2차 활성 여부(해제 팝업이 되돌려야 하므로), 진단 로그 팝업, 개발자 모드 끄기 요청.
 *
 * ## ⚠️ 두 단의 경계는 라벨이 아니라 "무엇을 저장하는가"다(백로그 #77)
 * **1차**는 `DeveloperModeStore`에 저장되고 **release에도 실린다.** 그래서 여기에는 권한을
 * 만들지 않는 것만 둔다. **2차**는 `BuildConfig.DEBUG`로 감싸 release·playInternal에는 아예
 * 들어가지 않는다 — 프리미엄 부여·출석일 조작처럼 **권한을 무료로 찍어내는** 것들이 온다.
 * ⚠️ **새 컨트롤을 어느 단에 둘지는 저장소에 무엇을 쓰는지로 정할 것.** 1차에 하나라도 섞이면
 * 무료 획득 경로가 그대로 출시된다.
 *
 * ## ⚠️ 부모의 간격을 여기서 재현한다
 * 원래 이 자식들은 설정 화면의 `Column(verticalArrangement = spacedBy(12.dp))` **직계**였다.
 * 하나의 컴포저블로 묶으면 그 간격이 사라지므로, 같은 `spacedBy(12.dp)`를 안쪽에 다시 준다 —
 * **이 줄을 지우면 섹션 전체가 붙어 버린다.**
 */
@Composable
internal fun DeveloperTestSection(
    isAdvancedDeveloperModeEnabled: Boolean,
    onAdvancedEnabledChange: (Boolean) -> Unit,
    onShowDiagnosticLog: () -> Unit,
    onRequestDeveloperModeOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val strings = LocalUiStrings.current
    val preferencesStore = remember(context) { UserPreferencesStore(context) }
    val premium = LocalPremiumUiState.current
    val consumables = LocalConsumableUiState.current
    val bots = LocalBotCharacterUiState.current
    // ⚠️ **2차 진입 탭 수는 저장하지 않는다**(백로그 #84). 2차 자체가 세션 한정이므로(#77의
    // 안전장치) 그 진입 카운터를 남기면 다음 실행이 9탭에서 시작하는 셈이 된다.
    var buildInfoTapCount by remember { mutableStateOf(0) }
    // 2차의 프리미엄 부제가 읽는 값. `null`이면 지금 꺼져 있다는 뜻이다.
    // ⚠️ 초 단위로 갱신하지 않는다 — 화면을 다시 그릴 때만 맞다. 만료를 정확히 재는 것은
    // `PremiumExpiryAutoDisableEffect`의 몫이고, 여기 숫자는 "대략 얼마 남았나"의 안내다.
    val premiumRemainingMinutes = premium.adGrantExpiresAtMillis
        ?.minus(System.currentTimeMillis())
        ?.takeIf { it > 0L }
        ?.let { millis -> (millis / 60_000L).toInt() }
    // 2차의 출석 부제가 읽는 값.
    // ⚠️ **버튼을 누른 직후에 저장소를 읽으면 한 일차 뒤처진다** — 되감기는 표시만 지우고 실제
    // 증가는 `AttendanceRewardClaimDialog`의 effect가 하기 때문이다. 그래서 증가를 아는 쪽이
    // 알려 주는 값(`lastCheckedInDay`)을 우선 쓰고, 아직 없으면 저장소를 읽는다.
    val attendanceDay = AttendanceClaimReplaySignal.lastCheckedInDay
        ?: remember(context) { AttendanceStore(context).load().attendanceCount }

    Column(
        modifier = modifier.fillMaxWidth(),
        // ⚠️ 위 머리말 참고 — 원래 부모가 주던 간격이다. 지우면 섹션이 통째로 붙는다.
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        // 제목 행에 **[개발자 모드 끄기]** 를 함께 둔다(백로그 #99 ⓑ). 켠 자리(버전 10탭)는
        // 숨겨져 있어도 **끄는 자리는 보여야 한다** — 실수로 켠 사람이 되돌릴 길이 있어야 한다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.settingsDevTierBasicTitle,
                // ⚠️ **제목이 양보한다**(백로그 #107). 둘 다 weight가 없으면 제목이 먼저 폭을
                // 다 가져가고 버튼이 남은 자리에 눌려, 영어 1.3배에서 `Turn off develope / r mode`
                // 로 **단어 중간이 잘렸다.** 제목은 띄어쓰기가 있어 접혀도 읽힌다.
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            TextButton(onClick = { onRequestDeveloperModeOff() }) {
                Text(strings.settingsDeveloperModeOffAction)
            }
        }

        // 읽기 전용 ① — **어느 빌드를 보고 있는가.** 실기에서 이걸 못 봐서 치른 값이
        // 있다(#47, `launch-plan/README.md` §0 B-3의 808 vs 810). 버전만으로는
        // 빌드타입과 광고 ID 종류를 알 수 없다.
        // ⚠️ **이 행이 2차 개발자 테스트의 진입점이다 — 10번 탭**(백로그 #84).
        // 읽기 전용 한 줄에 진입을 얹은 이유가 둘이다. ⓐ 이 행은 **1차 섹션 안에만
        // 존재하므로** *"1차를 먼저 켜야 한다"* 가 구조로 성립한다(런타임 검사가 아니다).
        // ⓑ 눌러도 **아무것도 바뀌지 않는 행**이라, 실수로 눌러 상태가 망가질 일이 없다.
        // ⚠️ release·playInternal에서는 [onBuildInfoTap]이 곧바로 돌아온다 —
        // 토스트조차 띄우지 않는다(안내를 띄우면 2차의 존재를 광고하는 셈이다).
        DeveloperInfoRow(
            title = strings.settingsDevBuildInfoTitle,
            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                "${BuildConfig.BUILD_TYPE} · ${if (BuildConfig.USE_TEST_ADS) "test ads" else "REAL ADS"}",
            onTap = {
                buildInfoTapCount++
                onBuildInfoTap(
                    tapCount = buildInfoTapCount,
                    isAdvancedEnabled = isAdvancedDeveloperModeEnabled,
                    onEnable = { onAdvancedEnabledChange(true) },
                    context = context,
                    strings = strings,
                )
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1회권 한 장 지급 — 출석 1일차가 30장을 주므로 한 장은 경제에 영향이 없다.
        // ⚠️ **`consumables.refresh()`를 반드시 함께 부른다.** `runConsumableGrant`는
        // 저장소에 **직접** 쓰고 화면 사본은 나가는 것만 알기 때문에(그 KDoc이 못박고
        // 있다) 빠뜨리면 마이 페이지가 옛 재고를 계속 보여준다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.settingsDevGrantTicketTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.settingsDevGrantTicketSubtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            TextButton(
                onClick = {
                    val store = ConsumableInventoryStore(context)
                    // ⚠️ **`PremiumOnce`(광고 스킵권)는 여기 두지 않는다**(2026-09-05).
                    // 이 버튼은 1차라 **release 빌드에 그대로 실린다**(함정 11번). 형세·추천
                    // 1회권은 1일차 출석이 30장씩 주는 것이라 경제에 영향이 없지만,
                    // 광고 스킵권은 **광고가 사는 바로 그 재화**다(4·7일차 보상). 결제가
                    // 파킹된 지금 광고가 유일한 수익원이라, 반복해서 누를 수 있는 스킵권
                    // 발행기를 출시하는 앱에 넣는 셈이 된다.
                    // · 필요하면 2차(debug 전용)에서 만들 것 — 기준은 #77의 *"무엇을 저장하는가"*.
                    listOf(
                        ConsumableCatalog.EvalOnce,
                        ConsumableCatalog.TopMovesOnce,
                    ).forEach { item -> runConsumableGrant(item, amount = 1, consumableStore = store) }
                    consumables.refresh()
                },
            ) {
                Text(strings.settingsDevGrantAction)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 읽기 전용 ③ — **진단 로그를 앱 안에서 본다**(백로그 #79). `DiagnosticEventLog`가
        // 계속 쌓고 있는데 앱에서 볼 길이 없어, 폰만 손에 있으면 확인이 불가능했다.
        // ⚠️ 화면을 새로 만들지 않고 다이얼로그로 둔 이유는 셸 라인 예산이다(함정 3번).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.settingsDevDiagnosticLogTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.settingsDevDiagnosticLogSubtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            TextButton(onClick = { onShowDiagnosticLog() }) {
                Text(strings.settingsDevDiagnosticLogOpenAction)
            }
        }

        // ⚠️ **`BuildConfig.DEBUG`가 이 배치의 유일한 실제 경계다**(위 머리말 참고).
        // `DEBUG`는 `static final boolean`이라 release·playInternal에서는 컴파일 시점에
        // `false`로 접히고, 이 블록은 도달 불가가 된다.
        // · ⚠️ **다만 문구는 바이너리에 남는다** — 2026-09-03 release APK의 dex에서
        //   실제로 확인했다. `UiStringsKo` 등이 **데이터 클래스 생성자 인자**로 모든 문구를
        //   항상 만들기 때문에, 분기가 죽어도 문자열 상수는 살아 있다.
        //   **경로가 없는 것과 이름이 안 보이는 것은 다르다** — 이 배치가 보장하는 것은
        //   앞의 것뿐이고, APK를 뜯으면 2차의 존재는 드러난다. 그것으로 충분하다는 것이
        //   이 설계의 전제다(길게 누르기는 애초에 은닉이지 경계가 아니다).
        if (BuildConfig.DEBUG && isAdvancedDeveloperModeEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = strings.settingsDevTierAdvancedTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )

            // ⚠️ **영구 활성화 토글이 여기 있었다 — 버튼으로 바꿨다**(백로그 #78,
            // 2026-09-03 사용자 결정). 그 토글은 `PremiumSource.Purchase`를 저장소에
            // **영구 기록**했고, #26이 프리미엄을 월간 구독으로 옮기면서 판정 기준이
            // *"영구히 샀는가"* 가 아니라 **"지금 유효한가"** 로 바뀐다 — 사라질 상태를
            // 계속 테스트하게 두지 않는다.
            // ⚠️ **토글이 아니라 버튼인 이유**: 1시간 부여는 껐다 켜는 상태가 아니라
            // **사건**이다. Switch로 두면 "끄기"가 무엇을 뜻하는지 정의되지 않는다.
            // ✅ **부수 이득**: 이 버튼은 광고를 **띄우지 않고** 보상 경로만 밟으므로,
            // 실기에서 실제 AdMob 노출(자기 노출 = 정책 위반)을 만들지 않는다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.settingsDevAdGrantTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.settingsDevAdGrantSubtitle(premiumRemainingMinutes),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                TextButton(onClick = premium.simulateAdGrant) {
                    Text(strings.settingsDevAdGrantAction)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ⚠️ **2차인 이유**: 조각은 **광고 시청분**이다(#11) — 여기서 채워 주는 것은
            // 곧 광고를 건너뛰고 캐릭터를 얻는 무료 경로다. 1차(release에 실림)에 두면
            // 그것이 그대로 출시된다.
            // ⚠️ **획득으로 바로 넘기는 버튼은 없다** — `runBotCharacterShardSet`이
            // `required - 1`로 자른다. 캐릭터를 직접 심으면 유령 보상(#68)이 도달
            // 가능해지고 7·28일차 대체 보상이라는 미해결 결정을 끌어온다. "한 개 남기기"
            // 뒤에 광고를 한 번 보면 획득 루틴이 끝까지 밟힌다.
            BotCharacterCatalog.shardPathCharacters().forEach { character ->
                val required = (character.unlockSource as? BotUnlockSource.AdShards)?.required ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${strings.settingsDevShardTitle} · ${strings.botCharacterName(character)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (bots.isAvailable(character)) {
                                strings.botCharacterLabel(character)
                            } else {
                                "${bots.shardsFor(character)} / $required"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    TextButton(
                        onClick = {
                            runBotCharacterShardSet(character, required - 1, BotCollectionStore(context))
                            bots.refresh()
                        },
                    ) {
                        Text(strings.settingsDevShardAlmostAction)
                    }
                    TextButton(
                        onClick = {
                            runBotCharacterShardSet(character, 0, BotCollectionStore(context))
                            bots.refresh()
                        },
                    ) {
                        Text(strings.settingsDevShardClearAction)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ⚠️ **2차인 이유**: 이 버튼은 누를 때마다 1회권·캐릭터·무르기 영구 해금을
            // 실질적으로 **무료로 찍어낸다.** 기존 프리미엄 토글보다 악용 가치가 크다.
            // ⚠️ **부제에 지금 일차를 적는 것이 중요하다.** 8~13·15~20·22~27일차는
            // `isRewardedTier`가 false라 **원래 팝업이 안 뜬다.** 5·6일차도 그 조각
            // 캐릭터를 이미 다 모았으면 회차가 통째로 걸러진다. 숫자를 안 보여주면
            // "버튼이 고장났다"로 오진하게 되는 자리다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.settingsDevAttendanceTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.settingsDevAttendanceSubtitle(
                            current = attendanceDay,
                            next = attendanceDay + 1,
                            nextIsRewarded = isRewardedTier(attendanceDay + 1),
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                TextButton(
                    onClick = {
                        // 되감기만으로는 팝업이 안 뜬다 — 신호까지 올려야 계산이 다시 돈다
                        // (`AttendanceClaimReplaySignal`의 KDoc 참고).
                        runAttendanceDevDayRewind(AttendanceStore(context))
                        AttendanceClaimReplaySignal.request()
                    },
                ) {
                    Text(strings.settingsDevAttendanceAdvanceAction)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // **정식 출시 초기화(#63)를 손으로 다시 돌린다**(백로그 #80).
            // ⚠️ **지울 목록을 여기서 따로 쓰지 않는다** — 마커를 되감고
            // `ReleaseResetCoordinator`를 그대로 부른다. 목록을 두 벌로 쓰면 권한 저장소가
            // 늘 때 한쪽만 고쳐져, **개발자 도구가 실제 초기화와 다른 일을 하게 된다**
            // (함정 6번이 경고한 그 어긋남).
            // ⚠️ **가장 파괴적인 버튼이라 2차다** — 출석·캐릭터·1회권·프리미엄을 통째로 지운다.
            // ⚠️ 지울 것이 없으면 안내도 뜨지 않는다(#63의 설계: 안내는 **잃은 사람에게만**
            // 간다). 안내를 보려면 먼저 뭐라도 받아 둘 것 — 그래서 결과를 토스트로 가른다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.settingsDevReleaseResetTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.settingsDevReleaseResetSubtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                TextButton(
                    onClick = {
                        val wiped = runReleaseResetAgain(context)
                        // 지운 뒤 화면 사본을 전부 되읽는다 — 세 곳이 각자 저장소를 들고
                        // 있어서, 빠뜨리면 지워진 값이 화면에 남는다(#65와 같은 함정).
                        consumables.refresh()
                        bots.refresh()
                        premium.reload()
                        Toast.makeText(
                            context,
                            if (wiped) {
                                strings.settingsDevReleaseResetDoneMessage
                            } else {
                                strings.settingsDevReleaseResetNothingMessage
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(strings.settingsDevReleaseResetAction)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 버전 텍스트를 3초 이상 눌렀을 때(백로그 #77) — **2차(고급) 개발자 테스트**를 연다.
 *
 * ⚠️ **`BuildConfig.DEBUG`가 유일한 안전장치다.** 길게 누르기는 *은닉*이지 *경계*가 아니다 —
 * 제스처를 아는 사람은 그대로 실행한다. 2차에 붙는 것들(프리미엄 부여, 출석일 조작, 조각 수
 * 조절)은 **권한을 무료로 찍어내는** 것들이라, release·playInternal에서는 아예 존재하지 않아야
 * 한다. `isMinifyEnabled`가 켜진 그 둘에서는 R8이 이 분기를 통째로 지운다.
 * · `BuildConfig.DEBUG`는 **debug와 friend에서 true**, **playInternal과 release에서 false**다
 *   (`playInternal`만 `isDebuggable = false`로 되돌린다 — `build.gradle.kts`).
 *
 * ⚠️ **release에서는 토스트도 띄우지 않는다.** 안내를 띄우면 2차의 존재 자체를 광고하는 셈이라,
 * 조건이 맞지 않으면 **아무 일도 일어나지 않는 것**이 맞다.
 */
private fun onBuildInfoTap(
    tapCount: Int,
    isAdvancedEnabled: Boolean,
    onEnable: () -> Unit,
    context: Context,
    strings: UiStrings,
) {
    if (!BuildConfig.DEBUG) return
    if (isAdvancedEnabled) return
    val remaining = AdvancedDeveloperModeTapsRequired - tapCount
    if (remaining > 0) return
    onEnable()
    Toast.makeText(context, strings.settingsAdvancedDeveloperModeEnabledMessage, Toast.LENGTH_SHORT).show()
}

/**
 * 개발자 테스트 1차의 **읽기 전용 한 줄**(백로그 #77). 아무것도 쓰지 않으므로 release에 실려도
 * 무해하고, 그래서 1차에 둔다.
 *
 * ⚠️ 고정 `dp` 높이를 주지 않는다 — 글꼴 배율이 커지면 상자가 함께 자라야 한다(함정 9번).
 * 기존 두 컨트롤과 같은 `Row` + `Column(weight(1f))` 골격이라 배율에 저절로 따라간다.
 */
@Composable
private fun DeveloperInfoRow(title: String, value: String, onTap: () -> Unit = {}) {
    // ⚠️ **제목과 값을 좌우로 나누지 않는다 — 처음엔 그렇게 했고 배율 2.0배에서 깨졌다**
    // (2026-09-04, #81이 만든 배율 전환 버튼으로 발견했다). 값이 길면(`0.8.10 (810) · debug ·
    // test ads`) 폭을 다 먹어 제목이 `빌 / 드`로 쪼개진다. 위아래로 두면 서로 폭을 다투지 않고,
    // **주변 행들과 같은 모양**(제목 위, 부제 아래)이 되기도 한다.
    // ⚠️ 고정 높이는 여전히 금지다(함정 9번) — 높이를 지정하지 않아 배율을 저절로 따라간다.
    // ⚠️ 탭 대상은 **행 전체**다(제목만이 아니다) — 값 줄이 두 줄로 접히는 배율에서도
    // 누를 곳이 줄지 않아야 한다. 물결 효과는 두지 않는다: 이 행은 눌러도 아무것도 바뀌지
    // 않는 읽기 전용 줄이고, 눌리는 것처럼 보이면 조작할 수 있는 행으로 읽힌다(#81의 제보).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * 2차(고급) 개발자 테스트를 여는 **'빌드 정보' 행의 탭 수**(2026-09-04, 백로그 #84).
 *
 * ## ⚠️ 왜 길게 누르기를 버렸는가
 * #77은 3초 홀드를 썼는데 **세로 스크롤 안에서는 신뢰할 수 없었다.**
 * `waitForUpOrCancellation()`은 다른 핸들러가 제스처를 가져가면 3초 전에 `null`을 돌려주고,
 * 그러면 홀드도 탭도 아니어서 **조용히 아무 일도 일어나지 않는다.** `down`을 소비해도
 * 이후 MOVE 경쟁은 막지 못한다 — 재시작 뒤 그 자리까지 스크롤해 내려간 손가락은 이미
 * 스크롤과 경쟁하는 상태라, 사용자에게는 *"한 번은 됐는데 다시는 안 된다"* 로 보였다
 * (2026-09-04 사용자 제보 두 번).
 *
 * **탭은 이 경쟁을 아예 겪지 않는다** — 터치 슬롭을 넘기 전에 끝난다.
 *
 * ## ⚠️ 이 트리거의 은닉은 **위치**가 담당한다
 * '빌드 정보' 행은 **1차 섹션 안에만 존재한다.** 그래서 *"1차를 먼저 켜야 한다"* 는 조건이
 * 런타임 검사가 아니라 **구조로** 성립한다 — 1차가 꺼져 있으면 누를 대상 자체가 화면에 없다.
 * 실제 경계는 여전히 [BuildConfig.DEBUG]다(은닉은 오조작 방지일 뿐이다).
 */
private const val AdvancedDeveloperModeTapsRequired = 10
