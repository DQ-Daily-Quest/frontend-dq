package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.MyItemDto
import com.dailyquest.app.data.remote.dto.UserMeResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** STEP 9-6 내 아바타 불러오기·착용 변경을 MockWebServer로 검증한다 (backend v0.4 응답 형식). */
class MyAvatarRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repo: MyAvatarRepository

    /** 경로별 응답. 요청 순서와 관계없이(동시 요청) 경로로 고른다. */
    private val responses = mutableMapOf<String, MockResponse>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.path!!.substringBefore('?')}"
                return responses[key] ?: MockResponse().setResponseCode(500)
            }
        }
        server.start()
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        session = SessionManager(tokens, emptyList())
        session.markAuthenticated(UserProfile(12, null, "진영", 100, 0, 0, 0, 0))
        val api: DqApi = NetworkModule.createApi(
            tokens, { session.expire(LogoutReason.EXPIRED) },
            server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString(),
        )
        repo = MyAvatarRepository(api, session)
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun me(gender: String?, avatar: String, coins: Int = 100) = json(
        """{"userId":12,"nickname":"진영","coins":$coins,"gender":${gender?.let { "\"$it\"" } ?: "null"},"avatar":$avatar,
            "streak":{"current":0,"longest":0},"stats":{"roomCount":0,"completedMissionCount":0},"hasPassword":true}""",
    )

    private val s3 = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/boy"
    private val boyItems = json(
        """[
        {"itemId":1,"name":"아이보리 피부","type":"AVATAR","category":"SKIN","price":0,"imageUrl":"$s3/DQ_Boy_01_Body.png","modelUrl":"$s3/DQ_Boy_01_Body.glb","isDefault":true,"owned":true,"acquiredBy":"DEFAULT","equipped":true,"code":"DQ_Boy_01_Body","gender":"MALE"},
        {"itemId":4,"name":"기본 눈","type":"AVATAR","category":"EYES","price":0,"modelUrl":"$s3/DQ_Boy_02_Eyes.glb","isDefault":true,"equipped":true},
        {"itemId":13,"name":"기본 머리","type":"AVATAR","category":"HAIR","price":0,"modelUrl":"$s3/DQ_Boy_03_Hair.glb","isDefault":true,"equipped":true},
        {"itemId":22,"name":"기본 상의","type":"AVATAR","category":"TOP","price":0,"modelUrl":"$s3/DQ_Boy_04_Top.glb","isDefault":true,"equipped":true},
        {"itemId":31,"name":"기본 하의","type":"AVATAR","category":"BOTTOM","price":0,"modelUrl":"$s3/DQ_Boy_05_Bottom.glb","isDefault":true,"equipped":true},
        {"itemId":40,"name":"기본 신발","type":"AVATAR","category":"SHOES","price":0,"modelUrl":"$s3/DQ_Boy_06_Shoes.glb","isDefault":true,"equipped":true},
        {"itemId":49,"name":"기본 모자","type":"AVATAR","category":"HAT","price":0,"modelUrl":"$s3/DQ_Boy_07_Hat.glb","isDefault":true,"equipped":false},
        {"itemId":50,"name":"카페 모자","type":"AVATAR","category":"HAT","price":100,"modelUrl":"$s3/DQ_Boy_Hat_Cafe.glb","isDefault":false,"acquiredBy":"SHOP","equipped":false}
        ]""",
    )
    private val boyDefaultAvatar = """{"skin":1,"eyes":4,"hair":13,"top":22,"bottom":31,"shoes":40,"hat":null}"""

    @Test fun `불러오면 슬롯별 아이템 ID를 GLB 주소로 바꾼다`() = runBlocking {
        responses["GET /api/v1/users/me"] = me("MALE", boyDefaultAvatar)
        responses["GET /api/v1/users/me/items"] = boyItems

        val avatar = (repo.load() as ApiResult.Success).value

        assertEquals(AvatarGender.MALE, avatar.gender)
        assertFalse(avatar.needsGender)
        assertEquals(6, avatar.modelParts.size)
        assertEquals("$s3/DQ_Boy_01_Body.glb", avatar.modelParts[AvatarPart.SKIN])
        assertEquals("$s3/DQ_Boy_06_Shoes.glb", avatar.modelParts[AvatarPart.SHOES])
        assertNull(avatar.modelParts[AvatarPart.HAT])
        assertEquals(13L, avatar.itemId(AvatarPart.HAIR))
        assertEquals("기본 머리", avatar.equipped[AvatarPart.HAIR]?.name)
    }

    @Test fun `보유 목록은 아바타만 요청한다`() = runBlocking {
        responses["GET /api/v1/users/me"] = me("MALE", boyDefaultAvatar)
        responses["GET /api/v1/users/me/items"] = boyItems

        repo.load()

        val paths = List(2) { server.takeRequest().path!! }
        assertTrue(paths.any { it == "/api/v1/users/me" })
        assertTrue(paths.any { it == "/api/v1/users/me/items?type=AVATAR" })
    }

    @Test fun `성별을 정하지 않은 계정은 needsGender이고 착용이 비어 있다`() = runBlocking {
        responses["GET /api/v1/users/me"] = me(null, """{"skin":null,"eyes":null,"hair":null,"top":null,"bottom":null,"shoes":null,"hat":null}""")
        responses["GET /api/v1/users/me/items"] = json("[]")

        val avatar = (repo.load() as ApiResult.Success).value

        assertTrue(avatar.needsGender)
        assertTrue(avatar.equipped.isEmpty())
        assertTrue(avatar.modelParts.isEmpty())
    }

    @Test fun `서버가 gender를 보내지 않으면(구버전) 성별 없음으로 본다`() = runBlocking {
        responses["GET /api/v1/users/me"] = json("""{"userId":12,"nickname":"진영","avatar":{}}""")
        responses["GET /api/v1/users/me/items"] = json("[]")

        val avatar = (repo.load() as ApiResult.Success).value

        assertTrue(avatar.needsGender)
    }

    @Test fun `하나라도 실패하면 불러오기 실패`() = runBlocking {
        responses["GET /api/v1/users/me"] = me("MALE", boyDefaultAvatar)
        // users/me/items 응답 없음 → 500

        assertTrue(repo.load() is ApiResult.Failure)
    }

    @Test fun `모자를 쓰면 바꾼 슬롯만 PATCH로 보내고 새 착용을 돌려준다`() = runBlocking {
        responses["PATCH /api/v1/users/me"] = me("MALE", boyDefaultAvatar.replace("\"hat\":null", "\"hat\":50"))
        responses["GET /api/v1/users/me/items"] = boyItems

        val avatar = (repo.equip(mapOf(AvatarPart.HAT to 50L)) as ApiResult.Success).value

        val patch = takeRequest("PATCH")
        assertEquals("""{"avatar":{"hat":50}}""", patch.body.readUtf8())
        assertEquals("$s3/DQ_Boy_Hat_Cafe.glb", avatar.modelParts[AvatarPart.HAT])
        assertEquals(7, avatar.modelParts.size)
    }

    @Test fun `모자를 벗으면 null을 그대로 보낸다`() = runBlocking {
        responses["PATCH /api/v1/users/me"] = me("MALE", boyDefaultAvatar)
        responses["GET /api/v1/users/me/items"] = boyItems

        repo.equip(mapOf(AvatarPart.HAT to null))

        assertEquals("""{"avatar":{"hat":null}}""", takeRequest("PATCH").body.readUtf8())
    }

    @Test fun `착용 저장 응답으로 세션의 내 정보도 갱신한다`() = runBlocking {
        responses["PATCH /api/v1/users/me"] = me("MALE", boyDefaultAvatar, coins = 380)
        responses["GET /api/v1/users/me/items"] = boyItems

        repo.equip(mapOf(AvatarPart.TOP to 22L))

        assertEquals(380, (session.state.value as SessionState.Authenticated).profile.coins)
    }

    @Test fun `착용은 저장됐는데 보유 목록 조회만 실패하면 마지막 목록으로 대신하고 성공으로 돌려준다`() = runBlocking {
        responses["GET /api/v1/users/me"] = me("MALE", boyDefaultAvatar)
        responses["GET /api/v1/users/me/items"] = boyItems
        repo.load()
        responses["PATCH /api/v1/users/me"] = me("MALE", boyDefaultAvatar.replace("\"hat\":null", "\"hat\":50"))
        responses.remove("GET /api/v1/users/me/items")   // 이제 500

        val avatar = (repo.equip(mapOf(AvatarPart.HAT to 50L)) as ApiResult.Success).value

        assertEquals("$s3/DQ_Boy_Hat_Cafe.glb", avatar.modelParts[AvatarPart.HAT])
    }

    @Test fun `체형이 다른 아이템이면 ITEM_GENDER_MISMATCH 실패와 안내 문구`() = runBlocking {
        responses["PATCH /api/v1/users/me"] = json(
            """{"code":"ITEM_GENDER_MISMATCH","message":"내 아바타 체형에 맞지 않는 아이템입니다.","timestamp":"2026-11-22T00:00:00Z"}""", 409,
        )

        val result = repo.equip(mapOf(AvatarPart.TOP to 105L)) as ApiResult.Failure

        assertEquals(ApiErrorCode.ITEM_GENDER_MISMATCH, result.error.httpCode)
        assertEquals("내 캐릭터가 입을 수 없는 아이템이에요.", AvatarErrorMessages.forApi(result.error))
    }

    @Test fun `보유하지 않은 아이템이면 ITEM_NOT_OWNED 안내 문구`() {
        val e = ApiException.Http(403, ApiErrorCode.ITEM_NOT_OWNED, null, "")
        assertEquals("보유하지 않은 아이템이에요. 상점에서 먼저 구매해 주세요.", AvatarErrorMessages.forApi(e))
    }

    @Test fun `resolveMyAvatar - 보유 목록에 없는 착용 ID는 건너뛰고 상대 경로는 전체 주소로 바꾼다`() {
        val me = UserMeResponse(userId = 1, nickname = "a", gender = "FEMALE", avatar = AvatarDto(skin = 1, top = 999))
        val items = listOf(MyItemDto(itemId = 1, name = "피부", modelUrl = "/files/items/body.glb"))

        val avatar = resolveMyAvatar(me, items)

        assertEquals(AvatarGender.FEMALE, avatar.gender)
        assertEquals(setOf(AvatarPart.SKIN), avatar.equipped.keys)
        assertTrue(avatar.modelParts.getValue(AvatarPart.SKIN).let { it.startsWith("http") && it.endsWith("/files/items/body.glb") })
    }

    @Test fun `AvatarGender - 모르는 값은 null`() {
        assertEquals(AvatarGender.MALE, AvatarGender.parse("MALE"))
        assertNull(AvatarGender.parse("male"))
        assertNull(AvatarGender.parse(null))
        assertNull(AvatarGender.parse("OTHER"))
    }

    private fun takeRequest(method: String): RecordedRequest {
        repeat(5) { server.takeRequest().let { if (it.method == method) return it } }
        error("$method 요청이 없습니다")
    }
}
