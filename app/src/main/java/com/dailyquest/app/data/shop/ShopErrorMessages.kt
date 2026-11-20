package com.dailyquest.app.data.shop

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 상점·인벤토리 서버 오류 코드를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object ShopErrorMessages {
    fun forApi(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.INSUFFICIENT_COINS -> "코인이 부족해요."
            ApiErrorCode.ITEM_ALREADY_OWNED -> "이미 보유한 아이템이에요."
            ApiErrorCode.ITEM_NOT_FOUND -> "판매가 종료되었거나 존재하지 않는 아이템이에요."
            else -> if (e.status == 404) "요청한 아이템을 찾을 수 없어요." else AuthErrorMessages.forApi(e)
        }
    }

    /** 구매 요청의 결과를 알 수 없을 때(응답 유실·타임아웃·서버 오류) 쓰는 안내. */
    const val PURCHASE_UNCONFIRMED = "구매 결과를 확인하지 못했어요. 코인과 보유 아이템을 확인한 뒤 다시 시도해 주세요."
    const val PURCHASE_NOT_APPLIED = "구매가 처리되지 않았어요. 다시 시도해 주세요."
}
