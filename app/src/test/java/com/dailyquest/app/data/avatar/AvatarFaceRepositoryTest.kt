package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.dto.AvatarDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.CopyOnWriteArrayList

/** 프로필 얼굴 3단계: 다른 사람 아바타(아이템 ID) → `/shop/items/batch` → 얼굴용 3D 주소. */
class AvatarFaceRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AvatarFaceRepository

    private val boy = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/boy"
    private val girl = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/girl"

    /** 서버에 있는 아이템: ID → 3D 주소(null이면 3D 파일 없음). */
    private val catalog = mutableMapOf<Long, String?>()

    /** 받은 batch 요청의 ids 값. */
    private val batchCalls = CopyOnWriteArrayList<List<Long>>()
    @Volatile private var failWith: Int? = null

    @Before fun setUp() {
        catalog += mapOf(
            1L to "$boy/DQ_Boy_01_Body.glb", 4L to "$boy/DQ_Boy_02_Eyes.glb", 13L to "$boy/DQ_Boy_03_Hair.glb",
            22L to "$boy/DQ_Boy_04_Top.glb", 31L to "$boy/DQ_Boy_05_Bottom.glb", 40L to "$boy/DQ_Boy_06_Shoes.glb",
            50L to "$boy/DQ_Boy_Hat_Cafe.glb",
            101L to "$girl/DQ_Girl_01_Body.glb", 104L to "$girl/DQ_Girl_02_Eyes.glb", 113L to "$girl/DQ_Girl_03_Hair.glb",
            122L to "$girl/DQ_Girl_04_Top.glb",
            200L to null, // 3D 파일이 없는 아이템
        )
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (request.method != "GET" || url.encodedPath != "/api/v1/shop/items/batch") return MockResponse().setResponseCode(404)
                failWith?.let { return json("""{"code":"INTERNAL_ERROR","message":"x"}""", it) }
                val ids = url.queryParameter("ids")!!.split(',').map { it.toLong() }
                batchCalls += ids
                if (ids.size > 100) return json("""{"code":"VALIDATION_ERROR","message":"too many"}""", 400)
                val body = ids.filter { it in catalog }.sorted().joinToString(",", "[", "]") { id ->
                    val model = catalog[id]?.let { "\"$it\"" } ?: "null"
                    """{"itemId":$id,"name":"아이템$id","type":"AVATAR","category":"HAIR","price":0,"modelUrl":$model,"isDefault":false,"owned":false}"""
                }
                return json(body)
            }
        }
        server.start()
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        val api: DqApi = NetworkModule.createApi(tokens, {}, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
        repo = AvatarFaceRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val boyBasic = AvatarDto(hair = 13, eyes = 4, skin = 1, top = 22, bottom = 31, shoes = 40, hat = null)
    private val girlBasic = AvatarDto(hair = 113, eyes = 104, skin = 101, top = 122)

    private fun <T> ok(r: ApiResult<T>): T = (r as? ApiResult.Success)?.value ?: throw AssertionError("성공해야 한다: $r")

    @Test fun `한 사람의 얼굴 파츠 - 하의·신발은 빼고 3D 주소로 바꾼다`() = runBlocking {
        val parts = ok(repo.faceParts(boyBasic))
        assertEquals(
            mapOf(
                AvatarPart.SKIN to "$boy/DQ_Boy_01_Body.glb", AvatarPart.EYES to "$boy/DQ_Boy_02_Eyes.glb",
                AvatarPart.HAIR to "$boy/DQ_Boy_03_Hair.glb", AvatarPart.TOP to "$boy/DQ_Boy_04_Top.glb",
            ),
            parts,
        )
        assertEquals("얼굴에 보이는 아이템 ID만 묻는다", listOf(listOf(1L, 4L, 13L, 22L)), batchCalls)
    }

    @Test fun `하의·신발만 다르면 같은 얼굴 이름, 모자를 쓰면 다른 이름`() = runBlocking {
        val sameFace = boyBasic.copy(bottom = 999, shoes = 998)
        val withHat = boyBasic.copy(hat = 50)
        val (a, b, c) = ok(repo.faceParts(listOf(boyBasic, sameFace, withHat)))
        assertEquals(FaceKey(a).id, FaceKey(b).id)
        assertTrue(FaceKey(a).id != FaceKey(c).id)
        assertEquals("$boy/DQ_Boy_Hat_Cafe.glb", c[AvatarPart.HAT])
    }

    @Test fun `여러 사람은 한 번에 묻고, 이미 아는 아이템은 다시 묻지 않는다`() = runBlocking {
        val first = ok(repo.faceParts(listOf(boyBasic, girlBasic, null)))
        assertEquals(3, first.size)
        assertEquals("$girl/DQ_Girl_03_Hair.glb", first[1][AvatarPart.HAIR])
        assertEquals("아바타가 없는 사람은 빈 맵", emptyMap<AvatarPart, String>(), first[2])
        assertEquals(listOf(listOf(1L, 4L, 13L, 22L, 101L, 104L, 113L, 122L)), batchCalls)

        ok(repo.faceParts(listOf(girlBasic, boyBasic)))
        assertEquals("다시 부를 때는 서버에 묻지 않는다", 1, batchCalls.size)

        ok(repo.faceParts(boyBasic.copy(hat = 50)))
        assertEquals("새 아이템(모자)만 묻는다", listOf(50L), batchCalls.last())
    }

    @Test fun `이미 아는 것만으로 바로 만들기 - 모르는 ID가 있으면 null`() = runBlocking {
        assertNull(repo.cachedFaceParts(boyBasic))
        assertEquals(emptyMap<AvatarPart, String>(), repo.cachedFaceParts(null))
        ok(repo.faceParts(boyBasic))
        assertEquals(4, repo.cachedFaceParts(boyBasic)!!.size)
        assertNull("모자는 아직 모른다", repo.cachedFaceParts(boyBasic.copy(hat = 50)))
    }

    @Test fun `서버에 없는 아이템과 3D 파일이 없는 아이템은 그 슬롯만 빼고, 다시 묻지 않는다`() = runBlocking {
        val avatar = boyBasic.copy(hair = 777, hat = 200)
        val parts = ok(repo.faceParts(avatar))
        assertEquals(setOf(AvatarPart.SKIN, AvatarPart.EYES, AvatarPart.TOP), parts.keys)
        ok(repo.faceParts(avatar))
        assertEquals(1, batchCalls.size)
        assertEquals(parts, repo.cachedFaceParts(avatar))
    }

    @Test fun `100개가 넘으면 나눠서 묻는다`() = runBlocking {
        // 사람 50명이 서로 다른 피부·눈·머리·상의(4개씩) = 아이템 200개 + 이미 아는 것 없음
        val people = (0 until 50).map { i ->
            val base = 1000L + i * 4
            AvatarDto(skin = base, eyes = base + 1, hair = base + 2, top = base + 3)
        }
        people.flatMap { listOfNotNull(it.skin, it.eyes, it.hair, it.top) }.forEach { catalog[it] = "$boy/item_$it.glb" }
        val all = ok(repo.faceParts(people))
        assertEquals(listOf(100, 100), batchCalls.map { it.size })
        assertEquals("$boy/item_1198.glb", all[49][AvatarPart.HAIR])
    }

    @Test fun `서버 오류면 실패를 돌려주고, 다음에 다시 묻는다`() = runBlocking {
        failWith = 500
        assertTrue(repo.faceParts(boyBasic) is ApiResult.Failure)
        assertNull("실패한 것은 기억하지 않는다", repo.cachedFaceParts(boyBasic))
        failWith = null
        assertEquals(4, ok(repo.faceParts(boyBasic)).size)
    }

    @Test fun `여러 화면이 동시에 불러도 같은 아이템은 한 번만 묻는다`() = runBlocking {
        val results = (1..5).map { async(kotlinx.coroutines.Dispatchers.IO) { repo.faceParts(listOf(boyBasic, girlBasic)) } }.awaitAll()
        assertTrue(results.all { it is ApiResult.Success })
        assertEquals(1, batchCalls.size)
    }

    @Test fun `내 아바타처럼 주소를 이미 알면 얼굴 슬롯만 남긴다`() {
        val mine = mapOf(
            AvatarPart.SKIN to "s", AvatarPart.HAIR to "h", AvatarPart.BOTTOM to "b", AvatarPart.SHOES to "sh", AvatarPart.HAT to "ht",
        )
        assertEquals(setOf(AvatarPart.SKIN, AvatarPart.HAIR, AvatarPart.HAT), mine.faceOnly().keys)
    }
}
