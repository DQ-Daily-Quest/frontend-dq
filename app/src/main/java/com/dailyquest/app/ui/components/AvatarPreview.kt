package com.dailyquest.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R

// 아이템·지갑·장착 저장소를 참조하지 않는 표시 전용 입력.
// GLB URI, 노드 이름, 리그 등 실제 모델 규격은 아직 정의하지 않는다.
data class AvatarPreviewSpec(val bodyVisualKey: String, val partVisualKeys: Map<String, String>)

@Composable
fun AvatarPreview(spec: AvatarPreviewSpec) {
    Scaled(358f, 228f) {
        DqImage(R.drawable.img_room_interior, Modifier.size(358.dp, 228.dp))
        DqAvatar(if (spec.bodyVisualKey == "female") R.drawable.img_avatar_girl else R.drawable.img_avatar_boy,
            130.dp, Modifier.at(114, 40))
    }
}
