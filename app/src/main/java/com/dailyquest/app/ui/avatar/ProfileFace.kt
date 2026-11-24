package com.dailyquest.app.ui.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

/** 프로필 얼굴 자리에 지금 무엇이 보이는지 (UI 테스트에서 확인). */
enum class ProfileFaceKind { PHOTO, BOY, GIRL, PERSON }

val ProfileFaceKindKey = SemanticsPropertyKey<ProfileFaceKind>("ProfileFaceKind")
private var SemanticsPropertyReceiver.profileFaceKind by ProfileFaceKindKey

/**
 * 사람 얼굴 동그라미 (프로필 얼굴 4단계). 화면마다 얼굴 그림을 그리던 자리를 이것으로 바꾼다.
 *
 * 보여주는 순서:
 * 1. 그 사람 착용 조합의 **아바타 얼굴 사진**(저장돼 있으면 바로, 없으면 얼굴 공장이 찍는 동안 아래 그림)
 * 2. 사진이 아직 없으면 성별별 기본 그림(남자아이·여자아이)
 * 3. 성별도 모르면 사람 아이콘
 *
 * 모양은 기존 [com.dailyquest.app.ui.components.DqAvatar]와 같다(동그라미, 흰 테두리 2dp).
 */
@Composable
fun ProfileFace(face: PersonFace?, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val studio = remember { FaceStudios.get(context) }
    val photo = rememberAvatarFace(face?.parts, studio)
    val kind = when {
        photo != null -> ProfileFaceKind.PHOTO
        face?.gender == AvatarGender.MALE -> ProfileFaceKind.BOY
        face?.gender == AvatarGender.FEMALE -> ProfileFaceKind.GIRL
        else -> ProfileFaceKind.PERSON
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (kind == ProfileFaceKind.PERSON) DQColors.White else DQColors.Cream)
            .border(2.dp, DQColors.White, CircleShape)
            .semantics {
                profileFaceKind = kind
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            ProfileFaceKind.PHOTO -> Image(photo!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ProfileFaceKind.BOY -> Image(painterResource(R.drawable.img_avatar_boy), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ProfileFaceKind.GIRL -> Image(painterResource(R.drawable.img_avatar_girl), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ProfileFaceKind.PERSON -> Image(painterResource(R.drawable.ic_users_lg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * 서버 아바타(착용 아이템 ID)로 그리는 얼굴. 방 멤버·피드 작성자처럼 다른 사람 얼굴에 쓴다.
 * 아이템 정보를 아직 모르면 받아 오는 동안 사람 아이콘을, 받아 오기에 실패해도 사람 아이콘을 보여준다.
 */
@Composable
fun ProfileFace(avatar: AvatarDto?, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    ProfileFace(rememberPersonFace(avatar), size, modifier, contentDescription)
}

/** [avatar] → 얼굴 재료. 이미 아는 아이템이면 바로, 아니면 서버에서 받아 온 뒤. 모르는 동안·실패하면 null. */
@Composable
fun rememberPersonFace(avatar: AvatarDto?): PersonFace? {
    val repo = LocalAppContainer.current.avatarFaceRepository
    var face by remember(avatar) { mutableStateOf(repo.cachedFace(avatar)) }
    LaunchedEffect(avatar) {
        if (face != null) return@LaunchedEffect
        val r = repo.face(avatar)
        if (r is ApiResult.Success) face = r.value
    }
    return face
}
