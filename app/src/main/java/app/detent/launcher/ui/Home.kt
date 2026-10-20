package app.detent.launcher.ui

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import app.detent.launcher.Corner
import app.detent.launcher.Hear
import app.detent.launcher.Ease
import app.detent.launcher.Kind
import app.detent.launcher.LauncherState
import app.detent.launcher.PALETTES
import app.detent.launcher.Palette
import app.detent.launcher.Pose
import app.detent.launcher.Phase
import app.detent.launcher.Spin
import app.detent.launcher.Step
import app.detent.launcher.THEMES
import app.detent.launcher.ThemeDef
import app.detent.launcher.handMatrix
import app.detent.launcher.W
import app.detent.launcher.mod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

internal val DAYS = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
internal val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

@Composable
fun DetentRoot(s: LauncherState) {
    val base = LocalDensity.current
    // The dial geometry is fixed. A font scale of 1 keeps text sizes in design units.
    CompositionLocalProvider(LocalDensity provides Density(base.density, 1f)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = LocalDensity.current
            val k = constraints.maxWidth / W
            val top = WindowInsets.statusBars.getTop(d) / k
            val bottom = WindowInsets.navigationBars.getBottom(d) / k
            val u = remember(k, constraints.maxHeight, top, bottom, d.density) {
                Units(k, d.density, constraints.maxHeight / k, top, bottom)
            }
            SideEffect {
                s.k = u.k
                s.hd = u.hd
                s.clockTop = u.clockTop
                s.searchY = u.searchY
            }
            CompositionLocalProvider(LocalUnits provides u) { Home(s) }
        }
    }
}

@Composable
private fun Home(s: LauncherState) {
    val themeStep = s.ob == Step.THEME
    val live = if (themeStep) mod(s.index, THEMES.size) else s.cfg.theme
    val pal = PALETTES[live]
    // While the theme dial turns, colours follow every tick. The wallpaper waits until the dial stops.
    var wall by remember { mutableIntStateOf(live) }
    val idle = s.spin == Spin.Idle
    LaunchedEffect(live, idle, themeStep) { if (!themeStep || idle) wall = live }
    val bg by animateColorAsState(pal.bg, tween(400), label = "bg")
    SystemBars(pal.light)

    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val fm = LocalFocusManager.current
    LaunchedEffect(s.search) {
        if (!s.search) {
            kb?.hide()
            fm.clearFocus(force = true)
            runCatching { focus.requestFocus() }
        }
    }

    Box(
        Modifier.fillMaxSize().background(bg)
            .focusRequester(focus).focusable()
            .onKeyEvent { it.type == KeyEventType.KeyDown && s.key(it.key) },
    ) {
        // The grid layouts draw the whole screen as dots, with no wallpaper.
        val grid = s.layout.grid
        val wave = rememberVoiceWave(s)
        if (!grid) Wallpaper(THEMES[wall], pal, s.left, s.customWall)
        GestureLayer(s)
        if (grid) {
            DotGrid(s, pal, wave)
        } else {
            Clock(s, pal)
            DialCanvas(s, pal, wave)
        }
        // The readout layout shows the letter in its readout.
        if (s.kind != Kind.Readout) ScrubLetter(s, pal)
        PressRing(s, pal)
        AppsButton(s, pal)
        when (s.corner) {
            Corner.Quick -> QuickButton(s, pal)
            Corner.Web -> WebButton(s, pal)
            Corner.None -> {}
        }
        HearLayer(s, pal)
        if (s.ob != null) Onboarding(s, pal, THEMES[live])
        QuickMenuView(s, pal)
        SearchSheet(s, pal)
        AppSheet(s, pal)
        Title(s, pal)
        ToastView(s, pal)
    }
}

@Composable
private fun SystemBars(light: Boolean) {
    val view = LocalView.current
    LaunchedEffect(light) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

// Wallpaper.

private object Walls {
    private val cache = LruCache<String, ImageBitmap>(4)

    suspend fun load(ctx: Context, path: String): ImageBitmap? = cache.get(path) ?: withContext(Dispatchers.IO) {
        runCatching {
            // An absolute path is the user's own wallpaper. Anything else is an asset.
            val bitmap = if (path.startsWith("/")) BitmapFactory.decodeFile(path)
            else ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }
            bitmap?.asImageBitmap()
        }.getOrNull()
    }?.also { cache.put(path, it) }
}

/** The last loaded image for [path]. The previous image stays until the next one is ready. */
@Composable
private fun rememberAsset(path: String): Pair<String, ImageBitmap>? {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(path) { Walls.load(ctx, path)?.let { shown = path to it } }
    return shown
}

@Composable
private fun Wallpaper(t: ThemeDef, pal: Palette, left: Boolean, custom: String?) {
    val wall = custom ?: t.wall
    if (wall == null) {
        HandArt(pal, left)
        return
    }
    val img = rememberAsset(if (custom == null && left && t.wallLeft != null) t.wallLeft else wall)
    Crossfade(img, animationSpec = tween(450), label = "wall") { p ->
        if (p != null) Image(p.second, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    val b = pal.bg
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to b.copy(alpha = .6f), .32f to b.copy(alpha = .15f), .7f to b.copy(alpha = .2f), 1f to b.copy(alpha = .75f),
            ),
        ),
    )
    // The side with the dial is darker, so the labels stay readable.
    val near = b.copy(alpha = .86f)
    val far = b.copy(alpha = .2f)
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to (if (left) near else far), .5f to b.copy(alpha = .5f), 1f to (if (left) far else near)),
        ),
    )
}

/** The dot-hand art, tinted so the dots take the hand colour and the black takes the background colour. */
@Composable
private fun HandArt(pal: Palette, left: Boolean) {
    val img = rememberAsset("hands.webp") ?: return
    val m = ColorMatrix(handMatrix(pal))
    Image(
        img.second, null,
        Modifier.fillMaxSize().graphicsLayer { scaleX = if (left) -1f else 1f },
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(m),
    )
}

// Input.

@Composable
private fun GestureLayer(s: LauncherState) {
    val u = LocalUnits.current
    val k by rememberUpdatedState(u.k)
    val mid = s.cy
    Box(
        Modifier.fillMaxSize()
            // The dial sits on a screen edge. Keep the system back swipe off the rows around the notch.
            // Search covers the dial, so there the back swipe works on the whole edge.
            .then(
                if (s.search) Modifier else Modifier.systemGestureExclusion { c ->
                    val w = c.size.width.toFloat()
                    val top = (mid - 110f) * u.k
                    val bottom = (mid + 110f) * u.k
                    if (s.left) Rect(0f, top, 56f * u.k, bottom) else Rect(w - 56f * u.k, top, w, bottom)
                },
            )
            .pointerInput(s) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val kk = k
                    s.onDown(down.position.x / kk, down.position.y / kk)
                    var ended = false
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.changedToUp() || !ch.pressed) {
                                s.onUp(ch.position.x / kk, ch.position.y / kk)
                                ended = true
                                break
                            }
                            if (ch.positionChanged()) {
                                s.onMove(ch.position.x / kk, ch.position.y / kk)
                                ch.consume()
                            }
                        }
                    } finally {
                        if (!ended) s.onCancel()
                    }
                }
            }
            .pointerInput(s) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll) {
                            s.onWheel(ev.changes.first().scrollDelta.y * 90f)
                            ev.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    )
}

// Home elements.

@Composable
private fun Clock(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val ctx = LocalContext.current
    val alpha by animateFloatAsState(
        when {
            s.ob != null -> 0f
            s.scrub != null -> .25f
            else -> 1f
        },
        tween(400), label = "clockAlpha",
    )
    val cal = Calendar.getInstance().apply { timeInMillis = s.now }
    val hour = if (DateFormat.is24HourFormat(ctx)) {
        cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    } else {
        (cal.get(Calendar.HOUR).takeIf { it != 0 } ?: 12).toString()
    }
    val time = hour + ":" + cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val date = "${DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} ${MONTHS[cal.get(Calendar.MONTH)]}"
    // While Detent listens, the words take the place of the clock.
    val hear by animateFloatAsState(if (s.hear != null) 1f else 0f, tween(300), label = "clockHear")
    if (hear < .996f) {
        SideBox(s.left, u.clockTop, 28f, Modifier.graphicsLayer { this.alpha = alpha * (1 - hear) }) {
            Column(
                horizontalAlignment = if (s.left) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(u.dp(6f)),
            ) {
                Txt(time, 68f, pal.fg, weight = FontWeight.Light, spacing = -.05f, line = 1f)
                Txt(date, 13f, pal.muted2, spacing = .02f)
            }
        }
    }
    if (hear > .004f) Heard(s, pal, Modifier.graphicsLayer { this.alpha = hear; translationY = (1 - hear) * 6f * u.k })
}

/** The words that Detent heard, in the place of the clock. The app that they name shows below them. */
@Composable
private fun Heard(s: LauncherState, pal: Palette, modifier: Modifier) {
    val u = LocalUnits.current
    val words = s.heardText
    val app = s.heardApp
    val miss = s.hear == Hear.Miss
    val end = if (s.left) TextAlign.End else TextAlign.Start
    SideBox(s.left, u.clockTop, 28f, modifier) {
        Column(
            horizontalAlignment = if (s.left) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            val quiet = miss || words.isEmpty()
            Txt(
                if (quiet) "say an app name" else words, 34f, if (quiet) pal.muted else pal.fg,
                weight = FontWeight.Light, spacing = -.03f, line = 1.1f, align = end, maxLines = 3,
            )
            if (app != null && !miss) {
                Txt(
                    app.display, 13f, pal.accent,
                    Modifier.onGloballyPositioned {
                        val p = it.positionInRoot()
                        s.heardPose = Pose(p.x / u.k, (p.y + it.size.height / 2f) / u.k, 0f, 13f / 44f, 0f)
                    },
                    spacing = .02f, maxLines = 1,
                )
            } else {
                Txt(hearStatus(s), 13f, pal.muted2, spacing = .02f, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AppsButton(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val homeIdle = s.ob == null && !s.search && s.hear == null && s.open == null
    val shown = homeIdle && s.scrub == null
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "searchAlpha")
    val dy by animateFloatAsState(if (shown) 0f else 8f, tween(300), label = "searchY")
    SideBox(s.left, u.searchY, 24f, Modifier.graphicsLayer { this.alpha = alpha; translationY = dy * u.k }) {
        // The voice button sits between apps and the quick button.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(10f))) {
            if (s.left && s.mic) VoiceButton(s, pal, homeIdle)
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.height(u.dp(44f)).clip(shape).background(pal.btnBg).border(u.dp(1f), pal.rim2, shape)
                    .tap(enabled = homeIdle) { s.openSearch() }
                    .padding(start = u.dp(14f), end = u.dp(18f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
            ) {
                // A 3 x 3 grid of squares.
                Canvas(Modifier.size(u.dp(14f))) {
                    val k = u.k
                    for (i in 0..2) for (j in 0..2) {
                        drawRoundRect(pal.btnFg, Offset(i * 5.4f * k, j * 5.4f * k), Size(3.2f * k, 3.2f * k), CornerRadius(.6f * k))
                    }
                }
                Txt("apps", 13f, pal.btnFg)
            }
            if (!s.left && s.mic) VoiceButton(s, pal, homeIdle)
        }
    }
}

/** The voice button. Say the name of an app to open it. */
@Composable
private fun VoiceButton(s: LauncherState, pal: Palette, idle: Boolean) {
    val u = LocalUnits.current
    // The centre of the button in design units.
    var centre by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(u.dp(44f)).clip(CircleShape).background(pal.btnBg).border(u.dp(1f), pal.rim2, CircleShape)
            .onGloballyPositioned {
                val p = it.positionInRoot()
                centre = Offset((p.x + it.size.width / 2f) / u.k, (p.y + it.size.height / 2f) / u.k)
            }
            .semantics { contentDescription = "voice" }
            .tap(enabled = idle) { s.voiceTap(centre.x, centre.y) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(u.dp(14f))) {
            val k = u.k
            val line = Stroke(1.5f * k)
            drawRoundRect(pal.btnFg, Offset(4.5f * k, .75f * k), Size(5f * k, 8f * k), CornerRadius(2.5f * k), style = line)
            drawArc(pal.btnFg, 0f, 180f, false, Offset(2f * k, 3.5f * k), Size(10f * k, 8f * k), style = line)
            drawLine(pal.btnFg, Offset(7f * k, 11.5f * k), Offset(7f * k, 13.5f * k), strokeWidth = 1.5f * k)
        }
    }
}

/** The quick button sits in the bottom corner on the dial side. A tap opens its app. Hold it to pick another app. */
@Composable
private fun QuickButton(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val homeIdle = s.ob == null && !s.search && s.hear == null && s.open == null
    val idle by rememberUpdatedState(homeIdle)
    val shown = homeIdle && s.scrub == null
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "quickAlpha")
    val dy by animateFloatAsState(if (shown) 0f else 8f, tween(300), label = "quickY")
    val app = s.quickApp
    // The start and the centre of the label in design units. The name lifts off from here when the app opens.
    var label by remember { mutableStateOf(Offset.Zero) }
    SideBox(!s.left, u.searchY, 24f, Modifier.graphicsLayer { this.alpha = alpha; translationY = dy * u.k }) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.height(u.dp(44f)).clip(shape).background(pal.btnBg).border(u.dp(1f), pal.rim2, shape)
                .pointerInput(s) {
                    detectTapGestures(
                        onTap = { if (idle) s.quickTap(label.x, label.y) },
                        onLongPress = { if (idle) s.quickHold() },
                    )
                }
                .padding(start = u.dp(14f), end = u.dp(18f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            val dot = if (app != null && s.hasDot(app)) pal.accent else pal.btnFg
            Canvas(Modifier.size(u.dp(14f))) {
                val k = u.k
                if (app == null) {
                    drawLine(pal.btnFg, Offset(2f * k, 7f * k), Offset(12f * k, 7f * k), strokeWidth = 1.5f * k)
                    drawLine(pal.btnFg, Offset(7f * k, 2f * k), Offset(7f * k, 12f * k), strokeWidth = 1.5f * k)
                } else {
                    drawCircle(dot, 3.5f * k, Offset(7f * k, 7f * k))
                }
            }
            Txt(
                app?.display ?: "quick app", 13f, pal.btnFg,
                Modifier.onGloballyPositioned {
                    val p = it.positionInRoot()
                    label = Offset(p.x / u.k, (p.y + it.size.height / 2f) / u.k)
                },
                maxLines = 1,
            )
        }
    }
}

/** The web search button takes the place of the quick button. A tap opens search for the web. */
@Composable
private fun WebButton(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val homeIdle = s.ob == null && !s.search && s.hear == null && s.open == null
    val shown = homeIdle && s.scrub == null
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "webAlpha")
    val dy by animateFloatAsState(if (shown) 0f else 8f, tween(300), label = "webY")
    SideBox(!s.left, u.searchY, 24f, Modifier.graphicsLayer { this.alpha = alpha; translationY = dy * u.k }) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.height(u.dp(44f)).clip(shape).background(pal.btnBg).border(u.dp(1f), pal.rim2, shape)
                .tap(enabled = homeIdle) { s.openWeb() }
                .padding(start = u.dp(14f), end = u.dp(18f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            SearchIcon(pal.btnFg)
            Txt("search", 13f, pal.btnFg)
        }
    }
}

/** A magnifying glass. */
@Composable
fun SearchIcon(color: Color) {
    val u = LocalUnits.current
    Canvas(Modifier.size(u.dp(14f))) {
        val k = u.k
        drawCircle(color, 4.25f * k, Offset(5f * k, 5f * k), style = Stroke(1.5f * k))
        drawLine(color, Offset(8.5f * k, 8.5f * k), Offset(12.74f * k, 12.74f * k), strokeWidth = 1.5f * k)
    }
}

@Composable
private fun ScrubLetter(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val letters = s.scrub
    var letter by remember { mutableStateOf("") }
    if (letters != null) letter = letters[mod(s.index, letters.size)]
    val on = letters != null
    val alpha by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "scrubAlpha")
    val t by animateFloatAsState(if (on) 1f else 0f, tween(250, easing = Ease), label = "scrubMove")
    SideBox(s.left, s.cy - 66f, 36f) {
        Box(
            Modifier.height(u.dp(132f)).graphicsLayer {
                this.alpha = alpha
                translationY = (1 - t) * 6f * u.k
                scaleX = .96f + .04f * t
                scaleY = .96f + .04f * t
            },
            contentAlignment = Alignment.Center,
        ) {
            Txt(letter, 112f, pal.accent, weight = FontWeight.Light, spacing = -.04f, line = 1f)
        }
    }
}

@Composable
private fun PressRing(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val p = s.press ?: return
    key(p.id) {
        val scale = remember { Animatable(.35f) }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { scale.animateTo(1f, tween(500, easing = LinearEasing)) }
            alpha.animateTo(.9f, tween(300))
        }
        Box(
            Modifier.place(u, p.x - 36f, p.y - 36f).size(u.dp(72f))
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                .border(u.dp(1.5f), pal.accent, CircleShape),
        )
    }
}

@Composable
private fun ToastView(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val d = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(d) / u.k
    val y = min(u.searchY - 56f, u.hd - ime - 60f)
    var last by remember { mutableStateOf(s.toast) }
    s.toast?.let { last = it }
    val t = last ?: return
    val move = (10 * u.k).toInt()
    Box(Modifier.fillMaxWidth().place(u, 0f, y), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            s.toastShown,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300, easing = Ease)) { move },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300, easing = Ease)) { move },
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.height(u.dp(44f)).shadow(u.dp(10f), shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape).background(pal.card).border(u.dp(1f), pal.rim2, shape)
                    .padding(start = u.dp(18f), end = u.dp(4f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u.dp(4f)),
            ) {
                Txt(t.text, 12f, pal.fg, maxLines = 1)
                if (t.undo != null) {
                    Box(Modifier.height(u.dp(44f)).tap { s.undo() }.padding(horizontal = u.dp(14f)), contentAlignment = Alignment.Center) {
                        Txt("undo", 12f, pal.accent, weight = FontWeight.Medium)
                    }
                } else {
                    Spacer(Modifier.width(u.dp(14f)))
                }
            }
        }
    }
}

@Composable
private fun QuickMenuView(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    var last by remember { mutableStateOf(s.menu) }
    s.menu?.let { last = it }
    if (s.menu == null) return
    val m = last ?: return
    val veil by animateFloatAsState(if (s.menuShown) 1f else 0f, tween(200), label = "menuVeil")
    val op by animateFloatAsState(if (s.menuShown) 1f else 0f, tween(180), label = "menuAlpha")
    val t by animateFloatAsState(if (s.menuShown) 1f else 0f, tween(220, easing = Ease), label = "menuMove")
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil }.background(pal.veil).tap { s.closeMenu() })
    // Below the selected app. Near the bottom, the menu moves up so that it stays on the screen.
    SideBox(s.left, min(s.anchorY + 30f, u.hd - u.bottom - 330f), 24f) {
        val shape = RoundedCornerShape(u.dp(16f))
        Column(
            Modifier.width(u.dp(220f))
                .graphicsLayer {
                    alpha = op
                    scaleX = .95f + .05f * t
                    scaleY = .95f + .05f * t
                    translationY = -6f * (1 - t) * u.k
                    transformOrigin = TransformOrigin(if (s.left) .12f else .88f, 0f)
                }
                .shadow(u.dp(16f), shape).clip(shape).background(pal.card).border(u.dp(1f), pal.rim2, shape)
                .tap { }
                .padding(u.dp(6f)),
        ) {
            Txt(m.app.display, 11f, pal.muted, Modifier.padding(start = u.dp(12f), top = u.dp(10f), end = u.dp(12f), bottom = u.dp(8f)), spacing = .06f)
            m.shortcuts.forEach { info ->
                val label = (info.shortLabel ?: info.longLabel)?.toString()?.lowercase().orEmpty()
                MenuItem(label, pal.fg) { s.menuShortcut(info) }
            }
            if (m.shortcuts.isNotEmpty()) Box(Modifier.fillMaxWidth().height(u.dp(1f)).background(pal.rim))
            MenuItem("app info", pal.muted2) { s.menuInfo() }
            MenuItem("remove from dial", pal.muted2) { s.menuRemove() }
        }
    }
}

@Composable
private fun MenuItem(label: String, color: Color, onClick: () -> Unit) {
    val u = LocalUnits.current
    Box(
        Modifier.fillMaxWidth().height(u.dp(44f)).clip(RoundedCornerShape(u.dp(10f))).tap(onClick = onClick)
            .padding(horizontal = u.dp(12f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Txt(label, 13f, color, maxLines = 1)
    }
}

// Opening an app: a sheet fades in and the name lifts off the dial into the title.

@Composable
private fun AppSheet(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val o = s.open ?: return
    // The grid layouts open apps with their dot ripple.
    if (o.ripple) return
    val shown = o.phase == Phase.In
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(280), label = "sheetAlpha")
    val scale by animateFloatAsState(if (shown) 1f else .985f, tween(400, easing = Ease), label = "sheetScale")
    Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .background(pal.sheet).tap { s.close() },
    ) {
        if (o.sub.isNotEmpty()) {
            Txt(o.sub, 12f, pal.muted2, Modifier.place(u, 28f, max(150f, u.top + 110f)), spacing = .04f)
        }
    }
}

@Composable
private fun Title(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val o = s.open ?: return
    if (o.ripple) return
    val measurer = rememberTextMeasurer()
    val style = remember(u) { textStyle(u, 44f, pal.accent, FontWeight.Light, spacing = -.04f) }
    val prog = remember(o.id) { Animatable(0f) }
    val alpha = remember(o.id) { Animatable(1f) }
    LaunchedEffect(o.id, o.phase) {
        when (o.phase) {
            Phase.From -> prog.snapTo(0f)
            Phase.In -> prog.animateTo(1f, tween(440, easing = Ease))
            Phase.Out -> {
                if (!o.fromDial) launch { alpha.animateTo(0f, tween(250)) }
                prog.animateTo(0f, tween(440, easing = Ease))
            }
        }
    }
    // A long name shrinks so it fits between the margins.
    val fit = min(1f, 356f / (o.label.length * 24.64f))
    Canvas(Modifier.fillMaxSize()) {
        val t = prog.value
        val f = o.from
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        val x = lerp(f.x, 28f)
        val y = lerp(f.y, u.titleY)
        val rot = lerp(f.rot, 0f)
        val sc = lerp(f.s, fit)
        val tx = lerp(f.tx, 0f)
        val text = measurer.measure(o.label, style)
        withTransform({
            translate(x * u.k, y * u.k)
            rotate(Math.toDegrees(rot.toDouble()).toFloat(), Offset.Zero)
            scale(sc, sc, Offset.Zero)
        }) {
            drawText(text, pal.accent, Offset(tx * text.size.width, -text.size.height / 2f), alpha = alpha.value)
        }
    }
}
