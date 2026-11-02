package app.detent.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import app.detent.launcher.AppEntry
import app.detent.launcher.Ease
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun SearchSheet(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val move = (40 * u.k).toInt()
    AnimatedVisibility(
        s.search,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = Ease)) { move },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(400, easing = Ease)) { move },
    ) {
        val focus = remember { FocusRequester() }
        val kb = LocalSoftwareKeyboardController.current
        // The keyboard waits for a tap on the field, so the whole list stays in view.
        LaunchedEffect(Unit) {
            if (!s.typeSearch) return@LaunchedEffect
            delay(80)
            runCatching { focus.requestFocus() }
            kb?.show()
        }
        val q = s.query
        val results = remember(q, s.repo.apps) { s.matches(q) }
        val recent = s.recentApps
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(pal.sheet).tap { }.imePadding()) {
                Spacer(Modifier.height(u.dp(max(60f, u.top + 20f))))
                Row(
                    Modifier.padding(start = u.dp(28f), end = u.dp(18f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
                ) {
                    BasicTextField(
                        value = q,
                        onValueChange = { s.query = it },
                        modifier = Modifier.weight(1f).height(u.dp(52f)).focusRequester(focus)
                            .onGloballyPositioned { s.searchFieldY = (it.positionInRoot().y + it.size.height / 2f) / u.k }
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                                    s.back()
                                    true
                                } else {
                                    false
                                }
                            },
                        singleLine = true,
                        textStyle = textStyle(u, 26f, pal.fg, FontWeight.Light, spacing = -.03f),
                        cursorBrush = SolidColor(pal.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { s.searchEnter() }),
                        decorationBox = { inner ->
                            Box(
                                Modifier.fillMaxSize().drawBehind {
                                    val y = size.height - u.k / 2
                                    drawLine(pal.rim2, Offset(0f, y), Offset(size.width, y), strokeWidth = u.k)
                                },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val hint = when {
                                    s.pick -> "pick a quick app"
                                    s.web -> "search the web"
                                    else -> "search apps"
                                }
                                if (q.isEmpty()) Txt(hint, 26f, pal.muted, weight = FontWeight.Light, spacing = -.03f, maxLines = 1)
                                inner()
                            }
                        },
                    )
                    Box(Modifier.height(u.dp(44f)).tap { s.closeSearch() }.padding(horizontal = u.dp(10f)), contentAlignment = Alignment.Center) {
                        Txt("cancel", 13f, pal.muted2)
                    }
                }
                val n = s.dialApps.size
                val count = when {
                    s.web -> "results open in your browser"
                    q.isNotBlank() -> "${results.size} " + if (results.size == 1) "match" else "matches"
                    s.pick -> "tap an app for the quick button"
                    else -> "${results.size} apps · $n on dial"
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = u.dp(28f), top = u.dp(16f), end = u.dp(18f), bottom = u.dp(8f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(count, 11f, pal.muted, Modifier.weight(1f), spacing = .06f, maxLines = 1)
                    if (s.pick && s.quickKey != null) {
                        Box(Modifier.height(u.dp(32f)).tap { s.clearQuick() }.padding(horizontal = u.dp(10f)), contentAlignment = Alignment.Center) {
                            Txt("clear", 11f, pal.accent, spacing = .06f)
                        }
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = u.dp(12f), end = u.dp(12f), bottom = u.dp(48f)),
                ) {
                    if (s.web) {
                        if (q.isNotBlank()) {
                            item(key = "web") { WebRow(s, pal, q.trim()) }
                        } else if (s.recentSearchWords.isNotEmpty()) {
                            item(key = "recent") { Section("recent", pal, "clear") { s.clearRecentSearches() } }
                            itemsIndexed(s.recentSearchWords, key = { _, words -> "recent $words" }) { _, words ->
                                RecentSearchRow(s, pal, words)
                            }
                        }
                        return@LazyColumn
                    }
                    if (q.isBlank() && recent.isNotEmpty()) {
                        item(key = "recent") { Section("recent", pal, if (s.pick) null else "clear") { s.clearRecent() } }
                        itemsIndexed(recent, key = { _, a -> "recent " + a.key }) { _, app ->
                            ResultRow(s, pal, app, "recent " + app.key, highlight = false, recent = true)
                        }
                        item(key = "all") { Section("all apps", pal) }
                    }
                    itemsIndexed(results, key = { _, a -> a.key }) { i, app ->
                        ResultRow(s, pal, app, app.key, highlight = q.isNotBlank() && i == 0)
                    }
                }
            }
            SearchMenuView(s, pal)
        }
    }
}

/** The hold menu on a search row. It hangs below the row, like the hold menu on the dial. */
@Composable
private fun SearchMenuView(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) / u.k
    var last by remember { mutableStateOf(s.searchMenu) }
    s.searchMenu?.let { last = it }
    if (s.searchMenu == null) return
    val m = last ?: return
    val app = m.app
    val veil by animateFloatAsState(if (s.searchMenuShown) 1f else 0f, tween(200), label = "searchMenuVeil")
    val op by animateFloatAsState(if (s.searchMenuShown) 1f else 0f, tween(180), label = "searchMenuAlpha")
    val t by animateFloatAsState(if (s.searchMenuShown) 1f else 0f, tween(220, easing = Ease), label = "searchMenuMove")
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil }.background(pal.veil).tap { s.closeSearchMenu() })
    // Near the bottom or above the keyboard, the menu moves up so that it stays in view.
    val menuHeight = 250f
    val top = minOf(m.y + 4f, u.hd - u.bottom - menuHeight, u.hd - ime - menuHeight)
    val shape = RoundedCornerShape(u.dp(16f))
    Column(
        Modifier.place(u, 24f, top).width(u.dp(220f))
            .graphicsLayer {
                alpha = op
                scaleX = .95f + .05f * t
                scaleY = .95f + .05f * t
                translationY = -6f * (1 - t) * u.k
                transformOrigin = TransformOrigin(.12f, 0f)
            }
            .shadow(u.dp(16f), shape).clip(shape).background(pal.card).border(u.dp(1f), pal.rim2, shape)
            .tap { }
            .padding(u.dp(6f)),
    ) {
        MenuTitle(app, s.nameHint(app), pal)
        MenuItem("rename", pal.fg) { s.searchMenuRename() }
        if (app.renamed) MenuItem("reset to ${app.systemLabel}", pal.muted2) { s.searchMenuResetName() }
        Box(Modifier.fillMaxWidth().height(u.dp(1f)).background(pal.rim))
        MenuItem("app info", pal.muted2) { s.searchMenuInfo() }
        MenuItem(if (app.key in s.dialKeys) "remove from dial" else "add to dial", pal.muted2) { s.searchMenuDial() }
    }
}

/** The app name at the top of a hold menu, with the [hint] that tells apps of the same name apart. */
@Composable
fun MenuTitle(app: AppEntry, hint: String?, pal: Palette) {
    val u = LocalUnits.current
    Column(
        Modifier.padding(start = u.dp(12f), top = u.dp(10f), end = u.dp(12f), bottom = u.dp(8f)),
        verticalArrangement = Arrangement.spacedBy(u.dp(2f)),
    ) {
        Txt(app.display, 11f, pal.muted, spacing = .06f, maxLines = 1)
        if (hint != null) Txt(hint, 9f, pal.muted, maxLines = 2)
    }
}

/**
 * A one-line field for an app's own name. It takes the focus and brings up the keyboard as it appears.
 * The caller opens it with the whole name selected, so typing replaces the name.
 */
@Composable
fun NameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pal: Palette,
    textSize: Float,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = LocalUnits.current
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
        kb?.show()
    }
    // The field leaves on save, reset or cancel. Nothing else wants typing then, so the keyboard goes too.
    DisposableEffect(Unit) { onDispose { kb?.hide() } }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(u.dp(40f)).focusRequester(focus),
        singleLine = true,
        textStyle = textStyle(u, textSize, pal.fg),
        cursorBrush = SolidColor(pal.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val y = size.height - u.k / 2
                    drawLine(pal.accent, Offset(0f, y), Offset(size.width, y), strokeWidth = u.k)
                },
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

/** The accent pill that saves a name. */
@Composable
fun SavePill(pal: Palette, onClick: () -> Unit) {
    val u = LocalUnits.current
    Box(Modifier.height(u.dp(44f)).tap(onClick = onClick).padding(horizontal = u.dp(4f)), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(u.dp(28f)).clip(RoundedCornerShape(50)).background(pal.accent).padding(horizontal = u.dp(12f)),
            contentAlignment = Alignment.Center,
        ) { Txt("save", 11f, pal.onAccent, weight = FontWeight.Medium, maxLines = 1) }
    }
}

/** The whole name selected, so the first key typed replaces it. */
fun selectedName(name: String) = TextFieldValue(name, TextRange(0, name.length))

/** A search row that became a name field after "rename" in its hold menu. */
@Composable
private fun RenameRow(s: LauncherState, pal: Palette, app: AppEntry) {
    val u = LocalUnits.current
    var text by remember(app.key) { mutableStateOf(selectedName(app.label)) }
    val bringIntoView = remember { BringIntoViewRequester() }
    // The keyboard slides up after the field takes the focus, so the row scrolls into view once it is up.
    LaunchedEffect(Unit) {
        delay(400)
        bringIntoView.bringIntoView()
    }
    val save = { s.rename(app, text.text) }
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).clip(RoundedCornerShape(u.dp(10f))).background(pal.hover)
            .padding(bottom = u.dp(4f)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(u.dp(48f)).padding(start = u.dp(16f), end = u.dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(8f)),
        ) {
            NameField(text, { text = it }, pal, 16f, onDone = save, modifier = Modifier.weight(1f))
            SavePill(pal, save)
        }
        // The package tells two apps of the same name apart while the user picks a new name.
        Txt(app.component.packageName, 10f, pal.muted, Modifier.padding(horizontal = u.dp(16f)), maxLines = 1)
        Row(Modifier.padding(start = u.dp(4f))) {
            if (app.renamed) TextAction("reset to ${app.systemLabel}", pal) { s.rename(app, "") }
            TextAction("cancel", pal) { s.cancelRename() }
        }
    }
}

@Composable
private fun TextAction(text: String, pal: Palette, onClick: () -> Unit) {
    val u = LocalUnits.current
    Box(Modifier.height(u.dp(40f)).tap(onClick = onClick).padding(horizontal = u.dp(12f)), contentAlignment = Alignment.Center) {
        Txt(text, 11f, pal.muted2, spacing = .04f, maxLines = 1)
    }
}

/** A section label in the list, with an optional text button at the end. */
@Composable
private fun Section(text: String, pal: Palette, action: String? = null, onAction: () -> Unit = {}) {
    val u = LocalUnits.current
    Row(Modifier.fillMaxWidth().height(u.dp(40f)).padding(top = u.dp(8f)), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, 11f, pal.muted2, Modifier.weight(1f).padding(start = u.dp(16f)), spacing = .06f, maxLines = 1)
        if (action != null) {
            Box(Modifier.fillMaxHeight().tap(onClick = onAction).padding(horizontal = u.dp(16f)), contentAlignment = Alignment.Center) {
                Txt(action, 11f, pal.muted, spacing = .06f)
            }
        }
    }
}

@Composable
private fun ResultRow(s: LauncherState, pal: Palette, app: AppEntry, row: String, highlight: Boolean, recent: Boolean = false) {
    if (s.renameRow == row) {
        RenameRow(s, pal, app)
        return
    }
    val u = LocalUnits.current
    var centre by remember { mutableFloatStateOf(0f) }
    var bottom by remember { mutableFloatStateOf(0f) }
    val hint = s.nameHint(app)
    val onDial = app.key in s.dialKeys
    Row(
        Modifier.fillMaxWidth().height(u.dp(48f)).clip(RoundedCornerShape(u.dp(10f)))
            .background(if (highlight) pal.hover else Color.Transparent)
            .onGloballyPositioned {
                val y = it.positionInRoot().y
                centre = y + it.size.height / 2f
                bottom = y + it.size.height
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .tapOrHold(onHold = { s.holdInSearch(app, row, bottom / u.k) }) {
                    if (s.pick) s.setQuick(app) else s.openFromSearch(app, centre / u.k)
                }
                .padding(horizontal = u.dp(16f)),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(10f))) {
                Txt(app.label, 16f, pal.fg, Modifier.weight(1f, fill = false), maxLines = 1)
                if (s.hasDot(app)) Box(Modifier.size(u.dp(5f)).clip(CircleShape).background(pal.accent))
            }
            if (hint != null) Txt(hint, 10f, pal.muted, maxLines = 1)
        }
        if (s.pick) {
            if (app.key == s.quickKey) Txt("quick app", 11f, pal.muted, Modifier.padding(horizontal = u.dp(16f)), spacing = .04f)
        } else if (recent) {
            Box(Modifier.size(u.dp(44f)).tap { s.removeRecent(app) }, contentAlignment = Alignment.Center) {
                Cross(pal.muted, u.dp(10f))
            }
        } else if (onDial) {
            Txt("on dial", 11f, pal.muted, Modifier.padding(horizontal = u.dp(16f)), spacing = .04f)
        } else {
            Box(
                Modifier.height(u.dp(44f)).tap { s.addToDial(app) }.padding(start = u.dp(4f), end = u.dp(8f)),
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier.height(u.dp(28f)).clip(shape).border(u.dp(1f), pal.rim2, shape).padding(horizontal = u.dp(12f)),
                    contentAlignment = Alignment.Center,
                ) { Txt("+ dial", 11f, pal.fg, maxLines = 1) }
            }
        }
    }
}

/** The one row of a web search. A tap searches the web for [q], like the go key. */
@Composable
private fun WebRow(s: LauncherState, pal: Palette, q: String) {
    val u = LocalUnits.current
    var centre by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().height(u.dp(48f)).clip(RoundedCornerShape(u.dp(10f))).background(pal.hover)
            .onGloballyPositioned { centre = it.positionInRoot().y + it.size.height / 2f }
            .tap { s.webSearch(centre / u.k) }
            .padding(horizontal = u.dp(16f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(12f)),
    ) {
        SearchIcon(pal.muted)
        Txt(q, 16f, pal.fg, Modifier.weight(1f), maxLines = 1)
        Txt("web search", 11f, pal.muted, spacing = .04f)
    }
}

/** A past web search. A tap searches the same words again, and the cross removes it. */
@Composable
private fun RecentSearchRow(s: LauncherState, pal: Palette, words: String) {
    val u = LocalUnits.current
    var centre by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().height(u.dp(48f)).clip(RoundedCornerShape(u.dp(10f)))
            .onGloballyPositioned { centre = it.positionInRoot().y + it.size.height / 2f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .tap { s.searchAgain(words, centre / u.k) }
                .padding(horizontal = u.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(12f)),
        ) {
            SearchIcon(pal.muted)
            Txt(words, 16f, pal.fg, Modifier.weight(1f), maxLines = 1)
        }
        Box(Modifier.size(u.dp(44f)).tap { s.removeRecentSearch(words) }, contentAlignment = Alignment.Center) {
            Cross(pal.muted, u.dp(10f))
        }
    }
}

/** A small cross that removes a row. */
@Composable
private fun Cross(color: Color, size: Dp) {
    val u = LocalUnits.current
    Canvas(Modifier.size(size)) {
        val w = u.k * 1.2f
        drawLine(color, Offset(0f, 0f), Offset(this.size.width, this.size.height), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(this.size.width, 0f), Offset(0f, this.size.height), strokeWidth = w, cap = StrokeCap.Round)
    }
}
