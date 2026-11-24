package com.dailyquest.app.data.shop

import com.dailyquest.app.data.decor.Rotation
import com.dailyquest.app.data.decor.toItem
import com.dailyquest.app.data.remote.dto.DecorItemDto
import com.dailyquest.app.data.remote.dto.ItemDto
import com.dailyquest.app.data.remote.dto.MyItemDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * STEP 10-8 상점용 썸네일(`thumbnailUrl`, 백엔드 11/24 추가).
 * 규칙: 상점·가구 고르기 목록은 thumbnailUrl(없으면 imageUrl), 방에 그릴 때는 imageUrl(+ _r090 등).
 */
class ThumbnailUrlTest {
    private val s3 = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/furniture/desk_oak"
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun shopItemUsesThumbnailForCardAndFallsBackToImage() {
        // 서버 응답 그대로 (GET /shop/items)
        val withThumb = json.decodeFromString(
            ItemDto.serializer(),
            """{"itemId":142,"name":"오크 서랍 책상","type":"FURNITURE","category":"DESK","price":300,
               "imageUrl":"$s3/desk_oak.png","thumbnailUrl":"$s3/desk_oak_thumb.png","isDefault":false,"owned":false,
               "code":"desk_oak","gender":null}""",
        ).toShopItem()
        assertEquals("$s3/desk_oak_thumb.png", withThumb.cardImageUrl)
        assertEquals("방 그림 주소는 그대로", "$s3/desk_oak.png", withThumb.imageUrl)

        val noThumb = ItemDto(1, "예전 아이템", "AVATAR", "HAT", 100, imageUrl = "https://x/a.png").toShopItem()
        assertNull(noThumb.thumbnailUrl)
        assertEquals("https://x/a.png", noThumb.cardImageUrl)
    }

    @Test fun inventoryItemUsesThumbnailForCard() {
        val mine = MyItemDto(142, "오크 서랍 책상", "FURNITURE", "DESK", 300, imageUrl = "$s3/desk_oak.png", thumbnailUrl = "$s3/desk_oak_thumb.png")
            .toInventoryItem()
        assertEquals("$s3/desk_oak_thumb.png", mine.cardImageUrl)
        assertEquals("https://x/b.png", MyItemDto(2, "b", imageUrl = "https://x/b.png").toInventoryItem().cardImageUrl)
    }

    @Test fun decorListUsesThumbnailButRoomStillDrawsDirectionalImages() {
        val item = DecorItemDto(142, "오크 서랍 책상", "DESK", "$s3/desk_oak.png", null, "desk_oak", "$s3/desk_oak_thumb.png").toItem()
        assertEquals("가구 고르기 목록", "$s3/desk_oak_thumb.png", item.listImageUrl)
        // 방에 그릴 때는 썸네일이 아니라 방향별 그림
        assertEquals("$s3/desk_oak.png", item.spriteUrl(Rotation.R0))
        assertEquals("$s3/desk_oak_r090.png", item.spriteUrl(Rotation.R90))

        val old = DecorItemDto(142, "오크 서랍 책상", "DESK", "$s3/desk_oak.png", null, "desk_oak").toItem()
        assertEquals("썸네일이 없으면 원래 그림", "$s3/desk_oak.png", old.listImageUrl)
    }
}
