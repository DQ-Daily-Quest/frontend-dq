package com.dailyquest.app.data.proof

/**
 * 인증 미디어·문구 검증. 서버가 실제로 막는 것은 MIME(jpg/png/webp/mp4)과 용량(이미지 10MB, 영상 50MB)뿐이다.
 * api.md 합의(영상 5초·10MB)는 서버가 검사하지 않으므로 클라이언트가 지킨다. 카메라 앱이 한도를 조금 넘겨 녹화하는 경우를 위해 길이 허용 오차를 둔다.
 */
object ProofValidation {
    const val IMAGE_MAX_BYTES = 10L * 1024 * 1024
    const val VIDEO_MAX_BYTES = 10L * 1024 * 1024
    const val VIDEO_MAX_MS = 5_000L
    const val VIDEO_TOLERANCE_MS = 500L
    const val TEXT_MAX = 300

    val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")
    const val VIDEO_MIME = "video/mp4"

    fun mediaTypeOf(mime: String?): ProofMediaType? = when (mime?.lowercase()) {
        in IMAGE_MIMES -> ProofMediaType.IMAGE
        VIDEO_MIME -> ProofMediaType.VIDEO
        else -> null
    }

    fun extensionOf(mime: String): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "mp4"
    }

    /** 파일 앞부분의 시그니처로 실제 형식을 판별한다. 확장자나 선언된 MIME은 믿지 않는다. */
    fun sniffMime(header: ByteArray): String? {
        fun at(i: Int, vararg b: Int) = header.size >= i + b.size && b.indices.all { header[i + it] == b[it].toByte() }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp"
            at(4, 0x66, 0x74, 0x79, 0x70) -> VIDEO_MIME
            else -> null
        }
    }

    /** 오류 안내 문구, 통과하면 null. [durationMs]는 영상에만 필요하다. */
    fun validateMedia(mime: String?, sizeBytes: Long, durationMs: Long?): String? {
        val type = mediaTypeOf(mime) ?: return "jpg, png, webp 사진 또는 mp4 영상만 올릴 수 있어요."
        if (sizeBytes <= 0) return "비어 있는 파일이에요. 다른 파일을 선택해 주세요."
        if (type == ProofMediaType.IMAGE && sizeBytes > IMAGE_MAX_BYTES) return "사진은 최대 10MB까지 올릴 수 있어요."
        if (type == ProofMediaType.VIDEO) {
            if (sizeBytes > VIDEO_MAX_BYTES) return "영상은 최대 10MB까지 올릴 수 있어요."
            if (durationMs == null) return "영상 길이를 확인할 수 없어요. 다른 영상을 선택해 주세요."
            if (durationMs > VIDEO_MAX_MS + VIDEO_TOLERANCE_MS) return "영상은 5초 이내만 올릴 수 있어요."
        }
        return null
    }

    fun validateText(text: String): String? =
        if (text.trim().length > TEXT_MAX) "인증 문구는 최대 ${TEXT_MAX}자예요." else null

    /** 서버는 공백만 있는 문구를 null로 저장하므로 보내기 전에 같은 방식으로 정리한다. */
    fun normalizeText(text: String): String? = text.trim().ifEmpty { null }
}
