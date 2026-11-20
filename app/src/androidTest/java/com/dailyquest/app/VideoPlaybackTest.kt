package com.dailyquest.app

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.ui.proof.VideoDownloader
import com.dailyquest.app.ui.proof.VideoDownloads
import com.dailyquest.app.ui.proof.VideoProofView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** 실제 mp4(androidTest 에셋)로 로컬·원격(내려받기 후 로컬 재생) 경로를 에뮬레이터 디코더에서 검증한다. 네트워크는 쓰지 않는다. */
class VideoPlaybackTest {
    @get:Rule val compose = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("sample.mp4").readBytes()
    private lateinit var dir: File
    private var mode = "ok"
    private var calls = 0

    @Before fun setUp() {
        dir = File(ctx.cacheDir, "vt_${System.nanoTime()}").also { it.mkdirs() }
        val client = OkHttpClient.Builder().addInterceptor { c ->
            calls++
            when (mode) {
                "fail" -> throw IOException("down")
                "404" -> Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(404).message("nf").body("".toResponseBody()).build()
                else -> Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                    .body(bytes.toResponseBody("video/mp4".toMediaType())).build()
            }
        }.build()
        VideoDownloads.setForTest(VideoDownloader(File(dir, "dl"), client))
    }

    @After fun tearDown() { VideoDownloads.setForTest(null); dir.deleteRecursively() }

    private fun show(src: String) = compose.setContent { VideoProofView(src, Modifier.fillMaxWidth().height(300.dp), tag = "v") }
    // 짧은 영상은 단언 전에 끝날 수 있어 재생 중/종료 어느 쪽이든 오류 없이 컨트롤이 보이는지만 확인한다.
    private fun assertPlayedWithoutError() {
        compose.onNodeWithTag("vControl").assertExists()
        compose.onNodeWithTag("vError").assertDoesNotExist()
    }
    private fun waitTag(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    // 실제 서버에서 재생에 실패했던 인증 영상과 같은 구성(H.264 176x144 + AMR-NB, moov 뒤쪽)의 파일.
    @Test fun serverStyleH264AmrVideoPlaysAndStaysPlayerAlive() {
        val real = InstrumentationRegistry.getInstrumentation().context.assets.open("proof_h264_amrnb.mp4").readBytes()
        val f = File(dir, "real.mp4").also { it.writeBytes(real) }
        show(f.path)
        waitTag("vThumb"); compose.onNodeWithTag("vThumb").performClick()
        waitTag("vControl", 20_000)
        assertPlayedWithoutError()
    }

    @Test fun localVideoPlays() {
        val f = File(dir, "local.mp4").also { it.writeBytes(bytes) }
        show(f.path)
        waitTag("vThumb"); compose.onNodeWithTag("vThumb").performClick()
        waitTag("vControl")
        assertPlayedWithoutError()
    }

    @Test fun remoteVideoIsDownloadedOnceThenPlaysLocally() {
        show("https://example.invalid/proofs/a.mp4")
        waitTag("vThumb"); compose.onNodeWithTag("vThumb").performClick()
        waitTag("vControl")
        assertPlayedWithoutError()
        assertEquals(1, calls)
    }

    @Test fun networkFailureShowsKoreanErrorAndRetryRecovers() {
        mode = "fail"
        show("https://example.invalid/proofs/b.mp4")
        waitTag("vThumb"); compose.onNodeWithTag("vThumb").performClick()
        waitTag("vError")
        compose.onNodeWithTag("vError").assertTextContains("네트워크 문제로 영상을 불러오지 못했어요")
        mode = "ok"
        compose.onNodeWithTag("vRetry").performClick()
        waitTag("vControl")
    }

    @Test fun missingFileShowsNotFoundMessage() {
        mode = "404"
        show("https://example.invalid/proofs/c.mp4")
        waitTag("vThumb"); compose.onNodeWithTag("vThumb").performClick()
        waitTag("vError")
        compose.onNodeWithTag("vError").assertTextContains("영상 파일을 찾을 수 없어요")
    }
}
