package com.dailyquest.app.data.decor

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections

/** STEP 10-3 방 꾸미기 조회·저장을 MockWebServer로 검증한다 (backend api.md 8장 응답 형식). */
class DecorRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: DecorRepository
    private val catalog = RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText())

    private val responses = mutableMapOf<String, MockResponse>()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return responses["${request.method} ${request.path}"] ?: MockResponse().setResponseCode(500)
            }
        }
        server.start()
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        val session = SessionManager(tokens, emptyList())
        session.markAuthenticated(UserProfile(12, null, "진영", 100, 0, 0, 0, 0))
        val api = NetworkModule.createApi(
            tokens, { session.expire(LogoutReason.EXPIRED) },
            server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString(),
        )
        repo = DecorRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val s3 = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/furniture"
    private fun item(id: Long, code: String?, category: String = "DESK", name: String = code ?: "?") =
        """{"itemId":$id,"name":"$name","category":"$category","imageUrl":"$s3/$code/$code.png","modelUrl":"$s3/$code/$code.glb","code":${code?.let { "\"$it\"" } ?: "null"}}"""

    private val room = item(138, "room_16x16", "FLOOR", "기본 방")
    private fun placement(id: Long, item: String, x: Double, y: Double, rotation: Int = 0, z: Int = 0) =
        """{"placementId":$id,"item":$item,"ownerId":16,"x":$x,"y":$y,"z":$z,"rotation":$rotation}"""

    private fun decorations(background: String?, vararg placements: String) = json(
        """{"roomId":10,"background":${background ?: "null"},"placements":[${placements.joinToString()}],
            "updatedAt":"2026-11-24T03:20:00Z","updatedBy":{"userId":16,"nickname":"test02"}}""",
    )

    private fun error(status: Int, code: String, message: String) =
        json("""{"code":"$code","message":"$message","timestamp":"2026-11-24T00:00:00Z"}""", status)

    private fun <T> ApiResult<T>.ok(): T = (this as ApiResult.Success).value
    private fun ApiResult<*>.err(): ApiException = (this as ApiResult.Failure).error

    // ---- 조회 ----

    @Test fun loadTurnsServerRatiosIntoCells() = runBlocking {
        responses["GET /api/v1/rooms/10/decorations"] = decorations(
            room,
            placement(1, item(142, "desk_oak"), 0.25, 0.375, rotation = 90),
            placement(2, item(150, "rug_sage", "DECO"), 0.4375, 0.1875),
        )
        val decor = repo.load(10, catalog).ok()

        assertEquals(138L, decor.background?.itemId)
        assertEquals(DecorUser(16, "test02"), decor.updatedBy)
        assertEquals(2, decor.furniture.size)
        val desk = decor.furniture[0].placement
        assertEquals("desk_oak", desk.spec.code)
        assertEquals(4, desk.gridX)
        assertEquals(6, desk.gridY)
        assertEquals(Rotation.R90, desk.rotation)
        assertEquals("연동 규칙 11-5 예시 (0.4375, 0.1875) → (7, 3)", 7 to 3, decor.furniture[1].placement.let { it.gridX to it.gridY })
        assertTrue(decor.kept.isEmpty())
    }

    @Test fun neverSavedRoomHasNoBackground() = runBlocking {
        responses["GET /api/v1/rooms/10/decorations"] = json("""{"roomId":10,"background":null,"placements":[],"updatedAt":null,"updatedBy":null}""")
        val decor = repo.load(10, catalog).ok()
        assertNull(decor.background)
        assertTrue(decor.furniture.isEmpty())
        assertNull(decor.updatedBy)
    }

    @Test fun placementsTheAppCannotDrawAreKeptNotDropped() = runBlocking {
        responses["GET /api/v1/rooms/10/decorations"] = decorations(
            room,
            placement(1, item(142, "desk_oak"), 0.0, 0.0),
            placement(2, item(900, "new_sofa_2027"), 0.5, 0.5),          // 앱이 모르는 가구
            placement(3, item(143, "chair_sage", "CHAIR"), 0.03125, 0.0),  // 32칸 시절 반 칸
            placement(4, item(144, "plant_green", "DECO"), 0.5, 0.5, rotation = 45),
            placement(5, item(142, "desk_oak"), 0.9375, 0.0),              // 2칸 책상이 15번 칸 → 방 밖
        )
        val decor = repo.load(10, catalog).ok()
        assertEquals(1, decor.furniture.size)
        assertEquals(listOf(900L, 143L, 144L, 142L), decor.kept.map { it.item.itemId })
        assertTrue(decor.kept[0].reason.contains("모르는 가구"))
        assertEquals(0.03125, decor.kept[1].x, 0.0)
        assertEquals(45, decor.kept[2].rotation)
    }

    @Test fun availableSplitsBackgroundFromFurniture() = runBlocking {
        responses["GET /api/v1/rooms/10/decorations/available"] = json(
            """[{"item":$room,"ownerCount":2,"placedCount":0,"owners":[{"userId":16,"nickname":"test02"},{"userId":17,"nickname":"b"}]},
                {"item":${item(142, "desk_oak")},"ownerCount":2,"placedCount":1,"owners":[]},
                {"item":${item(900, "new_sofa_2027", "CHAIR")},"ownerCount":1,"placedCount":0,"owners":[]}]""",
        )
        val choices = repo.available(10, catalog).ok()
        assertEquals(138L, choices.defaultBackground?.itemId)
        assertEquals(listOf(138L), choices.backgrounds.map { it.itemId })
        assertEquals(listOf(142L, 900L), choices.furniture.map { it.item.itemId })
        val desk = choices.furniture[0]
        assertEquals("desk_oak", desk.spec?.code)
        assertEquals(2, desk.ownerCount)
        assertEquals(1, desk.placedCount)
        assertNull("앱이 모르는 가구는 놓을 수 없다", choices.furniture[1].spec)
    }

    // ---- 저장 ----

    private fun placed(id: Long, code: String, x: Int, y: Int, r: Rotation = Rotation.R0) =
        PlacedFurniture(DecorItem(id, code, "DECO", code, "$s3/$code/$code.png"), GridPlacement(catalog.furniture(code)!!, x, y, r))

    private fun lastBody(): JsonObject = Json.parseToJsonElement(requests.last().body.readUtf8()).jsonObject

    @Test fun saveSendsRatiosRotationDrawOrderAndDefaultBackground() = runBlocking {
        responses["PUT /api/v1/rooms/10/decorations"] = decorations(room)
        val kept = KeptPlacement(DecorItem(900, "새 소파", "CHAIR", "new_sofa_2027", null), 0.5, 0.5, 7, 0, "앱이 모르는 가구")
        val decor = RoomDecor(
            roomId = 10,
            background = null, // 한 번도 저장 안 한 방
            furniture = listOf(placed(143, "chair_sage", 7, 3, Rotation.R270), placed(150, "rug_sage", 6, 2)),
            kept = listOf(kept),
        )
        val defaultRoom = DecorItem(138, "기본 방", "FLOOR", "room_16x16", null)

        repo.save(decor, catalog, defaultRoom).ok()

        assertEquals("PUT", requests.last().method)
        val body = lastBody()
        assertEquals(138L, body["backgroundItemId"]!!.jsonPrimitive.long)
        val p = body["placements"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, p.size)
        // 의자: (7, 3) → 0.4375, 0.1875, 270°. 러그보다 나중에 그리므로 z = 1
        assertEquals(143L, p[0]["itemId"]!!.jsonPrimitive.long)
        assertEquals(0.4375, p[0]["x"]!!.jsonPrimitive.double, 0.0)
        assertEquals(0.1875, p[0]["y"]!!.jsonPrimitive.double, 0.0)
        assertEquals(270, p[0]["rotation"]!!.jsonPrimitive.int)
        assertEquals(1, p[0]["z"]!!.jsonPrimitive.int)
        // 러그는 먼저 그린다: z = 0
        assertEquals(0, p[1]["z"]!!.jsonPrimitive.int)
        // 앱이 모르는 가구는 받은 값 그대로 다시 보낸다
        assertEquals(900L, p[2]["itemId"]!!.jsonPrimitive.long)
        assertEquals(0.5, p[2]["x"]!!.jsonPrimitive.double, 0.0)
        assertEquals(7, p[2]["z"]!!.jsonPrimitive.int)
    }

    @Test fun saveKeepsCurrentBackgroundOverDefault() = runBlocking {
        responses["PUT /api/v1/rooms/10/decorations"] = decorations(room)
        val wall = DecorItem(200, "다른 벽", "WALL", "wall_x", null)
        repo.save(RoomDecor(10, wall, emptyList()), catalog, DecorItem(138, "기본 방", "FLOOR", "room_16x16", null)).ok()
        assertEquals(200L, lastBody()["backgroundItemId"]!!.jsonPrimitive.long)
    }

    @Test fun saveReturnsWhatServerStored() = runBlocking {
        responses["PUT /api/v1/rooms/10/decorations"] = decorations(room, placement(1, item(142, "desk_oak"), 0.875, 0.9375))
        val saved = repo.save(RoomDecor(10, null, listOf(placed(142, "desk_oak", 14, 15))), catalog, null).ok()
        assertEquals(138L, saved.background?.itemId)
        assertEquals(14 to 15, saved.furniture.single().placement.let { it.gridX to it.gridY })
    }

    @Test fun everyCellAndDirectionSurvivesSaveAndLoad() {
        // 저장 요청의 x·y·rotation을 그대로 조회 응답으로 돌려받았을 때 같은 칸·방향이 되는지(서버는 소수 4자리로 저장)
        for (x in 0 until 16) for (r in Rotation.entries) {
            val f = placed(143, "chair_sage", x, 15 - x, r)
            val req = DecorRepository.buildSaveRequest(RoomDecor(10, null, listOf(f)), catalog, null).placements.single()
            val stored = { v: Double -> Math.round(v * 10000) / 10000.0 }
            assertEquals(x, RoomLayout.fromServerRatio(stored(req.x), 16))
            assertEquals(15 - x, RoomLayout.fromServerRatio(stored(req.y), 16))
            assertEquals(r, Rotation.fromDegrees(req.rotation))
        }
    }

    // ---- 오류 ----

    @Test fun tooManyCopiesShowsServerMessageWithItemName() = runBlocking {
        val msg = "오크 서랍 책상은(는) 멤버 보유 수(1)를 넘겨 배치할 수 없습니다."
        responses["PUT /api/v1/rooms/10/decorations"] = error(403, ApiErrorCode.ITEM_NOT_OWNED_IN_ROOM, msg)
        val e = repo.save(RoomDecor(10, null, listOf(placed(142, "desk_oak", 0, 0), placed(142, "desk_oak", 3, 3))), catalog, null).err()
        assertEquals(ApiErrorCode.ITEM_NOT_OWNED_IN_ROOM, e.httpCode)
        assertEquals(msg, DecorErrorMessages.forApi(e))
    }

    @Test fun otherErrorsHaveFriendlyMessages() = runBlocking {
        responses["GET /api/v1/rooms/10/decorations"] = error(403, ApiErrorCode.FORBIDDEN, "forbidden")
        assertEquals("이 방의 멤버만 꾸밀 수 있어요.", DecorErrorMessages.forApi(repo.load(10, catalog).err()))

        responses["GET /api/v1/rooms/10/decorations"] = error(404, ApiErrorCode.ROOM_NOT_FOUND, "no room")
        assertEquals("방을 찾을 수 없어요. 삭제되었을 수 있어요.", DecorErrorMessages.forApi(repo.load(10, catalog).err()))

        responses["PUT /api/v1/rooms/10/decorations"] = error(404, ApiErrorCode.ITEM_NOT_FOUND, "no item")
        val e = repo.save(RoomDecor(10, null, emptyList()), catalog, null).err()
        assertTrue(DecorErrorMessages.forApi(e).contains("판매가 끝난 가구"))
    }

    @Test fun networkFailureIsReportedNotThrown() = runBlocking {
        server.shutdown()
        val e = repo.load(10, catalog).err()
        assertTrue(e is ApiException.Network)
    }
}
