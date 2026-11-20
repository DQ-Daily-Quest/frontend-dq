package com.dailyquest.app.ui.proof

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.proof.PreparedMedia
import com.dailyquest.app.data.proof.ProofMediaType
import com.dailyquest.app.data.proof.ProofValidation
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.BottomCta
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextArea
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.FormBody
import com.dailyquest.app.ui.components.InfoNote
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 미션 인증하기 — 사진·영상 선택/촬영, 한마디, 제출. 기존 Mock 인증 화면(MissionVerifyScreen)의 구성을 따른다. */
@Composable
fun ProofSubmitScreen(missionId: Long, roomId: Long, viewModel: ProofSubmitViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val capture = rememberProofCapture(viewModel::onMediaSelected) { msg -> viewModel.onMediaError(msg) }

    LaunchedEffect(ui.done) {
        val done = ui.done ?: return@LaunchedEffect
        viewModel.consumeDone()
        val target = done.proofId?.let { Routes.proofDetail(it, roomId) } ?: Routes.missionDetail(missionId, roomId)
        nav.navigate(target) { popUpTo(Routes.proofSubmit(missionId, roomId)) { inclusive = true } }
    }

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("proofSubmit")) {
            NavHeader("미션 인증하기", divider = true)
            FormBody {
                val media = ui.media
                if (media != null) {
                    MediaPreview(media)
                    if (!ui.busy) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("선택 취소", Modifier.weight(1f).height(40.dp), BtnStyle.Outline, size = 13f) { viewModel.clearMedia() }
                        }
                    }
                } else {
                    Txt("사진이나 영상으로 인증해요", size = 14f, weight = Bold, modifier = Modifier.padding(start = 4.dp))
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val enabled = !ui.busy
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("사진 촬영", Modifier.weight(1f).height(44.dp).testTag("proofTakePhoto"), BtnStyle.Soft, size = 13f, enabled = enabled) { capture.takePhoto() }
                        Btn("영상 촬영", Modifier.weight(1f).height(44.dp).testTag("proofRecordVideo"), BtnStyle.Soft, size = 13f, enabled = enabled) { capture.recordVideo() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("앨범 사진", Modifier.weight(1f).height(44.dp).testTag("proofPickPhoto"), BtnStyle.Outline, size = 13f, enabled = enabled) { capture.pickPhoto() }
                        Btn("앨범 영상", Modifier.weight(1f).height(44.dp).testTag("proofPickVideo"), BtnStyle.Outline, size = 13f, enabled = enabled) { capture.pickVideo() }
                    }
                }
                ui.mediaError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(top = 8.dp, start = 4.dp).testTag("proofMediaError")) }
                if (ui.preparing) Txt("파일을 확인하는 중이에요…", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                Txt(
                    "사진 JPG·PNG·WebP · 영상 MP4 · 각 최대 10MB · 영상은 5초 이내",
                    size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )

                FieldLabel("한마디")
                DqTextArea(ui.text, viewModel::onTextChange, "오늘은 어땠어? (선택)", max = ProofValidation.TEXT_MAX)
                ui.textError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }

                InfoNote("승인이 확정되기 전에는 보상이 지급되지 않아요")
                ui.error?.let {
                    Txt(it, size = 12.5f, color = DQColors.Orange, modifier = Modifier.padding(top = 12.dp, start = 4.dp).testTag("proofSubmitError"))
                }
            }
        }
        BottomCta {
            val label = when (ui.phase) {
                SubmitPhase.UPLOADING -> "업로드 중…"
                SubmitPhase.SUBMITTING -> "제출 중…"
                SubmitPhase.CHECKING -> "제출 여부 확인 중…"
                SubmitPhase.IDLE -> when {
                    ui.outcomeUnknown -> "제출 여부 확인하고 다시 시도"
                    ui.uploaded -> "제출 다시 시도"
                    ui.error != null -> "다시 시도"
                    else -> "인증 올리기"
                }
            }
            if (ui.phase == SubmitPhase.UPLOADING) {
                Btn("취소", Modifier.height(54.dp).weight(0.4f).testTag("proofCancelUpload"), BtnStyle.Outline, size = 15f) { viewModel.cancelUpload() }
            }
            Btn(label, Modifier.weight(1f).height(54.dp).testTag("proofSubmitButton"), size = 16f, enabled = ui.canSubmit) { viewModel.submit() }
        }
    }
}

@Composable
private fun MediaPreview(media: PreparedMedia) {
    val shape = RoundedCornerShape(22.dp)
    if (media.type == ProofMediaType.VIDEO) {
        VideoProofView(
            media.file.path, Modifier.fillMaxWidth().height(282.dp).clip(shape).border(1.dp, DQColors.Line, shape).testTag("proofPreview"),
            knownDurationMs = media.durationMs, tag = "proofPreviewVideo",
        )
        return
    }
    val thumb by produceState<Bitmap?>(null, media.file) {
        value = withContext(Dispatchers.IO) { runCatching { decodePreview(media) }.getOrNull() }
    }
    Box(
        Modifier.fillMaxWidth().height(282.dp).clip(shape).background(DQColors.White)
            .border(1.dp, DQColors.Line, shape).testTag("proofPreview"),
        contentAlignment = Alignment.Center,
    ) {
        thumb?.let { Image(it.asImageBitmap(), "선택한 미디어 미리보기", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private fun decodePreview(media: PreparedMedia): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(media.file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
    return BitmapFactory.decodeFile(media.file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
