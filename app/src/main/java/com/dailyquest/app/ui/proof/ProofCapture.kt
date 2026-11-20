package com.dailyquest.app.ui.proof

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.dailyquest.app.data.proof.AndroidProofMediaPreparer
import com.dailyquest.app.data.proof.ProofValidation
import java.io.File
import java.util.UUID

/** 영상 촬영: 길이는 서버가 아닌 앱이 지켜야 하므로 카메라 앱에 5초 제한과 낮은 화질을 요청한다. */
private class CaptureVideoContract : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, (ProofValidation.VIDEO_MAX_MS / 1000).toInt())
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}

class ProofCaptureActions(
    val takePhoto: () -> Unit,
    val recordVideo: () -> Unit,
    val pickPhoto: () -> Unit,
    val pickVideo: () -> Unit,
)

/**
 * 카메라 촬영(사진·영상)과 시스템 사진 선택기(사진·영상)를 묶는다. 카메라·저장소 권한은 요청하지 않는다.
 * 촬영 결과는 캐시의 FileProvider URI로 받고, 결과 URI는 [onSelected]로 넘겨 검증·복사는 ViewModel이 한다.
 */
@Composable
fun rememberProofCapture(onSelected: (String) -> Unit, onError: (String) -> Unit): ProofCaptureActions {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun newTarget(ext: String): Uri {
        val dir = File(context.cacheDir, AndroidProofMediaPreparer.CAPTURE_DIR).apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.$ext")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f).also { pending = it.toString() }
    }

    fun launchSafely(block: () -> Unit) {
        try { block() } catch (_: ActivityNotFoundException) { onError("이 기기에서 사용할 수 있는 카메라 앱이 없어요. 앨범에서 선택해 주세요.") }
    }

    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.takeIf { ok }?.let(onSelected)
        pending = null
    }
    val video = rememberLauncherForActivityResult(CaptureVideoContract()) { ok ->
        pending?.takeIf { ok }?.let(onSelected)
        pending = null
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onSelected(it.toString()) } }

    return remember(photo, video, pick) {
        ProofCaptureActions(
            takePhoto = { launchSafely { photo.launch(newTarget("jpg")) } },
            recordVideo = { launchSafely { video.launch(newTarget("mp4")) } },
            pickPhoto = { launchSafely { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            pickVideo = { launchSafely { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) } },
        )
    }
}
