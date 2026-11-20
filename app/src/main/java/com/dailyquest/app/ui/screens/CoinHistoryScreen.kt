package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.data.shop.CoinTransaction
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.shop.CoinHistoryViewModel
import com.dailyquest.app.ui.theme.DQColors

/** 코인 이용 내역 — 서버가 기록한 거래(미션 보상·연속 달성 보상·아이템 구매)만 최신순으로 보여 준다. */
@Composable
fun CoinHistoryScreen(vm: CoinHistoryViewModel = viewModel(factory = CoinHistoryViewModel.Factory(LocalAppContainer.current))) {
    val nav = LocalNav.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.userId) { vm.refresh() }

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("coinHistory")) {
            NavHeader("코인 이용 내역", divider = true, onBack = { nav.backOrHome() })
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ui.error != null -> Column(Modifier.fillMaxSize().padding(24.dp).testTag("coinHistoryError"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Txt(ui.error.orEmpty(), size = 14f, color = DQColors.Muted)
                    Btn("다시 시도", Modifier.padding(top = 14.dp).height(44.dp)) { vm.refresh() }
                }
                ui.loaded && ui.items.isEmpty() -> Box(Modifier.fillMaxSize().testTag("coinHistoryEmpty"), contentAlignment = Alignment.Center) {
                    Txt("아직 코인 내역이 없어요.", size = 14f, color = DQColors.Muted)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui.items, key = { it.transactionId }) { TransactionRow(it) }
                    item {
                        if (ui.loadMoreError != null) {
                            Txt(ui.loadMoreError.orEmpty(), size = 12f, color = DQColors.Muted)
                            Btn("다시 시도", Modifier.padding(top = 8.dp).height(40.dp), style = BtnStyle.Outline) { vm.loadMore() }
                        } else if (ui.hasNext) {
                            Btn(if (ui.loadingMore) "불러오는 중…" else "더 보기", Modifier.fillMaxWidth().height(44.dp).testTag("coinHistoryMore"), style = BtnStyle.Outline, enabled = !ui.loadingMore) { vm.loadMore() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(t: CoinTransaction) {
    Row(Modifier.fillMaxWidth().testTag("coinTx_${t.transactionId}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(t.reason.label, size = 14f, weight = Bold)
            Txt(formatTxTime(t.createdAt), size = 11f, color = DQColors.Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Txt(if (t.amount > 0) "+${t.amount}" else "${t.amount}", size = 15f, weight = Bold, color = if (t.amount > 0) DQColors.Green else DQColors.InkDark)
            Txt("잔액 ${t.balanceAfter}", size = 11f, color = DQColors.Muted)
        }
    }
}

/** 서버 ISO 시각(2025-01-02T03:04:05Z 형태)을 날짜·시각으로 줄여 보여 준다. 형식이 다르면 원문을 그대로 쓴다. */
internal fun formatTxTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})""").find(iso) ?: return iso
    val (y, mo, d, h, mi) = m.destructured
    return "$y.$mo.$d $h:$mi"
}
