package com.dailyquest.app.data.decor

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 방·가구를 화면에 놓는 데 필요한 정보 (STEP 10-1).
 *
 * 서버는 가구의 이름·가격·이미지 주소만 주고, 몇 칸을 차지하는지·이미지의 어느 점을 바닥에 맞추는지는 주지 않는다.
 * 그 정보는 에셋 팩(`DailyQuest_Room16_AppPack_1080`)의 `assets_manifest.json`에서 필요한 값만 골라
 * `assets/decor/room16_catalog.json`으로 넣어 두었다. 서버 아이템과는 `code`로 연결한다.
 *
 * 좌표는 모두 **방 이미지(1080×810) 픽셀 기준**이다. 화면에 그릴 때는 방과 가구에 같은 배율을 곱한다.
 */
class RoomCatalog(val room: RoomGeometry, furniture: List<FurnitureSpec>) {
    private val byCode: Map<String, FurnitureSpec> = furniture.associateBy { it.code }

    val furniture: Collection<FurnitureSpec> get() = byCode.values

    /** 서버 아이템의 `code`로 찾는다. 앱이 모르는 가구면 null(새 에셋이 서버에만 먼저 올라온 경우). */
    fun furniture(code: String?): FurnitureSpec? = code?.let(byCode::get)

    companion object {
        /** `assets/` 안의 경로. */
        const val ASSET_PATH = "decor/room16_catalog.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** 형식이 틀리면 [IllegalArgumentException]. 잘못된 파일이 들어오면 앱을 만들 때 테스트에서 바로 드러나게 한다. */
        fun parse(text: String): RoomCatalog {
            val file = json.decodeFromString<CatalogFile>(text)
            require(file.schemaVersion == 1) { "모르는 배치 정보 형식: ${file.schemaVersion}" }
            return RoomCatalog(file.room.toGeometry(), file.furniture.map { it.toSpec() })
        }
    }
}

/** 앱 전체에서 한 번만 읽는다. */
object RoomCatalogs {
    @Volatile private var cached: RoomCatalog? = null

    fun load(context: Context): RoomCatalog = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets.open(RoomCatalog.ASSET_PATH)
            .bufferedReader().use { RoomCatalog.parse(it.readText()) }
            .also { cached = it }
    }
}

/** 방 이미지 안의 한 점 (픽셀). */
data class PxPoint(val x: Float, val y: Float)

/**
 * 방 이미지와 바닥 격자의 관계.
 * 칸 (cx, cy)의 바닥 위치 = [gridOrigin] + cx × [gridAxisX] + cy × [gridAxisY]. 칸 (0, 0)은 방 안쪽 구석이다.
 */
data class RoomGeometry(
    val code: String,
    val widthPx: Int,
    val heightPx: Int,
    val columns: Int,
    val rows: Int,
    val gridOrigin: PxPoint,
    val gridAxisX: PxPoint,
    val gridAxisY: PxPoint,
)

/** 가구 방향. 이미지를 돌리지 않고 방향마다 따로 그린 PNG를 쓴다. */
enum class Rotation(val degrees: Int, private val fileSuffix: String) {
    R0(0, ""), R90(90, "_r090"), R180(180, "_r180"), R270(270, "_r270");

    /** 회전 버튼을 누를 때의 다음 방향. */
    fun next(): Rotation = entries[(ordinal + 1) % entries.size]

    /**
     * 서버 `imageUrl`(0° 그림)에서 이 방향 그림의 주소를 만든다. 규칙: `.png` 앞에 `_r090`·`_r180`·`_r270`을 붙인다(연동 규칙 11-2).
     * `.png`로 끝나지 않는 주소는 규칙을 적용할 수 없어 그대로 돌려준다.
     */
    fun spriteUrl(imageUrl: String): String {
        if (fileSuffix.isEmpty()) return imageUrl
        val dot = imageUrl.lastIndexOf(".png")
        return if (dot < 0) imageUrl else imageUrl.substring(0, dot) + fileSuffix + imageUrl.substring(dot)
    }

    companion object {
        /** 서버 `rotation`(0~359)을 방향으로. 90도 단위가 아니면 null. 360·-90처럼 한 바퀴 넘는 값도 받는다. */
        fun fromDegrees(degrees: Int): Rotation? = entries.firstOrNull { it.degrees == Math.floorMod(degrees, 360) }
    }
}

/** 바닥에 까는 것(러그)은 다른 가구 아래에 먼저 그리고, 겹쳐 놓을 수 있다. */
enum class PlacementLayer { FLOOR_DECOR, FURNITURE }

/**
 * 한 방향의 그림.
 * - [anchor]: 그림을 놓는 기준점(그림 아래 가운데).
 * - [gridAnchorOffset]: 가구가 차지한 칸들의 바닥 가운데에서 [anchor]까지의 거리. 기준점이 실제 바닥 가운데와 조금 다르기 때문이다.
 * - [gridWidth]·[gridDepth]: 이 방향에서 차지하는 칸 수. 90°·270°에서는 가로·세로가 바뀐다.
 */
data class SpriteSpec(
    val rotation: Rotation,
    val widthPx: Int,
    val heightPx: Int,
    val anchor: PxPoint,
    val gridAnchorOffset: PxPoint,
    val gridWidth: Int,
    val gridDepth: Int,
)

data class FurnitureSpec(
    val code: String,
    val gridWidth: Int,
    val gridDepth: Int,
    val layer: PlacementLayer,
    /** false면 다른 가구와 칸이 겹쳐도 된다(러그). */
    val blocksPlacement: Boolean,
    val sprites: Map<Rotation, SpriteSpec>,
) {
    fun sprite(rotation: Rotation): SpriteSpec = sprites.getValue(rotation)
}

// ---- JSON 파일 형식 (에셋 팩에서 만든 그대로) ----

@Serializable
private class CatalogFile(val schemaVersion: Int, val room: RoomJson, val furniture: List<FurnitureJson>)

@Serializable
private class RoomJson(
    val code: String,
    val widthPx: Int,
    val heightPx: Int,
    val columns: Int,
    val rows: Int,
    val gridOriginPx: List<Float>,
    val gridAxisXPx: List<Float>,
    val gridAxisYPx: List<Float>,
) {
    fun toGeometry(): RoomGeometry {
        require(widthPx > 0 && heightPx > 0 && columns > 0 && rows > 0) { "방 크기가 잘못됨: $code" }
        return RoomGeometry(
            code, widthPx, heightPx, columns, rows,
            gridOriginPx.toPoint("방 기준점"), gridAxisXPx.toPoint("방 X축"), gridAxisYPx.toPoint("방 Y축"),
        )
    }
}

@Serializable
private class FurnitureJson(
    val code: String,
    val gridWidth: Int,
    val gridDepth: Int,
    val layer: String,
    val blocksPlacement: Boolean,
    val sprites: List<SpriteJson>,
) {
    fun toSpec(): FurnitureSpec {
        require(gridWidth > 0 && gridDepth > 0) { "$code: 차지하는 칸 수가 잘못됨" }
        val layer = when (layer) {
            "floor_decor" -> PlacementLayer.FLOOR_DECOR
            "furniture" -> PlacementLayer.FURNITURE
            else -> throw IllegalArgumentException("$code: 모르는 층 '$layer'")
        }
        val sprites = sprites.map { it.toSpec(code) }.associateBy { it.rotation }
        require(sprites.size == this.sprites.size) { "$code: 같은 방향 그림이 두 번 있음" }
        Rotation.entries.forEach { r ->
            val s = requireNotNull(sprites[r]) { "$code: ${r.degrees}° 그림이 없음" }
            val quarterTurn = r == Rotation.R90 || r == Rotation.R270
            val (w, d) = if (quarterTurn) gridDepth to gridWidth else gridWidth to gridDepth
            require(s.gridWidth == w && s.gridDepth == d) { "$code: ${r.degrees}°의 칸 수가 ${w}×$d 가 아님" }
            require(s.widthPx > 0 && s.heightPx > 0) { "$code: ${r.degrees}° 그림 크기가 잘못됨" }
        }
        return FurnitureSpec(code, gridWidth, gridDepth, layer, blocksPlacement, sprites)
    }
}

@Serializable
private class SpriteJson(
    val rotation: Int,
    val widthPx: Int,
    val heightPx: Int,
    val anchorPx: List<Float>,
    val gridAnchorOffsetPx: List<Float>,
    val gridWidth: Int,
    val gridDepth: Int,
) {
    fun toSpec(code: String): SpriteSpec {
        val r = requireNotNull(Rotation.fromDegrees(rotation)) { "$code: 90도 단위가 아닌 방향 $rotation" }
        return SpriteSpec(
            r, widthPx, heightPx,
            anchorPx.toPoint("$code 기준점"), gridAnchorOffsetPx.toPoint("$code 보정값"),
            gridWidth, gridDepth,
        )
    }
}

private fun List<Float>.toPoint(what: String): PxPoint {
    require(size == 2) { "$what: 값이 2개가 아님" }
    return PxPoint(this[0], this[1])
}
