package com.netkaize.subscription.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.ServiceTemplate
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.SubscriptionStatus
import com.netkaize.subscription.data.SyncFrequency
import com.netkaize.subscription.domain.BillingCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
private fun PageContainer(
    layout: AdaptiveLayoutSpec,
    maxContentWidthDp: Int = layout.contentMaxWidthDp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = maxContentWidthDp.dp)
                .fillMaxSize()
                .testTag("page_content"),
            contentPadding = PaddingValues(
                start = layout.pagePaddingDp.dp,
                end = layout.pagePaddingDp.dp,
                top = 22.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun HomeScreen(
    state: AppUiState,
    layout: AdaptiveLayoutSpec,
    onEdit: (Subscription) -> Unit,
    onDelete: (String) -> Unit,
    onScheduleCancel: (Subscription, LocalDate) -> Unit,
    onConfirmRenewal: (Subscription) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    selectedCategory?.let { category ->
        CategoryDetailScreen(
            category = category,
            values = state.subscriptions.filter { it.category == category },
            state = state,
            layout = layout,
            onBack = { selectedCategory = null },
            onEdit = onEdit,
            onDelete = onDelete,
        )
        return
    }
    val expanded = layout.isExpanded
    val user = state.session?.user ?: return
    val now = LocalDate.now()
    val active = BillingCalculator.activeSubscriptions(state.subscriptions)
    val spent = BillingCalculator.lifetimeSpent(state.subscriptions)
    val month = BillingCalculator.nextMonthBudget(state.subscriptions)
    val annual = BillingCalculator.annualSpend(state.subscriptions)
    val due = BillingCalculator.dueWithin(state.subscriptions, 30).firstOrNull()
    PageContainer(layout) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("M月d日 EEEE")),
                        color = AppSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("订阅", style = MaterialTheme.typography.displaySmall)
                }
                Surface(shape = CircleShape, color = AppFill, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, "个人中心", tint = AppSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        item {
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    OverviewCard(
                        activeCount = active.size,
                        month = formatMoney(month, user.currencyCode, state.currencyRates.rates),
                        annual = formatMoney(annual, user.currencyCode, state.currencyRates.rates),
                        spent = formatMoney(spent, user.currencyCode, state.currencyRates.rates),
                        dirty = state.dirty,
                        expanded = true,
                        modifier = Modifier.weight(1.55f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        HomeMetricSummary(state, month, annual, user.currencyCode)
                        SectionTitle("下一笔续费", if (due == null) "暂无待扣" else "30 天内")
                        if (due == null) EmptyCard("目前没有即将续费的订阅")
                        else DueCard(due.first, due.second, state, onScheduleCancel, onConfirmRenewal)
                    }
                }
            } else {
                OverviewCard(
                    activeCount = active.size,
                    month = formatMoney(month, user.currencyCode, state.currencyRates.rates),
                    annual = formatMoney(annual, user.currencyCode, state.currencyRates.rates),
                    spent = formatMoney(spent, user.currencyCode, state.currencyRates.rates),
                    dirty = state.dirty,
                    expanded = false,
                )
            }
        }
        if (!expanded) {
            item { HomeMetricSummary(state, month, annual, user.currencyCode) }
            item { SectionTitle("下一笔续费", if (due == null) "暂无待扣" else "30 天内") }
            item {
                if (due == null) EmptyCard("目前没有即将续费的订阅")
                else DueCard(due.first, due.second, state, onScheduleCancel, onConfirmRenewal)
            }
        }
        item { SectionTitle("订阅分类", "按类目查看") }
        val groups = state.subscriptions.groupBy { it.category }.entries.sortedBy { it.key }
        if (groups.isEmpty()) item { EmptyCard("添加第一项订阅后，这里会自动分类") }
        if (expanded) {
            items(groups.chunked(2), key = { row -> row.joinToString("|") { it.key } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (category, values) ->
                        CategoryCard(category, values, state, { selectedCategory = category }, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(groups, key = { it.key }) { (category, values) ->
                CategoryCard(category, values, state, { selectedCategory = category })
            }
        }
    }
}

@Composable
private fun CategoryDetailScreen(
    category: String,
    values: List<Subscription>,
    state: AppUiState,
    layout: AdaptiveLayoutSpec,
    onBack: () -> Unit,
    onEdit: (Subscription) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Subscription?>(null) }
    val sorted = values.sortedBy { BillingCalculator.nextOccurrence(it) ?: LocalDate.MAX }
    PageContainer(layout) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Outlined.ArrowBack, "返回")
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(category, style = MaterialTheme.typography.headlineMedium)
                    Text("${values.size} 项 · 按最近扣费排序", color = AppSecondary)
                }
            }
        }
        if (sorted.isEmpty()) item { EmptyCard("此类目暂无订阅") }
        items(sorted, key = { it.id }) { subscription ->
            SubscriptionRow(
                subscription = subscription,
                state = state,
                onEdit = { onEdit(subscription) },
                onDelete = { deleteTarget = subscription },
            )
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("删除前会自动保留本机备份，云端同步后该记录也会移除。") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = { onDelete(target.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppRed),
                ) { Text("删除") }
            },
        )
    }
}

@Composable
private fun OverviewCard(
    activeCount: Int,
    month: String,
    annual: String,
    spent: String,
    dirty: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppCard),
        shape = RoundedCornerShape(12.dp),
    ) {
        BoxWithConstraints {
            val fontScale = LocalDensity.current.fontScale
            val narrowContent = maxWidth < 360.dp || fontScale >= 1.5f
            Column(
                Modifier.padding(if (expanded) 22.dp else if (maxWidth < 360.dp) 16.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("支出概览", color = AppSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (dirty) AppOrangeTint else AppGreenTint)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "$activeCount 项生效",
                            color = if (dirty) AppOrange else AppGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text("下月支出预计", color = AppSecondary, style = MaterialTheme.typography.bodySmall)
                Text(
                    month,
                    color = AppInk,
                    style = if (narrowContent) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Visible,
                )
                if (narrowContent) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("年度支出预计", annual, Modifier.fillMaxWidth())
                        MetricTile("累计费用估算", spent, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile("年度支出预计", annual, Modifier.weight(1f))
                        MetricTile("累计费用估算", spent, Modifier.weight(1f))
                    }
                }
                Text(
                    if (dirty) "本机有待同步修改" else "按当前价格与订阅时间线估算",
                    color = if (dirty) AppOrange else AppTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HomeMetricSummary(
    state: AppUiState,
    month: Double,
    annual: Double,
    currencyCode: String,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val stackAll = fontScale >= 1.5f
        val monthValue = formatMoney(month, currencyCode, state.currencyRates.rates)
        val annualValue = formatMoney(annual, currencyCode, state.currencyRates.rates)
        val dueValue = "${BillingCalculator.dueWithin(state.subscriptions, 30).size} 笔"
        when {
            stackAll -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("下月预算", monthValue, Modifier.fillMaxWidth())
                MetricCard("年度预计", annualValue, Modifier.fillMaxWidth())
                MetricCard("30 天内", dueValue, Modifier.fillMaxWidth())
            }
            maxWidth < 380.dp -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("下月预算", monthValue, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("年度预计", annualValue, Modifier.weight(1f))
                    MetricCard("30 天内", dueValue, Modifier.weight(1f))
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("下月预算", monthValue, Modifier.weight(1f))
                MetricCard("年度预计", annualValue, Modifier.weight(1f))
                MetricCard("30 天内", dueValue, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(AppFill, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = AppSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(3.dp))
        Text(value, color = AppInk, style = MaterialTheme.typography.titleMedium, maxLines = 2)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AppCard)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = AppSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Visible)
        }
    }
}

@Composable
private fun DueCard(
    subscription: Subscription,
    date: LocalDate,
    state: AppUiState,
    onScheduleCancel: (Subscription, LocalDate) -> Unit,
    onConfirmRenewal: (Subscription) -> Unit,
) {
    var confirmCancellation by remember(subscription.id, date) { mutableStateOf(false) }
    val confirmed = subscription.lastReviewedAt == LocalDate.now()
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(AppFill), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date).coerceAtLeast(0).toString(), color = AppInk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("天后", color = AppSecondary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${subscription.name} · ${formatMoney(subscription.priceCny, state.session?.user?.currencyCode ?: "CNY", state.currencyRates.rates)}", style = MaterialTheme.typography.titleLarge)
                    Text(date.format(DateTimeFormatter.ofPattern("M月d日 E")), color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { confirmCancellation = true },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppRedTint, contentColor = AppRed),
                ) { Text("到期取消") }
                if (confirmed) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppGreenTint,
                            contentColor = AppGreen,
                            disabledContainerColor = AppGreenTint,
                            disabledContentColor = AppGreen,
                        ),
                    ) { Text("✓ 已确认") }
                } else {
                    Button(
                        onClick = { onConfirmRenewal(subscription) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("确认续费") }
                }
            }
        }
    }
    if (confirmCancellation) {
        AlertDialog(
            onDismissRequest = { confirmCancellation = false },
            title = { Text("到期后取消「${subscription.name}」？") },
            text = { Text("服务会保留至 ${date.format(DateTimeFormatter.ofPattern("M月d日"))}，当天起不再续费；本周期已发生费用不会撤销。") },
            dismissButton = { TextButton(onClick = { confirmCancellation = false }) { Text("暂不取消") } },
            confirmButton = {
                Button(
                    onClick = { confirmCancellation = false; onScheduleCancel(subscription, date) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppRed),
                ) { Text("确认到期取消") }
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: String,
    values: List<Subscription>,
    state: AppUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = values
        .mapNotNull { subscription -> BillingCalculator.nextOccurrence(subscription)?.let { subscription to it } }
        .minByOrNull { it.second }
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AppBlueTint), contentAlignment = Alignment.Center) { Text(category.take(1), color = AppBlue, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category, style = MaterialTheme.typography.titleLarge)
                Text(
                    next?.let { "${values.size} 项 · ${it.first.name} ${it.second.format(DateTimeFormatter.ofPattern("M月d日"))} 扣费" }
                        ?: "${values.size} 项 · 暂无待扣",
                    color = AppSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = AppTertiary)
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Text(trailing, color = AppTertiary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, color = AppSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
    }
}

// iOS search field: gray fill, no visible outline, 10pt radius.
@Composable
private fun searchFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppSearchFill,
    unfocusedContainerColor = AppSearchFill,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = AppBlue,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionsScreen(
    state: AppUiState,
    layout: AdaptiveLayoutSpec,
    onEdit: (Subscription) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<SubscriptionStatus?>(null) }
    var cycle by remember { mutableStateOf<BillingCycle?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Subscription?>(null) }
    val activeFilterCount = listOfNotNull(status, cycle, category).size
    val filtered = state.subscriptions
        .filter { query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) }
        .filter { status == null || BillingCalculator.effectiveStatus(it) == status }
        .filter { cycle == null || it.cycle == cycle }
        .filter { category == null || it.category == category }
        .sortedWith(compareBy(nullsLast()) { BillingCalculator.nextOccurrence(it) })
    PageContainer(layout) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("已订阅服务", style = MaterialTheme.typography.displaySmall)
                    Text("${filtered.size} 项 · 按最近扣费排序", color = AppSecondary, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(
                    onClick = { showFilters = true },
                    modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.FilterList, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (activeFilterCount == 0) "筛选" else "筛选 $activeFilterCount")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索服务或类目") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = AppTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = searchFieldColors(),
            )
        }
        if (activeFilterCount > 0) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { status = null; cycle = null; category = null },
                        label = { Text("清除全部") },
                        modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                    )
                }
                status?.let { value ->
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { status = null },
                            label = { Text(value.label) },
                            modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                        )
                    }
                }
                cycle?.let { value ->
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { cycle = null },
                            label = { Text(value.label) },
                            modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                        )
                    }
                }
                category?.let { value ->
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { category = null },
                            label = { Text(value) },
                            modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) item { EmptyCard("没有符合条件的订阅") }
        val groups = filtered.groupBy { it.category }
        groups.forEach { (group, values) ->
            item(key = "title-$group") {
                Text(
                    group,
                    color = AppSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
            }
            if (layout.isExpanded) {
                items(values.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { subscription ->
                            SubscriptionRow(
                                subscription,
                                state,
                                onEdit = { onEdit(subscription) },
                                onDelete = { deleteTarget = subscription },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(values, key = { it.id }) { subscription ->
                    SubscriptionRow(subscription, state, onEdit = { onEdit(subscription) }, onDelete = { deleteTarget = subscription })
                }
            }
        }
    }
    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("筛选订阅", style = MaterialTheme.typography.headlineSmall)
                        Text("状态、周期和类目可同时选择", color = AppSecondary)
                    }
                    TextButton(onClick = { status = null; cycle = null; category = null }) {
                        Text("重置")
                    }
                }
                Text("状态", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubscriptionStatus.entries.forEach { value ->
                        FilterChip(
                            selected = status == value,
                            onClick = { status = if (status == value) null else value },
                            label = { Text(value.label) },
                            modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                        )
                    }
                }
                Text("扣费周期", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingCycle.entries.forEach { value ->
                        FilterChip(
                            selected = cycle == value,
                            onClick = { cycle = if (cycle == value) null else value },
                            label = { Text(value.label) },
                            modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                        )
                    }
                }
                Text("类目", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.subscriptions.map { it.category }.distinct().sorted().forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = if (category == value) null else value },
                            label = { Text(value) },
                            modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                        )
                    }
                }
                Button(
                    onClick = { showFilters = false },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("查看 ${filtered.size} 项订阅")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("删除前会自动保留本机历史备份，云端同步后该记录也会移除。") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
            confirmButton = { Button(onClick = { onDelete(target.id); deleteTarget = null }, colors = ButtonDefaults.buttonColors(containerColor = AppRed)) { Text("删除") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    state: AppUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = BillingCalculator.nextOccurrence(subscription)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value -> value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.Settled },
        positionalThreshold = { distance -> distance * .28f },
    )
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(AppRed),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onEdit(); scope.launch { swipeState.reset() } },
                    modifier = Modifier.fillMaxHeight().width(68.dp).background(AppBlue),
                ) {
                    Icon(Icons.Outlined.Edit, "编辑", tint = Color.White)
                }
                IconButton(
                    onClick = { onDelete(); scope.launch { swipeState.reset() } },
                    modifier = Modifier.fillMaxHeight().width(68.dp).background(AppRed),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, "删除", tint = Color.White)
                }
            }
        },
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        ) {
            BoxWithConstraints {
                val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
                val detail = if (subscription.cycle == BillingCycle.ONCE) {
                    "一次性服务 · 已订阅 ${BillingCalculator.subscriptionDays(subscription)} 天"
                } else {
                    "${subscription.cycle.label} · ${next?.format(DateTimeFormatter.ofPattern("M月d日")) ?: subscription.status.label}"
                }
                if (stacked) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceIcon(subscription.name, subscription.icon, subscription.image, subscription.color, subscription.iconKey)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(subscription.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(detail, color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(
                            formatMoney(subscription.priceCny, state.session?.user?.currencyCode ?: "CNY", state.currencyRates.rates),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                    }
                } else {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ServiceIcon(subscription.name, subscription.icon, subscription.image, subscription.color, subscription.iconKey)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subscription.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(detail, color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            formatMoney(subscription.priceCny, state.session?.user?.currencyCode ?: "CNY", state.currencyRates.rates),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddScreen(
    state: AppUiState,
    layout: AdaptiveLayoutSpec,
    onCustom: () -> Unit,
    onTemplate: (ServiceTemplate) -> Unit,
    onExisting: (Subscription) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    var gridMode by remember { mutableStateOf(false) }
    val categories = listOf("全部") + state.templates.map { it.category }.distinct().sorted()
    val values = state.templates.filter { (category == "全部" || it.category == category) && (query.isBlank() || it.name.contains(query, true)) }
    PageContainer(layout) {
        item { PageHeader("添加订阅", "模板与自定义已分开") }
        item {
            Button(onClick = onCustom, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("添加自定义服务")
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索官方服务模板") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = AppTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = searchFieldColors(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { value ->
                    FilterChip(
                        selected = category == value,
                        onClick = { category = value },
                        label = { Text(value) },
                        modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("全部预设模板", color = AppSecondary, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { gridMode = false },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) { Icon(Icons.Outlined.ViewList, "列表显示", tint = if (!gridMode) AppBlue else AppSecondary) }
                IconButton(
                    onClick = { gridMode = true },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) { Icon(Icons.Outlined.GridView, "网格显示", tint = if (gridMode) AppBlue else AppSecondary) }
            }
        }
        if (values.isEmpty()) item { EmptyCard("没有匹配的模板，可使用自定义添加") }
        if (gridMode) {
            val columns = if (layout.isExpanded) 3 else 2
            items(values.chunked(columns), key = { row -> row.joinToString("|") { it.id } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { template ->
                        TemplateTile(template, state, onTemplate, onExisting, Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            items(values, key = { it.id }) { template -> TemplateRow(template, state, onTemplate, onExisting) }
        }
    }
}

@Composable
private fun TemplateTile(
    template: ServiceTemplate,
    state: AppUiState,
    onTemplate: (ServiceTemplate) -> Unit,
    onExisting: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val existing = state.subscriptions.firstOrNull {
        it.name.equals(template.name, ignoreCase = true) &&
            BillingCalculator.effectiveStatus(it) != SubscriptionStatus.CANCELED
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { if (existing != null) onExisting(existing) else onTemplate(template) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ServiceIcon(template.name, template.icon, template.image, template.color)
            Text(template.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                existing?.let { "已订阅" }
                    ?: "${template.cycle.label} ${formatMoney(template.priceCny, state.session?.user?.currencyCode ?: "CNY", state.currencyRates.rates)}",
                color = if (existing != null) AppGreen else AppSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TemplateRow(
    template: ServiceTemplate,
    state: AppUiState,
    onTemplate: (ServiceTemplate) -> Unit,
    onExisting: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val existing = state.subscriptions.firstOrNull {
        it.name.equals(template.name, ignoreCase = true) &&
            BillingCalculator.effectiveStatus(it) != SubscriptionStatus.CANCELED
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().clickable {
            if (existing != null) onExisting(existing) else onTemplate(template)
        },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(template.name, template.icon, template.image, template.color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (template.isOfficial) Text("已验证", color = AppGreen, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    existing?.let { "已订阅 · 点击查看" }
                        ?: "${template.category} · ${template.cycle.label} ${formatMoney(template.priceCny, state.session?.user?.currencyCode ?: "CNY", state.currencyRates.rates)}",
                    color = if (existing != null) AppGreen else AppSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = AppTertiary)
        }
    }
}

@Composable
internal fun AnalysisScreen(state: AppUiState, layout: AdaptiveLayoutSpec) {
    val user = state.session?.user ?: return
    val year = LocalDate.now().year
    val now = LocalDate.now()
    var budgetMode by remember { mutableStateOf(true) }
    val values = if (budgetMode) {
        BillingCalculator.monthlyBudgetSeries(state.subscriptions, year)
    } else {
        BillingCalculator.monthlySeries(state.subscriptions, year)
    }
    val total = values.sum()
    val categoryTotals = state.subscriptions.groupBy { it.category }
        .mapValues { (_, items) ->
            if (budgetMode) BillingCalculator.monthlyBudgetSeries(items, year).sum()
            else BillingCalculator.annualSpend(items, year)
        }
        .entries.sortedByDescending { it.value }
    val active = BillingCalculator.activeSubscriptions(state.subscriptions, now)
    val expensive = active.maxByOrNull(BillingCalculator::annualizedPrice)
    val currentMonth = YearMonth.from(now)
    val currentSchedule = BillingCalculator.chargeSchedule(
        state.subscriptions,
        currentMonth.atDay(1),
        currentMonth.atEndOfMonth(),
    )
    val next30 = BillingCalculator.chargeSchedule(state.subscriptions, now, now.plusDays(30))
    val duplicateCategory = active.groupBy { it.category }.entries.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }
    val staleReview = active.firstOrNull {
        BillingCalculator.subscriptionDays(it, now) >= 90 &&
            (it.lastReviewedAt == null || it.lastReviewedAt.isBefore(now.minusDays(90)))
    }
    val savingMessage = when {
        duplicateCategory != null ->
            "「${duplicateCategory.key}」类有 ${duplicateCategory.value.size} 项生效服务，可先核对功能是否重叠。"
        staleReview != null ->
            "「${staleReview.name}」已连续订阅 ${BillingCalculator.subscriptionDays(staleReview, now)} 天且 90 天未确认，建议本周期复盘。"
        expensive != null ->
            "优先复盘「${expensive.name}」：它是当前年化成本最高的服务。"
        else -> "添加订阅后，会根据周期、续费日与费用生成可行动建议。"
    }
    PageContainer(layout) {
        item { PageHeader("支出分析", "$year 年 · 预算与现金流双口径") }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppSearchFill).padding(2.dp),
            ) {
                listOf(true to "预算视图 · 月度摊销", false to "现金流 · 预计扣费").forEach { (value, label) ->
                    TextButton(
                        onClick = { budgetMode = value },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (budgetMode == value) Color.White else Color.Transparent,
                            contentColor = if (budgetMode == value) AppInk else AppSecondary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(label, maxLines = 2, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            if (layout.isExpanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnalysisChartCard(
                        total = formatMoney(total, user.currencyCode, state.currencyRates.rates),
                        values = values,
                        budgetMode = budgetMode,
                        modifier = Modifier.weight(1.65f),
                    )
                    SavingsCard(savingMessage, expensive, user.currencyCode, state.currencyRates.rates, Modifier.weight(1f))
                }
            } else {
                AnalysisChartCard(formatMoney(total, user.currencyCode, state.currencyRates.rates), values, budgetMode)
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 520.dp || LocalDensity.current.fontScale >= 1.5f) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnalysisMetricCard("${now.monthValue} 月${if (budgetMode) "预算" else "扣费"}", formatMoney(values[now.monthValue - 1], user.currencyCode, state.currencyRates.rates), Modifier.fillMaxWidth())
                        AnalysisMetricCard("未来 30 天待扣", formatMoney(next30.sumOf { it.subscription.priceCny }, user.currencyCode, state.currencyRates.rates), Modifier.fillMaxWidth())
                        AnalysisMetricCard("最高年成本", expensive?.let { formatMoney(BillingCalculator.annualizedPrice(it), user.currencyCode, state.currencyRates.rates) } ?: "—", Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnalysisMetricCard("${now.monthValue} 月${if (budgetMode) "预算" else "扣费"}", formatMoney(values[now.monthValue - 1], user.currencyCode, state.currencyRates.rates), Modifier.weight(1f))
                        AnalysisMetricCard("未来 30 天待扣", formatMoney(next30.sumOf { it.subscription.priceCny }, user.currencyCode, state.currencyRates.rates), Modifier.weight(1f))
                        AnalysisMetricCard("最高年成本", expensive?.let { formatMoney(BillingCalculator.annualizedPrice(it), user.currencyCode, state.currencyRates.rates) } ?: "—", Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionTitle("支出构成", "按年度预计") }
        if (categoryTotals.isEmpty()) item { EmptyCard("添加订阅后，这里会显示分类支出") }
        if (layout.isExpanded) {
            items(categoryTotals.chunked(2), key = { row -> row.joinToString("|") { it.key } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { entry ->
                        CategorySpendCard(
                            category = entry.key,
                            value = entry.value,
                            total = total,
                            currencyCode = user.currencyCode,
                            rates = state.currencyRates.rates,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(categoryTotals, key = { it.key }) { entry ->
                CategorySpendCard(entry.key, entry.value, total, user.currencyCode, state.currencyRates.rates)
            }
        }
        if (!layout.isExpanded) item { SavingsCard(savingMessage, expensive, user.currencyCode, state.currencyRates.rates) }
        item { SectionTitle("${now.monthValue} 月扣费日程", if (currentSchedule.isEmpty()) "暂无待扣" else "${currentSchedule.size} 笔") }
        if (currentSchedule.isEmpty()) {
            item { EmptyCard("本月没有预计扣费安排") }
        } else {
            items(currentSchedule, key = { "${it.subscription.id}-${it.date}" }) { occurrence ->
                ChargeScheduleRow(occurrence, user.currencyCode, state.currencyRates.rates)
            }
        }
    }
}

@Composable
private fun AnalysisChartCard(total: String, values: List<Double>, budgetMode: Boolean, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(if (budgetMode) "年度累计预算" else "年度预计扣费", color = AppSecondary, style = MaterialTheme.typography.bodySmall)
            Text(total, style = MaterialTheme.typography.displaySmall, maxLines = 2)
            Text(
                if (budgetMode) "按可计费天数摊销，适合观察长期成本" else "只在预计收费日计入，适合安排现金流",
                color = AppSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            BarChart(values, Modifier.fillMaxWidth().height(190.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1月", color = AppTertiary, style = MaterialTheme.typography.labelMedium)
                Text("6月", color = AppTertiary, style = MaterialTheme.typography.labelMedium)
                Text("12月", color = AppTertiary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AnalysisMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = AppSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        }
    }
}

@Composable
private fun CategorySpendCard(
    category: String,
    value: Double,
    total: Double,
    currencyCode: String,
    rates: Map<String, Double>,
    modifier: Modifier = Modifier,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints {
            val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
            if (stacked) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AppBlueTint), contentAlignment = Alignment.Center) {
                            Text(category.take(1), color = AppBlue, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category, style = MaterialTheme.typography.titleLarge)
                            Text(if (total > 0) "${(value / total * 100).toInt()}% 的全年支出" else "暂无支出", color = AppSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(formatMoney(value, currencyCode, rates), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AppBlueTint), contentAlignment = Alignment.Center) {
                        Text(category.take(1), color = AppBlue, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category, style = MaterialTheme.typography.titleLarge)
                        Text(if (total > 0) "${(value / total * 100).toInt()}% 的全年支出" else "暂无支出", color = AppSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(formatMoney(value, currencyCode, rates), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SavingsCard(
    message: String,
    expensive: Subscription?,
    currencyCode: String,
    rates: Map<String, Double>,
    modifier: Modifier = Modifier,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("省钱建议", color = AppBlue, style = MaterialTheme.typography.titleLarge)
            Text(message, color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
            if (expensive != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "最高可影响 ${formatMoney(BillingCalculator.annualizedPrice(expensive), currencyCode, rates)}/年",
                    color = AppBlue,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ChargeScheduleRow(
    occurrence: BillingCalculator.ChargeOccurrence,
    currencyCode: String,
    rates: Map<String, Double>,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(AppFill),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${occurrence.date.dayOfMonth}", color = AppInk, fontWeight = FontWeight.Bold)
                    Text("日", color = AppSecondary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            ServiceIcon(
                occurrence.subscription.name,
                occurrence.subscription.icon,
                occurrence.subscription.image,
                occurrence.subscription.color,
                occurrence.subscription.iconKey,
                Modifier.size(40.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(occurrence.subscription.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(occurrence.subscription.cycle.label, color = AppSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(formatMoney(occurrence.subscription.priceCny, currencyCode, rates), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BarChart(values: List<Double>, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val gap = size.width / values.size
        val barWidth = (gap * .55f).coerceAtMost(28.dp.toPx())
        values.forEachIndexed { index, value ->
            val height = (size.height * (value / max)).toFloat().coerceAtLeast(if (value > 0) 4.dp.toPx() else 0f)
            drawRoundRect(
                color = if (index == LocalDate.now().monthValue - 1) AppBlue else Color(0xFFD1D1D6),
                topLeft = androidx.compose.ui.geometry.Offset(index * gap + (gap - barWidth) / 2, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

@Composable
internal fun ProfileScreen(
    state: AppUiState,
    layout: AdaptiveLayoutSpec,
    viewModel: AppViewModel,
    fileActions: FileActions,
) {
    val user = state.session?.user ?: return
    var editingName by remember { mutableStateOf(false) }
    var name by remember(user.displayName) { mutableStateOf(user.displayName) }
    var currencyMenu by remember { mutableStateOf(false) }
    var showLegacyImport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    PageContainer(layout, maxContentWidthDp = if (layout.isExpanded) 840 else layout.contentMaxWidthDp) {
        item { PageHeader("个人中心", if (state.dirty) "本机有待同步修改" else "数据已安全保存") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(AppBlueTint), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = AppBlue, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text(user.displayName, style = MaterialTheme.typography.headlineSmall); Text(user.email, color = AppSecondary, style = MaterialTheme.typography.bodyMedium) }
                    IconButton(onClick = { editingName = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.Edit, "修改昵称", tint = AppBlue) }
                }
            }
        }
        item { SectionTitle("云端与数据", state.lastSyncedAt?.let { "已同步" } ?: "等待同步") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = if (state.dirty) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                    title = if (state.dirty) "等待同步" else "云端已同步",
                    subtitle = state.lastSyncedAt ?: "尚无同步记录",
                    action = { IconButton(onClick = { viewModel.syncNow() }) { Icon(Icons.Outlined.Refresh, "立即同步", tint = AppBlue) } },
                )
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("自动同步频率", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(SyncFrequency.entries) { value ->
                            FilterChip(
                                selected = state.syncFrequency == value,
                                onClick = { viewModel.setSyncFrequency(value) },
                                label = { Text(value.label) },
                                modifier = Modifier.heightIn(min = MinimumTouchTargetDp.dp),
                            )
                        }
                    }
                }
            }
        }
        item { SectionTitle("显示与账户", user.currencyCode) }
        item {
            SettingsCard {
                Box {
                    SettingsRow(Icons.Outlined.Language, "显示货币", user.currencyCode, tint = AppPurple, tintBackground = AppPurpleTint, action = {
                        TextButton(onClick = { currencyMenu = true }) { Text("切换") }
                    })
                    DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                        state.currencyRates.currencies.forEach { currency ->
                            DropdownMenuItem(text = { Text("${currency.name} · ${currency.code}") }, onClick = { currencyMenu = false; viewModel.updateCurrency(currency.code) })
                        }
                    }
                }
                HorizontalDivider()
                if (user.isAdmin) {
                    SettingsRow(Icons.AutoMirrored.Outlined.OpenInNew, "管理控制台", "仅超级管理员可见", tint = AppIndigo, tintBackground = AppIndigoTint, action = {
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://admin.netkaize.com/"))) }) { Icon(Icons.Outlined.ChevronRight, null, tint = AppBlue) }
                    })
                }
            }
        }
        item { SectionTitle("备份与迁移", "版本化 JSON") }
        if (state.hasUnassignedLegacy) {
            item {
                SettingsCard {
                    SettingsRow(
                        Icons.Outlined.Backup,
                        "发现旧版未归属账本",
                        "旧网页未留下可核验的账户信息，需由你确认后合并",
                        tint = AppOrange,
                        tintBackground = AppOrangeTint,
                        action = { TextButton(onClick = { showLegacyImport = true }) { Text("导入") } },
                    )
                }
            }
        }
        item {
            SettingsCard {
                SettingsRow(Icons.Outlined.Download, "导出我的订阅", "生成可恢复的本机备份", tint = AppGreen, tintBackground = AppGreenTint, action = {
                    IconButton(onClick = { fileActions.export("订阅备份-${LocalDate.now()}.json", viewModel.exportBackup()) }) { Icon(Icons.Outlined.ChevronRight, null, tint = AppBlue) }
                })
                HorizontalDivider()
                SettingsRow(Icons.Outlined.Upload, "导入订阅备份", "兼容网页端导出的 JSON", tint = AppOrange, tintBackground = AppOrangeTint, action = {
                    IconButton(onClick = fileActions.import) { Icon(Icons.Outlined.ChevronRight, null, tint = AppBlue) }
                })
                HorizontalDivider()
                SettingsRow(Icons.Outlined.Backup, "恢复最近本机历史", "每次编辑前自动留存", tint = AppGray, tintBackground = AppGrayTint, action = {
                    TextButton(onClick = viewModel::restoreLatestBackup) { Text("恢复") }
                })
            }
        }
        item {
            // iOS destructive row: white grouped cell, centered red label.
            Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::logout)) {
                Text(
                    "退出登录",
                    color = AppRed,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
        }
    }
    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("修改昵称") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true, label = { Text("新昵称") }) },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("取消") } },
            confirmButton = { Button(onClick = { editingName = false; viewModel.updateDisplayName(name) }) { Text("保存") } },
        )
    }
    if (showLegacyImport) {
        AlertDialog(
            onDismissRequest = { showLegacyImport = false },
            title = { Text("合并旧版账本？") },
            text = { Text("旧版数据无法自动确认所属账号。合并前会先备份当前账本；重名 ID 会保留为两条记录，避免覆盖。") },
            dismissButton = { TextButton(onClick = { showLegacyImport = false }) { Text("暂不导入") } },
            confirmButton = {
                Button(onClick = { showLegacyImport = false; viewModel.importUnassignedLegacy() }) {
                    Text("确认合并")
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color = AppBlue,
    tintBackground: Color = AppBlueTint,
    action: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tintBackground), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = AppSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        action()
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.displaySmall); Text(subtitle, color = AppSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun SubscriptionEditor(
    initial: Subscription?,
    currencyCode: String,
    rate: Double,
    categoryOptions: List<String>,
    pickImage: (((String) -> Unit)) -> Unit,
    onSave: (Subscription) -> Unit,
    onCancel: () -> Unit,
) {
    val today = LocalDate.now()
    val context = LocalContext.current
    val base = initial ?: Subscription(name = "", startDate = today, nextDate = today.plusMonths(1), renewalAnchorDate = today)
    var name by remember(initial?.id) { mutableStateOf(base.name) }
    var category by remember(initial?.id) { mutableStateOf(base.category) }
    var price by remember(initial?.id) { mutableStateOf(if (initial == null) "" else "%.2f".format(base.priceCny * rate)) }
    var cycle by remember(initial?.id) { mutableStateOf(base.cycle) }
    var status by remember(initial?.id) { mutableStateOf(base.status) }
    var startDate by remember(initial?.id) { mutableStateOf(base.startDate.toString()) }
    var nextDate by remember(initial?.id) { mutableStateOf(base.nextDate?.toString().orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(base.note) }
    var officialUrl by remember(initial?.id) { mutableStateOf(base.officialUrl) }
    var manageUrl by remember(initial?.id) { mutableStateOf(base.manageUrl) }
    var image by remember(initial?.id) { mutableStateOf(base.image) }
    var iconKey by remember(initial?.id) { mutableStateOf(base.iconKey ?: if (base.image.isBlank()) "sparkles" else null) }
    var cycleMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable
    fun CycleSelector(modifier: Modifier = Modifier) {
        Box(modifier) {
            OutlinedButton(
                onClick = { cycleMenu = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("周期：${cycle.label}", maxLines = 2) }
            DropdownMenu(expanded = cycleMenu, onDismissRequest = { cycleMenu = false }) {
                BillingCycle.entries.forEach { value ->
                    DropdownMenuItem(text = { Text(value.label) }, onClick = {
                        cycle = value
                        cycleMenu = false
                        val parsed = runCatching { LocalDate.parse(startDate) }.getOrElse { today }
                        nextDate = when (value) {
                            BillingCycle.MONTHLY -> parsed.plusMonths(1).toString()
                            BillingCycle.YEARLY -> parsed.plusYears(1).toString()
                            BillingCycle.ONCE -> ""
                        }
                    })
                }
            }
        }
    }

    @Composable
    fun StatusSelector(modifier: Modifier = Modifier) {
        Box(modifier) {
            OutlinedButton(
                onClick = { statusMenu = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("状态：${status.label}", maxLines = 2) }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                SubscriptionStatus.entries.forEach { value ->
                    DropdownMenuItem(text = { Text(value.label) }, onClick = {
                        status = value
                        statusMenu = false
                    })
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackSelectors = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 840.dp).imePadding(),
            contentPadding = PaddingValues(
                start = if (maxWidth < 360.dp) 16.dp else 20.dp,
                end = if (maxWidth < 360.dp) 16.dp else 20.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(if (initial == null || initial.name.isBlank()) "添加订阅" else "编辑订阅", style = MaterialTheme.typography.headlineMedium) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("服务图标", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceIcon(
                            name = name.ifBlank { "自定义服务" },
                            icon = base.icon.ifBlank { name.take(1) },
                            image = image,
                            color = base.color,
                            iconKey = iconKey,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { pickImage { selected -> image = selected; iconKey = null } },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Image, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (image.isBlank()) "上传图片" else "更换图片")
                        }
                        if (image.isNotBlank()) {
                            TextButton(onClick = { image = ""; iconKey = "sparkles" }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("移除")
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(serviceIconChoices, key = { it.first }) { (key, vector) ->
                            val selected = image.isBlank() && iconKey == key
                            FilledTonalButton(
                                onClick = { image = ""; iconKey = key },
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) AppBlueTint else AppFill,
                                    contentColor = if (selected) AppBlue else AppSecondary,
                                ),
                            ) {
                                Icon(vector, contentDescription = "选择图标")
                            }
                        }
                    }
                }
            }
            item { OutlinedTextField(name, { name = it.take(32) }, label = { Text("服务名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categoryOptions, key = { it }) { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(value) },
                        )
                    }
                }
            }
            item { OutlinedTextField(category, { category = it.take(16) }, label = { Text("服务类目") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item { OutlinedTextField(price, { price = it }, label = { Text(if (cycle == BillingCycle.ONCE) "订阅服务费用（$currencyCode）" else "单次价格（$currencyCode）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item {
                if (stackSelectors) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CycleSelector(Modifier.fillMaxWidth())
                        StatusSelector(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CycleSelector(Modifier.weight(1f))
                        StatusSelector(Modifier.weight(1f))
                    }
                }
            }
            item { OutlinedTextField(startDate, { startDate = it.take(10) }, label = { Text("首次订阅日期") }, supportingText = { Text("格式：YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            if (cycle != BillingCycle.ONCE) item { OutlinedTextField(nextDate, { nextDate = it.take(10) }, label = { Text("下一次扣费日（可修改）") }, supportingText = { Text("后续按自然月或自然年推算") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item { OutlinedTextField(officialUrl, { officialUrl = it.take(240) }, label = { Text("官网订阅地址（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            item { OutlinedTextField(manageUrl, { manageUrl = it.take(240) }, label = { Text("订阅管理地址（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            if (validHttpUrl(officialUrl) || validHttpUrl(manageUrl)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (validHttpUrl(officialUrl)) {
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl))) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("前往官网订阅", maxLines = 2) }
                        }
                        if (validHttpUrl(manageUrl)) {
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(manageUrl))) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("管理订阅", maxLines = 2) }
                        }
                    }
                }
            }
            item { OutlinedTextField(note, { note = it.take(200) }, label = { Text("备注（可选）") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) }
            error?.let { item { Text(it, color = AppRed) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                    Button(onClick = {
                        runCatching {
                            val parsedStart = LocalDate.parse(startDate)
                            val parsedNext = if (cycle == BillingCycle.ONCE) null else LocalDate.parse(nextDate)
                            require(name.isNotBlank() && category.isNotBlank()) { "请填写名称和类目" }
                            require(parsedNext == null || parsedNext > parsedStart) { "下一次扣费日需晚于首次订阅日" }
                            require(officialUrl.isBlank() || validHttpUrl(officialUrl)) { "官网地址需以 http:// 或 https:// 开头" }
                            require(manageUrl.isBlank() || validHttpUrl(manageUrl)) { "管理地址需以 http:// 或 https:// 开头" }
                            val displayPrice = price.toDoubleOrNull() ?: error("请输入有效费用")
                            val openPause = base.pauses.indexOfLast { it.endDate == null }
                            val pauses = when {
                                status == SubscriptionStatus.PAUSED && openPause == -1 -> base.pauses + com.netkaize.subscription.data.PausePeriod(today)
                                status == SubscriptionStatus.ACTIVE && openPause >= 0 -> base.pauses.mapIndexed { index, period -> if (index == openPause) period.copy(endDate = today) else period }
                                else -> base.pauses
                            }
                            base.copy(
                                name = name.trim(), category = category.trim(), note = note.trim(), priceCny = displayPrice / rate.coerceAtLeast(.0000001),
                                cycle = cycle, startDate = parsedStart, nextDate = parsedNext,
                                renewalAnchorDate = if (cycle == BillingCycle.ONCE) null else if (parsedNext == when (cycle) { BillingCycle.MONTHLY -> parsedStart.plusMonths(1); BillingCycle.YEARLY -> parsedStart.plusYears(1); BillingCycle.ONCE -> parsedStart }) parsedStart else parsedNext,
                                status = status, officialUrl = officialUrl.trim(), manageUrl = manageUrl.trim(),
                                canceledAt = if (status == SubscriptionStatus.CANCELED) base.canceledAt ?: today else null,
                                pauses = pauses, icon = base.icon.ifBlank { name.take(1) },
                                iconKey = iconKey, image = image,
                            )
                        }.onSuccess(onSave).onFailure { error = it.message ?: "请检查输入内容" }
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) { Text("保存订阅", maxLines = 2) }
                }
            }
        }
    }
}

private fun validHttpUrl(value: String): Boolean = runCatching {
    val uri = Uri.parse(value.trim())
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
