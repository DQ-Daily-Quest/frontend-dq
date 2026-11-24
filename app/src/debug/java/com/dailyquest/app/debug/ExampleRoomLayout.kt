package com.dailyquest.app.debug

import android.content.Context
import com.dailyquest.app.data.decor.DecorItem
import com.dailyquest.app.data.decor.GridPlacement
import com.dailyquest.app.data.decor.PlacedFurniture
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.data.decor.Rotation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 에셋 팩 배치 예시 54개 (STEP 10-4). debug 빌드 assets `decor/example_layout.json`
 * (tools/make_room_layout_fixture.py로 만들었고, 단위 테스트 RoomLayoutTest도 같은 파일을 쓴다).
 * 서버 없이 실제 S3 그림으로 그린다. 에셋 팩 미리보기(preview/room_example_1080.png)와 같은 모습이어야 한다.
 */
object ExampleRoomLayout {
    const val S3_FURNITURE = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/furniture"
    const val ROOM_URL = "$S3_FURNITURE/room_16x16/room_16x16.png"

    fun load(context: Context, catalog: RoomCatalog): List<PlacedFurniture> {
        val text = context.assets.open("decor/example_layout.json").bufferedReader().use { it.readText() }
        val items = Json.parseToJsonElement(text).jsonObject["items"]!!.jsonArray
        return items.mapIndexed { i, e ->
            val o = e.jsonObject
            val code = o["code"]!!.jsonPrimitive.content
            val spec = requireNotNull(catalog.furniture(code)) { "배치 정보에 없는 가구: $code" }
            PlacedFurniture(
                item = DecorItem(i + 1L, code, "DECO", code, "$S3_FURNITURE/$code/$code.png"),
                placement = GridPlacement(
                    spec,
                    o["gridX"]!!.jsonPrimitive.int,
                    o["gridY"]!!.jsonPrimitive.int,
                    Rotation.fromDegrees(o["rotation"]!!.jsonPrimitive.int)!!,
                ),
            )
        }
    }
}
