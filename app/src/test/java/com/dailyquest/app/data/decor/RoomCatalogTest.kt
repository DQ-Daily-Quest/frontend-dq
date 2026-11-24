package com.dailyquest.app.data.decor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** STEP 10-1: 앱에 넣은 배치 정보 파일(assets/decor/room16_catalog.json)을 그대로 읽어 확인한다. */
class RoomCatalogTest {
    private val catalog by lazy {
        // 단위 테스트는 app 모듈 폴더에서 돈다. 앱이 읽는 경로 상수를 그대로 써서 파일 위치가 바뀌면 여기서 실패하게 한다.
        RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText())
    }

    @Test fun roomIs16x16On1080Image() {
        val room = catalog.room
        assertEquals("room_16x16", room.code)
        assertEquals(1080, room.widthPx)
        assertEquals(810, room.heightPx)
        assertEquals(16, room.columns)
        assertEquals(16, room.rows)
        // 에셋 팩 assets_manifest.json 값 (소수 4자리로 줄임)
        assertEquals(PxPoint(540f, 237.1285f), room.gridOrigin)
        assertEquals(PxPoint(-31.6406f, 15.8203f), room.gridAxisX)
        assertEquals(PxPoint(31.6406f, 15.8203f), room.gridAxisY)
    }

    @Test fun has50FurnitureWithUniqueCodes() {
        assertEquals(50, catalog.furniture.size)
        assertEquals(50, catalog.furniture.map { it.code }.toSet().size)
        assertNull("방은 가구 목록에 없다", catalog.furniture("room_16x16"))
    }

    @Test fun findsFurnitureByServerCode() {
        val desk = catalog.furniture("desk_oak")
        assertNotNull(desk)
        desk!!
        assertEquals(2, desk.gridWidth)
        assertEquals(1, desk.gridDepth)
        // 에셋 팩 README의 예: 2×1 책상은 기본 방향에서 87×76px
        assertEquals(87, desk.sprite(Rotation.R0).widthPx)
        assertEquals(76, desk.sprite(Rotation.R0).heightPx)
        assertNull(catalog.furniture("no_such_item"))
        assertNull(catalog.furniture(null))
    }

    @Test fun everyFurnitureHasAllFourDirections() {
        catalog.furniture.forEach { f ->
            assertEquals(f.code, Rotation.entries.toSet(), f.sprites.keys)
        }
    }

    @Test fun quarterTurnsSwapOccupiedCells() {
        catalog.furniture.forEach { f ->
            assertEquals(f.code, f.gridWidth to f.gridDepth, f.sprite(Rotation.R0).let { it.gridWidth to it.gridDepth })
            assertEquals(f.code, f.gridWidth to f.gridDepth, f.sprite(Rotation.R180).let { it.gridWidth to it.gridDepth })
            assertEquals(f.code, f.gridDepth to f.gridWidth, f.sprite(Rotation.R90).let { it.gridWidth to it.gridDepth })
            assertEquals(f.code, f.gridDepth to f.gridWidth, f.sprite(Rotation.R270).let { it.gridWidth to it.gridDepth })
        }
        val sofa = catalog.furniture("sofa_oat")!!
        assertEquals(1, sofa.sprite(Rotation.R90).gridWidth)
        assertEquals(2, sofa.sprite(Rotation.R90).gridDepth)
    }

    @Test fun anchorIsBottomCenterOfEachSprite() {
        catalog.furniture.forEach { f ->
            f.sprites.values.forEach { s ->
                assertEquals("${f.code} ${s.rotation}", s.widthPx / 2f, s.anchor.x, 0.01f)
                assertEquals("${f.code} ${s.rotation}", s.heightPx.toFloat(), s.anchor.y, 0.01f)
            }
        }
    }

    @Test fun onlyRugIsFloorDecorAndCanOverlap() {
        val rug = catalog.furniture("rug_sage")!!
        assertEquals(PlacementLayer.FLOOR_DECOR, rug.layer)
        assertFalse(rug.blocksPlacement)
        val others = catalog.furniture.filter { it.code != "rug_sage" }
        assertTrue(others.all { it.layer == PlacementLayer.FURNITURE && it.blocksPlacement })
    }

    @Test fun everyFurnitureFitsInRoom() {
        catalog.furniture.forEach { f ->
            assertTrue(f.code, f.gridWidth <= catalog.room.columns && f.gridDepth <= catalog.room.rows)
        }
    }

    @Test fun spriteUrlAddsDirectionSuffixBeforePng() {
        val url = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/furniture/desk_oak/desk_oak.png"
        assertEquals(url, Rotation.R0.spriteUrl(url))
        assertEquals(url.replace("desk_oak.png", "desk_oak_r090.png"), Rotation.R90.spriteUrl(url))
        assertEquals(url.replace("desk_oak.png", "desk_oak_r180.png"), Rotation.R180.spriteUrl(url))
        assertEquals(url.replace("desk_oak.png", "desk_oak_r270.png"), Rotation.R270.spriteUrl(url))
        assertEquals("https://x/a.webp", Rotation.R90.spriteUrl("https://x/a.webp"))
    }

    @Test fun rotationFromServerDegrees() {
        assertEquals(Rotation.R0, Rotation.fromDegrees(0))
        assertEquals(Rotation.R90, Rotation.fromDegrees(90))
        assertEquals(Rotation.R180, Rotation.fromDegrees(180))
        assertEquals(Rotation.R270, Rotation.fromDegrees(270))
        assertEquals(Rotation.R0, Rotation.fromDegrees(360))
        assertEquals(Rotation.R270, Rotation.fromDegrees(-90))
        assertNull(Rotation.fromDegrees(45))
    }

    @Test fun nextRotationCyclesClockwiseOrder() {
        assertEquals(Rotation.R90, Rotation.R0.next())
        assertEquals(Rotation.R180, Rotation.R90.next())
        assertEquals(Rotation.R270, Rotation.R180.next())
        assertEquals(Rotation.R0, Rotation.R270.next())
    }

    // ---- 잘못된 파일은 거부한다 ----

    private fun sprite(rotation: Int, w: Int, d: Int) =
        """{"rotation":$rotation,"widthPx":10,"heightPx":10,"anchorPx":[5,10],"gridAnchorOffsetPx":[0,1],"gridWidth":$w,"gridDepth":$d}"""

    private fun file(sprites: String, layer: String = "furniture") = """
        {"schemaVersion":1,"room":{"code":"r","widthPx":100,"heightPx":80,"columns":16,"rows":16,
          "gridOriginPx":[50,10],"gridAxisXPx":[-3,1.5],"gridAxisYPx":[3,1.5]},
         "furniture":[{"code":"t","gridWidth":2,"gridDepth":1,"layer":"$layer","blocksPlacement":true,"sprites":[$sprites]}]}
    """.trimIndent()

    @Test fun validSmallFileParses() {
        val c = RoomCatalog.parse(file(listOf(sprite(0, 2, 1), sprite(90, 1, 2), sprite(180, 2, 1), sprite(270, 1, 2)).joinToString()))
        assertEquals(1, c.furniture.size)
    }

    @Test fun rejectsMissingDirection() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            RoomCatalog.parse(file(listOf(sprite(0, 2, 1), sprite(90, 1, 2), sprite(180, 2, 1)).joinToString()))
        }
        assertTrue(e.message!!.contains("270"))
    }

    @Test fun rejectsQuarterTurnThatDoesNotSwapCells() {
        assertThrows(IllegalArgumentException::class.java) {
            RoomCatalog.parse(file(listOf(sprite(0, 2, 1), sprite(90, 2, 1), sprite(180, 2, 1), sprite(270, 1, 2)).joinToString()))
        }
    }

    @Test fun rejectsUnknownLayer() {
        assertThrows(IllegalArgumentException::class.java) {
            RoomCatalog.parse(file(listOf(sprite(0, 2, 1), sprite(90, 1, 2), sprite(180, 2, 1), sprite(270, 1, 2)).joinToString(), layer = "ceiling"))
        }
    }

    @Test fun rejectsUnknownSchemaVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            RoomCatalog.parse(file(sprite(0, 2, 1)).replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        }
    }
}
