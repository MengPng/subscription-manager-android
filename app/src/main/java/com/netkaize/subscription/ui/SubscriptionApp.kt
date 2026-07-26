package com.netkaize.subscription.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.netkaize.subscription.R
import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.ServiceTemplate
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.SubscriptionStatus
import com.netkaize.subscription.data.SyncFrequency
import com.netkaize.subscription.domain.BillingCalculator
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

data class FileActions(
    val export: (String, String) -> Unit,
    val import: () -> Unit,
    val pickImage: (((String) -> Unit)) -> Unit,
)

internal const val MinimumTouchTargetDp = 48

internal enum class AppWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal data class AdaptiveLayoutSpec(
    val widthClass: AppWindowWidthClass,
    val pagePaddingDp: Int,
    val contentMaxWidthDp: Int,
) {
    val isCompact: Boolean get() = widthClass == AppWindowWidthClass.COMPACT
    val isExpanded: Boolean get() = widthClass == AppWindowWidthClass.EXPANDED
}

internal fun adaptiveLayoutSpec(widthDp: Int): AdaptiveLayoutSpec = when {
    widthDp < 360 -> AdaptiveLayoutSpec(AppWindowWidthClass.COMPACT, pagePaddingDp = 16, contentMaxWidthDp = 600)
    widthDp < 600 -> AdaptiveLayoutSpec(AppWindowWidthClass.COMPACT, pagePaddingDp = 20, contentMaxWidthDp = 600)
    widthDp < 840 -> AdaptiveLayoutSpec(AppWindowWidthClass.MEDIUM, pagePaddingDp = 24, contentMaxWidthDp = 760)
    else -> AdaptiveLayoutSpec(AppWindowWidthClass.EXPANDED, pagePaddingDp = 32, contentMaxWidthDp = 1180)
}

@Composable
fun SubscriptionApp(viewModel: AppViewModel, fileActions: FileActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    DingYueTheme {
        Box(Modifier.fillMaxSize().background(AppCanvas)) {
            if (state.session == null) {
                AuthScreen(state, viewModel)
            } else {
                MainShell(state, viewModel, fileActions, snackbar)
            }
            if (state.busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color.White, shadowElevation = 8.dp) {
                        CircularProgressIndicator(Modifier.padding(18.dp).size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
            state.conflict?.let {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("发现两个账本版本") },
                    text = { Text("本机和云端都发生过修改。两个版本已自动备份，请选择本次保留哪一份。") },
                    dismissButton = { TextButton(onClick = viewModel::resolveConflictUseCloud) { Text("使用云端") } },
                    confirmButton = { Button(onClick = viewModel::resolveConflictUseLocal) { Text("保留本机并上传") } },
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(state: AppUiState, viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val mode = state.authMode
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val narrow = maxWidth < 360.dp
        val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
        val stackAuthControls = narrow || largeText
        val short = maxHeight < 480.dp
        val compactHeight = maxHeight < 650.dp
        val horizontal = if (narrow) 16.dp else 28.dp
        val maxFormWidth = 520.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compactHeight) 8.dp else 32.dp))
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_brand),
                contentDescription = "订阅",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(if (short) 150.dp else if (narrow) 180.dp else 210.dp)
                    .height(if (short) 80.dp else if (narrow) 96.dp else 112.dp),
            )
            Text(
                text = when (mode) {
                    AuthMode.LOGIN -> "登录后，数据随你而行"
                    AuthMode.REGISTER -> "创建账户，开始云端记录"
                    AuthMode.RESET -> "找回你的订阅账本"
                },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本机即时读取，云端安全同步。",
                color = AppSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().widthIn(max = maxFormWidth).clip(RoundedCornerShape(10.dp)).background(AppSearchFill).padding(2.dp),
            ) {
                listOf(AuthMode.LOGIN to "登录", AuthMode.REGISTER to "创建账户").forEach { (item, label) ->
                    val selected = mode == item
                    TextButton(
                        onClick = { viewModel.setAuthMode(item) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = if (selected) Color.White else Color.Transparent),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text(label, color = if (selected) AppInk else AppSecondary, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = maxFormWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mode == AuthMode.REGISTER) AppTextField(name, { name = it }, "昵称", "怎么称呼你")
                AppTextField(email, { email = it }, "账号", "邮箱地址", KeyboardType.Email)
                if (mode != AuthMode.LOGIN) {
                    if (stackAuthControls) {
                        AppTextField(code, { code = it.filter(Char::isDigit).take(6) }, "邮箱验证码", "6 位验证码", KeyboardType.Number)
                        FilledTonalButton(
                            onClick = { viewModel.sendCode(email) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("发送验证码") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppTextField(code, { code = it.filter(Char::isDigit).take(6) }, "邮箱验证码", "6 位验证码", KeyboardType.Number, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { viewModel.sendCode(email) },
                                modifier = Modifier.height(56.dp).sizeIn(minWidth = 104.dp),
                            ) { Text("发送验证码") }
                        }
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (mode == AuthMode.LOGIN) "登录密码" else "设置密码") },
                    placeholder = { Text("至少 8 位") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.authenticate(email, password, code, name) }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Button(
                    onClick = { viewModel.authenticate(email, password, code, name) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(when (mode) { AuthMode.LOGIN -> "登录并同步"; AuthMode.REGISTER -> "创建账户"; AuthMode.RESET -> "重设密码并登录" })
                }
                if (mode == AuthMode.LOGIN) {
                    TextButton(onClick = { viewModel.setAuthMode(AuthMode.RESET) }, modifier = Modifier.align(Alignment.End).sizeIn(minHeight = 48.dp)) {
                        Text("忘记密码？")
                    }
                } else if (mode == AuthMode.RESET) {
                    TextButton(onClick = { viewModel.setAuthMode(AuthMode.LOGIN) }, modifier = Modifier.align(Alignment.End).sizeIn(minHeight = 48.dp)) {
                        Text("返回登录")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "验证码 10 分钟有效；同邮箱 5 分钟后可重发，每日最多 3 次。",
                color = AppSecondary,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: AppUiState,
    viewModel: AppViewModel,
    fileActions: FileActions,
    snackbar: SnackbarHostState,
) {
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AdaptiveNavigationFrame(
            destination = state.destination,
            onNavigate = viewModel::navigate,
            snackbar = snackbar,
        ) { scaffoldPadding, layout ->
            Box(
                Modifier.fillMaxSize().padding(scaffoldPadding).background(AppCanvas),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (state.destination) {
                    MainDestination.HOME -> HomeScreen(
                        state = state,
                        layout = layout,
                        onEdit = { editing = it; showEditor = true },
                        onDelete = viewModel::deleteSubscription,
                        onScheduleCancel = { subscription, date ->
                            viewModel.saveSubscription(
                                subscription.copy(scheduledCancelDate = date, lastReviewedAt = null),
                            )
                        },
                        onConfirmRenewal = { subscription ->
                            viewModel.saveSubscription(subscription.copy(lastReviewedAt = LocalDate.now()))
                        },
                    )
                    MainDestination.SUBSCRIPTIONS -> SubscriptionsScreen(state, layout, onEdit = { editing = it; showEditor = true }, onDelete = viewModel::deleteSubscription)
                    MainDestination.ADD -> AddScreen(
                        state,
                        layout,
                        onCustom = { editing = null; showEditor = true },
                        onTemplate = { editing = it.toSubscription(); showEditor = true },
                        onExisting = { editing = it; showEditor = true },
                    )
                    MainDestination.ANALYSIS -> AnalysisScreen(state, layout)
                    MainDestination.PROFILE -> ProfileScreen(state, layout, viewModel, fileActions)
                }
            }
        }
        if (showEditor) {
            ModalBottomSheet(
                onDismissRequest = { showEditor = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                        SubscriptionEditor(
                            initial = editing,
                            currencyCode = state.session?.user?.currencyCode ?: "CNY",
                            rate = state.currencyRates.rates[state.session?.user?.currencyCode] ?: 1.0,
                            categoryOptions = (
                                listOf("协作", "AI", "经营", "娱乐", "学习", "生活", "云服务", "健康", "其他") +
                                    state.templates.map { it.category } +
                                    state.subscriptions.map { it.category }
                                ).filter(String::isNotBlank).distinct(),
                            pickImage = fileActions.pickImage,
                            onSave = { viewModel.saveSubscription(it); showEditor = false },
                            onCancel = { showEditor = false },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveNavigationFrame(
    destination: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues, AdaptiveLayoutSpec) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = adaptiveLayoutSpec(maxWidth.value.toInt())
        val shortHeight = maxHeight < 480.dp
        val snackbarHost: @Composable () -> Unit = {
            if (snackbar != null) SnackbarHost(snackbar)
        }
        if (layout.isCompact) {
            Scaffold(
                modifier = Modifier.fillMaxSize().testTag("adaptive_compact_shell"),
                containerColor = AppCanvas,
                snackbarHost = snackbarHost,
                bottomBar = {
                    // iOS tab bar: white, hairline top separator, system blue tint,
                    // no indicator pill, uniform items (no raised center button).
                    Column(Modifier.testTag("bottom_navigation").background(Color.White)) {
                        HorizontalDivider(thickness = 0.5.dp, color = AppDivider)
                        NavigationBar(
                            containerColor = Color.White,
                            tonalElevation = 0.dp,
                        ) {
                            destinations.forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item.destination,
                                    onClick = { onNavigate(item.destination) },
                                    modifier = Modifier
                                        .sizeIn(minWidth = MinimumTouchTargetDp.dp, minHeight = MinimumTouchTargetDp.dp)
                                        .testTag("nav_${item.destination.name.lowercase()}"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AppBlue,
                                        selectedTextColor = AppBlue,
                                        unselectedIconColor = AppTertiary,
                                        unselectedTextColor = AppTertiary,
                                        indicatorColor = Color.Transparent,
                                    ),
                                    icon = { Icon(item.icon, item.label) },
                                    label = { Text(item.label, maxLines = 1, fontSize = 10.sp) },
                                )
                            }
                        }
                    }
                },
            ) { padding -> content(padding, layout) }
        } else {
            Row(Modifier.fillMaxSize().background(AppCanvas).testTag("adaptive_rail_shell")) {
                NavigationRail(
                    containerColor = Color.White,
                    modifier = Modifier.fillMaxHeight().widthIn(min = 80.dp).testTag("navigation_rail"),
                ) {
                    Spacer(Modifier.height(if (shortHeight) 4.dp else 16.dp))
                    destinations.forEach { item ->
                        NavigationRailItem(
                            selected = destination == item.destination,
                            onClick = { onNavigate(item.destination) },
                            modifier = Modifier
                                .sizeIn(minWidth = MinimumTouchTargetDp.dp, minHeight = MinimumTouchTargetDp.dp)
                                .testTag("nav_${item.destination.name.lowercase()}"),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = AppBlue,
                                selectedTextColor = AppBlue,
                                unselectedIconColor = AppTertiary,
                                unselectedTextColor = AppTertiary,
                                indicatorColor = AppBlueTint,
                            ),
                            icon = { Icon(item.icon, item.label) },
                            label = if (shortHeight) null else ({ Text(item.label, maxLines = 1) }),
                            alwaysShowLabel = !shortHeight,
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = AppCanvas,
                    snackbarHost = snackbarHost,
                ) { padding -> content(padding, layout) }
            }
        }
    }
}

private data class DestinationItem(val destination: MainDestination, val label: String, val icon: ImageVector)
private val destinations = listOf(
    DestinationItem(MainDestination.HOME, "账本", Icons.Outlined.Home),
    DestinationItem(MainDestination.SUBSCRIPTIONS, "订阅", Icons.Outlined.Inventory2),
    DestinationItem(MainDestination.ADD, "添加", Icons.Outlined.Add),
    DestinationItem(MainDestination.ANALYSIS, "分析", Icons.Outlined.Analytics),
    DestinationItem(MainDestination.PROFILE, "我的", Icons.Outlined.Person),
)

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
    )
}

private fun ServiceTemplate.toSubscription(): Subscription {
    val start = LocalDate.now()
    val next = when (cycle) {
        BillingCycle.MONTHLY -> start.plusMonths(1)
        BillingCycle.YEARLY -> start.plusYears(1)
        BillingCycle.ONCE -> null
    }
    return Subscription(
        name = name,
        category = category,
        priceCny = priceCny,
        cycle = cycle,
        startDate = start,
        nextDate = next,
        renewalAnchorDate = if (cycle == BillingCycle.ONCE) null else start,
        officialUrl = officialUrl,
        manageUrl = manageUrl,
        icon = icon,
        image = image,
        color = color,
    )
}

@Composable
fun ServiceIcon(
    name: String,
    icon: String,
    image: String,
    color: String,
    iconKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val background = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrElse { AppBlue }
    Box(modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        if (image.isNotBlank()) {
            val source = if (image.startsWith("/")) "https://subscription.netkaize.com$image" else image
            AsyncImage(model = source, contentDescription = name, modifier = Modifier.fillMaxSize())
        } else if (serviceVectorIcon(iconKey) != null) {
            Icon(serviceVectorIcon(iconKey)!!, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
        } else {
            Text(icon.ifBlank { name.take(1) }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

internal val serviceIconChoices = listOf(
    "sparkles" to Icons.Outlined.AutoAwesome,
    "cloud" to Icons.Outlined.Cloud,
    "music" to Icons.Outlined.MusicNote,
    "play" to Icons.Outlined.PlayArrow,
    "book" to Icons.Outlined.MenuBook,
    "heart" to Icons.Outlined.FavoriteBorder,
    "game" to Icons.Outlined.SportsEsports,
    "chart" to Icons.Outlined.BarChart,
)

internal fun serviceVectorIcon(key: String?): ImageVector? = serviceIconChoices.firstOrNull { it.first == key }?.second

fun formatMoney(cnyValue: Double, code: String, rates: Map<String, Double>): String {
    val rate = rates[code] ?: 1.0
    return runCatching {
        NumberFormat.getCurrencyInstance(Locale.CHINA).apply { currency = Currency.getInstance(code) }.format(cnyValue * rate)
    }.getOrElse { "$code ${"%.2f".format(cnyValue * rate)}" }
}
