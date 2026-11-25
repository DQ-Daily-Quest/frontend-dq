package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.mission.MissionValidation
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.BottomCta
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.FormBody
import com.dailyquest.app.ui.components.InfoNote
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.mission.EditMissionViewModel
import com.dailyquest.app.ui.mission.MISSION_EDIT_OUTCOME_UNKNOWN_MESSAGE
import com.dailyquest.app.ui.mission.MissionDueOption
import com.dailyquest.app.ui.theme.DQColors

/** 멤버 미션 수정 — 서버가 허용하는 내용(content)과 기한(dueAt)만 편집한다. 받는 사람·보상은 바꿀 수 없다. */
@Composable
fun EditMissionScreen(viewModel: EditMissionViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.saved) {
        if (!ui.saved) return@LaunchedEffect
        viewModel.consumeSaved()
        nav.backOrHome()
    }

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("editMission")) {
            NavHeader("미션 수정", divider = true)
            when {
                ui.loading -> Txt("불러오는 중…", size = 13f, color = DQColors.Muted, modifier = Modifier.padding(24.dp))
                ui.loadError != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt(ui.loadError.orEmpty(), size = 13f, color = DQColors.Orange, modifier = Modifier.testTag("editMissionLoadError"))
                    Btn("다시 불러오기", Modifier.height(44.dp), size = 14f) { viewModel.load() }
                }
                else -> FormBody {
                    FieldLabel("미션 내용", first = true)
                    DqTextField(
                        ui.content, viewModel::onContentChange, "2~100자로 입력해 주세요",
                        height = 50.dp, fontSize = 14.5f, weight = FontWeight.Medium, maxLength = MissionValidation.CONTENT_MAX,
                    )
                    ui.contentError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }

                    FieldLabel("기한")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("변경 안 함", ui.due == null) { viewModel.onDueChange(null) }
                        MissionDueOption.entries.forEach { o -> Chip(o.label, o == ui.due) { viewModel.onDueChange(o) } }
                    }
                    InfoNote("기한을 바꾸면 지금부터 선택한 시간 뒤로 설정돼요. 받는 사람과 보상은 바꿀 수 없어요.")
                    ui.serverError?.let {
                        Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag("editMissionError"))
                    }
                    if (ui.outcomeUnknown) {
                        Txt(
                            MISSION_EDIT_OUTCOME_UNKNOWN_MESSAGE, size = 12.5f, color = DQColors.Orange,
                            modifier = Modifier.padding(top = 16.dp).testTag("editMissionOutcomeUnknown"),
                        )
                    }
                }
            }
        }
        if (!ui.loading && ui.loadError == null) {
            BottomCta {
                Btn(
                    if (ui.submitting) "저장 중…" else "저장하기",
                    Modifier.weight(1f).height(54.dp), size = 16f, enabled = !ui.submitting,
                ) { viewModel.submit() }
            }
        }
    }
}
