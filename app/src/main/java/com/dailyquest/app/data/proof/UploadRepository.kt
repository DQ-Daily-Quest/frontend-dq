package com.dailyquest.app.data.proof

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** 파일 업로드 API 호출 계층. 업로드는 멱등하지 않으므로 자동 재시도하지 않는다(DqApi의 No-Retry 헤더). */
class UploadRepository(private val api: DqApi) {

    /** 파일은 스트리밍으로 전송한다(전체를 메모리에 올리지 않는다). 서버 필드명은 file, purpose는 쿼리다. */
    suspend fun uploadProofMedia(file: File, mime: String): ApiResult<UploadedMedia> = apiCall {
        val part = MultipartBody.Part.createFormData(
            "file", "proof.${ProofValidation.extensionOf(mime)}", file.asRequestBody(mime.toMediaType()),
        )
        api.upload(part, PURPOSE_PROOF).toUploaded()
    }

    companion object {
        const val PURPOSE_PROOF = "PROOF"
    }
}
