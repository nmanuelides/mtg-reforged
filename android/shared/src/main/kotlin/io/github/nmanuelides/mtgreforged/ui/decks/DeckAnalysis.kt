package io.github.nmanuelides.mtgreforged.ui.decks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import forge.card.MagicColor
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.ManaSymbol
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---- The numbers -------------------------------------------------------------------

/** What a card does for the deck, read from its rules text. */
internal enum class Role(val label: String, val color: () -> Color, val target: Int, val hint: String) {
    Ramp("Ramp", { Palette.Sage }, 10, "Mana rocks, dorks and land search"),
    Draw("Card draw", { Palette.Slate }, 10, "Cards that draw or give card advantage"),
    Removal("Removal", { Palette.Terracotta }, 8, "Answers to one threat"),
    Wipe("Board wipes", { Palette.Plum }, 3, "Answers to many threats at once"),
    Counter("Counterspells", { Palette.Slate }, 0, "Stops spells on the stack"),
    Protection("Protection", { Palette.Ochre }, 3, "Hexproof, indestructible, phasing"),
    Tutor("Tutors", { Palette.Ochre }, 0, "Finds a specific card"),
}

private val DRAW = Regex("""\bdraws? (a|an|one|two|three|four|five|x|that many|cards?|\d+)\b""")
private val REMOVAL = Regex(
    """(destroy|exile) (up to \w+ )?(another )?target|deals? (\d+|x) damage to (any target|target (creature|planeswalker|permanent))|""" +
        """return (up to \w+ )?target (nonland )?(creature|permanent|nonland permanent)[^.]* to (its|their) owner'?s? hand|\bfights?\b|""" +
        """target (creature|player) (sacrifices|gets -)""",
)
private val WIPE = Regex("""(destroy|exile) (all|each)|all (other )?creatures get -|deals? (\d+|x) damage to each creature|each player sacrifices""")
private val RAMP = Regex("""\badd \{|add (one|two|three) mana|search your library for (up to \w+ )?(a |an )?(basic )?(land|forest|plains|island|swamp|mountain)""")
private val PROTECT = Regex("""\b(hexproof|indestructible|shroud|phase out|phases out|protection from)\b""")

internal fun rolesOf(card: PaperCard): Set<Role> {
    val r = card.rules
    if (r.type.isLand) return emptySet()
    val text = r.oracleText.orEmpty().lowercase()
    val roles = HashSet<Role>()
    if (RAMP.containsMatchIn(text)) roles += Role.Ramp
    if (DRAW.containsMatchIn(text)) roles += Role.Draw
    if (WIPE.containsMatchIn(text)) roles += Role.Wipe
    else if (REMOVAL.containsMatchIn(text)) roles += Role.Removal
    if ("counter target" in text) roles += Role.Counter
    // A creature with hexproof protects itself; only cards that give protection count.
    if (PROTECT.containsMatchIn(text) && (!r.type.isCreature || "you control" in text) && ("target" in text || "you control" in text || r.type.isEquipment || r.type.isAura)) roles += Role.Protection
    if ("search your library for" in text && !Regex("""search your library for (up to \w+ )?(a |an |two )?(basic )?(land|forest|plains|island|swamp|mountain)""").containsMatchIn(text)) roles += Role.Tutor
    return roles
}

private val BASIC_TYPES = listOf("Plains" to MagicColor.WHITE, "Island" to MagicColor.BLUE, "Swamp" to MagicColor.BLACK, "Mountain" to MagicColor.RED, "Forest" to MagicColor.GREEN)
private val ADD = Regex("""\{([WUBRG])\}""")

/** The colours a land can make: basic land types, the symbols after "Add", or "any color". */
private fun landColors(card: PaperCard, identity: Byte): Byte {
    val r = card.rules
    var mask = 0
    BASIC_TYPES.forEach { (type, color) -> if (r.type.hasSubtype(type)) mask = mask or color.toInt() }
    val text = r.oracleText.orEmpty()
    val lower = text.lowercase()
    if ("any color" in lower || "any one color" in lower || "any type" in lower) mask = mask or identity.toInt()
    text.lines().filter { "Add" in it }.forEach { line ->
        ADD.findAll(line).forEach { m -> mask = mask or MagicColor.fromName(m.groupValues[1][0]).toInt() }
    }
    return mask.toByte()
}

internal class DeckStats(
    val commanders: List<PaperCard>,
    /** Main deck cards, one entry per copy. */
    val library: List<PaperCard>,
    val format: Engine.Format,
) {
    val size = library.size
    val lands = library.count { it.rules.type.isLand }
    private val spells = library.filterNot { it.rules.type.isLand }
    val avgCmc = if (spells.isEmpty()) 0.0 else spells.sumOf { it.rules.manaCost.cmc } / spells.size.toDouble()

    /** Karsten's numbers: 99-card Commander and 60-card decks. Cheap ramp and draw lower the need. */
    val recommendedLands: Int = run {
        val cheap = spells.count { it.rules.manaCost.cmc <= 2 && (Role.Ramp in rolesOf(it) || Role.Draw in rolesOf(it)) }
        if (format == Engine.Format.Commander) (31.42 + 3.13 * avgCmc - 0.28 * cheap).roundToInt().coerceIn(30, 42)
        else (14.6 + 1.6 * avgCmc - 0.2 * cheap).roundToInt().coerceIn(14, 27)
    }

    /** Spells per mana value (0..7+), split into creatures and the rest. */
    val curve: List<Pair<Int, Int>> = (0..7).map { v ->
        val at = spells.filter { if (v == 7) it.rules.manaCost.cmc >= 7 else it.rules.manaCost.cmc == v }
        at.count { it.rules.type.isCreature } to at.count { !it.rules.type.isCreature }
    }

    val types: List<Triple<String, Int, Color>> = listOf(
        Triple("Creatures", library.count { it.rules.type.isCreature }, Palette.Sage),
        Triple("Instants", library.count { it.rules.type.isInstant }, Palette.Slate),
        Triple("Sorceries", library.count { it.rules.type.isSorcery }, Palette.Terracotta),
        Triple("Artifacts", library.count { it.rules.type.isArtifact && !it.rules.type.isCreature && !it.rules.type.isLand }, Color(0xFF9A8F80)),
        Triple("Enchantments", library.count { it.rules.type.isEnchantment && !it.rules.type.isCreature }, Palette.Ochre),
        Triple("Planeswalkers", library.count { it.rules.type.isPlaneswalker }, Palette.Plum),
        Triple("Lands", lands, Color(0xFF8C6A4F)),
    ).filter { it.second > 0 }

    private val identity: Byte = run {
        var m = 0
        (commanders + library).forEach { m = m or it.rules.colorIdentity.color.toInt() }
        m.toByte()
    }

    /** Per colour: coloured symbols in the spells' costs, and lands that make it. */
    val colors: List<Triple<ManaPip, Int, Int>> = ManaPip.WUBRG.map { pip ->
        val c = pip.toColorByte()
        val pips = (commanders + spells).sumOf { card -> card.rules.manaCost.count { s -> !s.isGeneric && (s.colorMask.toInt() and c.toInt()) != 0 } }
        val sources = library.count { it.rules.type.isLand && (landColors(it, identity).toInt() and c.toInt()) != 0 }
        Triple(pip, pips, sources)
    }.filter { it.second > 0 || it.third > 0 }

    val roles: Map<Role, List<PaperCard>> = Role.entries.associateWith { role -> library.filter { role in rolesOf(it) }.distinct() }
    fun roleCount(role: Role) = library.count { role in rolesOf(it) }

    /** Chance of exactly k lands in the opening 7. */
    val openingLands: List<Double> = (0..7).map { k -> hyper(size, lands, 7, k) }

    /** Chance of having hit every land drop up to turn t (on the play), t = 1..8. */
    val landDrops: List<Double> = (1..8).map { t -> atLeast(size, lands, 7 + t - 1, t) }

    fun drawnBy(copies: Int, turn: Int, onPlay: Boolean = true): Double = atLeast(size, copies, 7 + turn - (if (onPlay) 1 else 0), 1)
}

private fun ManaPip.toColorByte(): Byte = when (this) {
    ManaPip.White -> MagicColor.WHITE
    ManaPip.Blue -> MagicColor.BLUE
    ManaPip.Black -> MagicColor.BLACK
    ManaPip.Red -> MagicColor.RED
    ManaPip.Green -> MagicColor.GREEN
    ManaPip.Colorless -> MagicColor.COLORLESS
}

/** ln C(n, k), so big decks don't overflow. */
private fun lnChoose(n: Int, k: Int): Double {
    if (k < 0 || k > n) return Double.NEGATIVE_INFINITY
    var s = 0.0
    for (i in 1..k) s += kotlin.math.ln((n - k + i).toDouble() / i)
    return s
}

/** Hypergeometric: exactly [k] hits drawing [draws] from [n] cards of which [hits] are hits. */
private fun hyper(n: Int, hits: Int, draws: Int, k: Int): Double {
    if (n <= 0 || draws > n) return 0.0
    val v = lnChoose(hits, k) + lnChoose(n - hits, draws - k) - lnChoose(n, draws)
    return if (v == Double.NEGATIVE_INFINITY) 0.0 else kotlin.math.exp(v)
}

private fun atLeast(n: Int, hits: Int, draws: Int, k: Int): Double {
    val d = draws.coerceAtMost(n)
    return (k..d).sumOf { hyper(n, hits, d, it) }.coerceIn(0.0, 1.0)
}

private fun pct(p: Double) = "${(p * 100).roundToInt()}%"

// ---- The dialog --------------------------------------------------------------------

private enum class Tab(val label: String) { Overview("Overview"), Roles("Roles"), Odds("Odds"), Hand("Sample hand") }

/** A big window with charts that sum the deck up at a glance; the sample hand tab plays out opening hands. */
@Composable
internal fun DeckAnalysisDialog(draft: DeckDraft, format: Engine.Format, onDismiss: () -> Unit) {
    val stats = remember(draft.commander.toMap(), draft.main.toMap()) {
        DeckStats(
            draft.commander.flatMap { (c, n) -> List(n) { c } },
            draft.main.flatMap { (c, n) -> List(n) { c } },
            format,
        )
    }
    var tab by remember { mutableStateOf(Tab.Overview) }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 1180.dp) {
        SectionLabel("Deck analysis")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Tab.entries.forEach { t ->
                OrnateButton(t.label, { tab = t }, style = if (t == tab) OrnateStyle.Primary else OrnateStyle.Secondary, height = 42.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            if (stats.size == 0) {
                Text("Add some cards to the deck first.", style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
            } else when (tab) {
                Tab.Overview -> Overview(stats)
                Tab.Roles -> Roles(stats)
                Tab.Odds -> Odds(stats)
                Tab.Hand -> SampleHand(stats)
            }
        }
        Spacer(Modifier.height(12.dp))
        OrnateButton("Close", onDismiss, Modifier.fillMaxWidth(), accent = Palette.Slate)
    }
}

/** Two or three columns on wide windows, one on phones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panels(vararg panels: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = when {
            maxWidth > 980.dp -> 3
            maxWidth > 620.dp -> 2
            else -> 1
        }
        val w = (maxWidth - 14.dp * (cols - 1)) / cols
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            panels.forEachIndexed { i, p -> Box(Modifier.width(w).appear(i)) { p() } }
        }
    }
}

@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 2.dp, corners = false).padding(14.dp),
    ) {
        Text(title, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Ink)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft) }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Panels rise into place one after another. */
private fun Modifier.appear(order: Int): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(order * 70L); p.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 40f }
}

/** 0 → 1 once, after [delayMs]: drives bars growing and arcs sweeping in. */
@Composable
private fun grow(delayMs: Long = 0): Float {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs); p.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    return p.value
}

@Composable
private fun BigNumber(value: String, label: String, color: Color = Palette.Terracotta, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft, textAlign = TextAlign.Center)
    }
}

// ---- Overview ------------------------------------------------------------------------

@Composable
private fun Overview(s: DeckStats) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().appear(0), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigNumber("${s.size + s.commanders.size}", "cards")
            BigNumber("%.2f".format(s.avgCmc), "average mana value", Palette.Slate)
            BigNumber("${s.lands}", "lands", Color(0xFF8C6A4F))
            BigNumber("${s.recommendedLands}", "lands suggested", Palette.Sage)
        }
        Panels(
            { CurvePanel(s) },
            { TypesPanel(s) },
            { ColorsPanel(s) },
        )
    }
}

@Composable
private fun CurvePanel(s: DeckStats) {
    Panel("Mana curve", "Spells by mana value · creatures and other spells") {
        val p = grow(150)
        val max = (s.curve.maxOfOrNull { it.first + it.second } ?: 1).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            s.curve.forEachIndexed { v, (creatures, others) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val total = creatures + others
                    if (total > 0) Text("$total", style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
                    // Each bar starts a little later than the one before.
                    val bar = ((p * 8f - v * 0.6f) / 3f).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxWidth().height((130 * total / max.toFloat()).dp)) {
                        val h = size.height * bar
                        val ch = if (total == 0) 0f else h * creatures / total
                        val r = CornerRadius(6f, 6f)
                        drawRoundRect(Palette.Slate, Offset(0f, size.height - h), Size(size.width, h - ch), r)
                        drawRoundRect(Palette.Sage, Offset(0f, size.height - ch), Size(size.width, ch), r)
                    }
                    Text(if (v == 7) "7+" else "$v", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Palette.Sage, "Creatures")
            Legend(Palette.Slate, "Other spells")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
    }
}

@Composable
private fun TypesPanel(s: DeckStats) {
    Panel("Card types") {
        val p = grow(250)
        val total = s.types.sumOf { it.second }.coerceAtLeast(1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(150.dp)) {
                    val stroke = 26.dp.toPx()
                    var start = -90f
                    s.types.forEach { (_, n, color) ->
                        val sweep = 360f * n / total * p
                        drawArc(color, start, (sweep - 1.5f).coerceAtLeast(0f), false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
                        start += sweep
                    }
                }
                Text("${s.size}", fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Palette.Ink)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.types.forEach { (label, n, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(color, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("$label  ", style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                        Text("$n", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorsPanel(s: DeckStats) {
    Panel("Colors", "Share of colored symbols in costs vs share of lands that make each color") {
        val p = grow(350)
        val pips = s.colors.sumOf { it.second }.coerceAtLeast(1)
        val sources = s.colors.sumOf { it.third }.coerceAtLeast(1)
        if (s.colors.isEmpty()) Text("Colorless deck.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.colors.forEach { (pip, n, src) ->
                val need = n / pips.toFloat()
                val have = src / sources.toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManaSymbol(pip, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Meter(need * p, pip.disc.darker(), "$n symbols · ${pct(need.toDouble())}")
                        Meter(have * p, Color(0xFF8C6A4F), "$src lands · ${pct(have.toDouble())}")
                    }
                }
                // Short on sources: a third less than the share of symbols asks for.
                if (n > 0 && have < need * 0.67f) {
                    Text("  Few ${pip.label.lowercase()} sources for how much the spells ask for.", style = MaterialTheme.typography.bodySmall, color = Palette.Terracotta)
                }
            }
        }
    }
}

private fun Color.darker() = Color(red * 0.72f, green * 0.72f, blue * 0.72f, alpha)

@Composable
private fun Meter(fraction: Float, color: Color, label: String) {
    Box(Modifier.fillMaxWidth().height(18.dp).background(Palette.Paper, RoundedCornerShape(9.dp)).border(1.dp, Palette.Line, RoundedCornerShape(9.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(9.dp)))
        Text(label, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), fontSize = 11.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Roles ---------------------------------------------------------------------------

@Composable
private fun Roles(s: DeckStats) {
    val commander = s.format == Engine.Format.Commander
    var open by remember { mutableStateOf<Role?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (commander) "What the deck's cards do, next to the usual Commander guideline. Read from the cards' rules text, so it's an estimate."
            else "What the deck's cards do. Read from the cards' rules text, so it's an estimate.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Role.entries.forEachIndexed { i, role ->
            val n = s.roleCount(role)
            val target = if (commander) role.target else 0
            val p = grow(100L + i * 90)
            val scale = maxOf(n, target, 1) * 1.15f
            Column(Modifier.fillMaxWidth().appear(i).clickable { open = if (open == role) null else role }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(150.dp)) {
                        Text(role.label, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
                        Text(role.hint, style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                    }
                    Box(Modifier.weight(1f).height(26.dp)) {
                        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                            drawRoundRect(Palette.Paper, size = size, cornerRadius = CornerRadius(13f, 13f))
                            val w = size.width * (n / scale) * p
                            drawRoundRect(role.color(), size = Size(w, size.height), cornerRadius = CornerRadius(13f, 13f))
                            if (target > 0) {
                                val x = size.width * target / scale
                                drawLine(Palette.Ink, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 3f, cap = StrokeCap.Round)
                            }
                        }
                        Text("$n", Modifier.align(Alignment.CenterStart).padding(start = 10.dp), fontWeight = FontWeight.Bold, color = Palette.OnAccent, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            target == 0 -> ""
                            n >= target -> "✓ $target+"
                            else -> "${target - n} short"
                        },
                        Modifier.width(78.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (target == 0 || n >= target) Palette.Sage else Palette.Terracotta,
                    )
                }
                AnimatedVisibility(open == role) {
                    val cards = s.roles[role].orEmpty()
                    Text(
                        if (cards.isEmpty()) "None found." else cards.joinToString(" · ") { it.name },
                        Modifier.padding(start = 150.dp, top = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Ink,
                    )
                }
            }
        }
        Text("Tap a role to see its cards. The dark line marks the guideline.", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
    }
}

// ---- Odds ----------------------------------------------------------------------------

@Composable
private fun Odds(s: DeckStats) {
    val keep = (2..4).sumOf { s.openingLands[it] }
    val commanderCmc = s.commanders.firstOrNull()?.rules?.manaCost?.cmc
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().appear(0), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigNumber(pct(keep), "opening hands with 2–4 lands", Palette.Sage)
            if (commanderCmc != null && commanderCmc in 1..8) {
                BigNumber(pct(s.landDrops[commanderCmc - 1]), "commander on curve (turn $commanderCmc)", Palette.Plum)
            }
            BigNumber(pct(s.landDrops[3]), "4th land drop on turn 4", Palette.Slate)
        }
        Panels(
            { OpeningLandsPanel(s) },
            { LandDropsPanel(s) },
            { FindCardPanel(s) },
        )
    }
}

@Composable
private fun OpeningLandsPanel(s: DeckStats) {
    Panel("Lands in the opening hand", "Chance of each number of lands in 7 cards") {
        val p = grow(150)
        val max = (s.openingLands.maxOrNull() ?: 1.0).coerceAtLeast(0.01)
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            s.openingLands.forEachIndexed { k, chance ->
                val good = k in 2..4
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(pct(chance), fontSize = 10.sp, color = Palette.Ink)
                    Box(
                        Modifier.fillMaxWidth().height((120 * chance / max * p).dp.coerceAtLeast(2.dp))
                            .background(if (good) Palette.Sage else Palette.Terracotta.copy(alpha = 0.7f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                    )
                    Text("$k", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun LandDropsPanel(s: DeckStats) {
    Panel("Hitting land drops", "Chance of having a land for every turn so far, on the play") {
        val p = grow(250)
        val line = Palette.Slate
        val grid = Palette.Line
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val n = s.landDrops.size
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { y ->
                val yy = size.height * (1 - y)
                drawLine(grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            val pts = s.landDrops.mapIndexed { i, v -> Offset(size.width * i / (n - 1), size.height * (1 - v.toFloat())) }
            val path = Path()
            val shown = (p * (n - 1)).coerceAtLeast(0f)
            pts.forEachIndexed { i, o ->
                if (i > shown + 1) return@forEachIndexed
                val pt = if (i > shown) {
                    val prev = pts[i - 1]; val f = shown - (i - 1)
                    Offset(prev.x + (o.x - prev.x) * f, prev.y + (o.y - prev.y) * f)
                } else o
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                if (i <= shown) drawCircle(line, 5f, pt)
            }
            drawPath(path, line, style = Stroke(4f, cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth()) {
            s.landDrops.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("T${i + 1}", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                    Text(pct(v), fontSize = 10.sp, color = Palette.Ink)
                }
            }
        }
    }
}

@Composable
private fun FindCardPanel(s: DeckStats) {
    val choices = remember(s) { s.library.groupingBy { it.name }.eachCount().toList().sortedBy { it.first } }
    var picked by remember(s) { mutableStateOf(choices.firstOrNull { (n, _) -> s.library.first { it.name == n }.rules.type.isLand.not() } ?: choices.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    Panel("Finding a card", "Chance of having drawn it by each turn, on the play") {
        Box {
            OrnateButton((picked?.first ?: "Pick a card") + " ▾", { menu = true }, Modifier.fillMaxWidth(), height = 40.dp, textStyle = MaterialTheme.typography.labelMedium)
            DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp)) {
                choices.forEach { c ->
                    DropdownMenuItem({ Text(c.first + if (c.second > 1) " ×${c.second}" else "") }, { picked = c; menu = false })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val pick = picked ?: return@Panel
        val p = grow(100)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(1, 2, 3, 4, 5, 6, 8, 10).forEach { t ->
                val chance = s.drawnBy(pick.second, t)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn $t", Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                    Box(Modifier.weight(1f)) { Meter((chance * p).toFloat(), Palette.Ochre, pct(chance)) }
                }
            }
        }
    }
}

// ---- Sample hand ---------------------------------------------------------------------

@Composable
private fun SampleHand(s: DeckStats) {
    // A fresh shuffle per deal; the hand is drawn from its top.
    var shuffle by remember(s) { mutableStateOf(s.library.shuffled()) }
    var drawn by remember(s) { mutableIntStateOf(7) }
    var mulligans by remember(s) { mutableIntStateOf(0) }
    val bottomed = remember(s) { mutableStateListOf<Int>() }
    var deal by remember { mutableIntStateOf(0) }
    val toBottom = mulligans - bottomed.size
    // Commander's first mulligan is free.
    val free = if (s.format == Engine.Format.Commander && mulligans == 1) 1 else 0

    fun newHand(mull: Int) {
        shuffle = s.library.shuffled(); drawn = 7; mulligans = mull; bottomed.clear(); deal++
    }

    val hand = shuffle.take(drawn.coerceAtMost(shuffle.size)).withIndex().filter { it.index !in bottomed }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                toBottom - free > 0 -> "Mulligan $mulligans: tap ${toBottom - free} card${if (toBottom - free > 1) "s" else ""} to put on the bottom."
                drawn > 7 -> "Turn ${drawn - 7 + 1} (on the draw: turn ${drawn - 7})."
                else -> "Opening hand · ${hand.count { it.value.rules.type.isLand }} lands"
            },
            style = MaterialTheme.typography.titleSmall, color = if (toBottom - free > 0) Palette.Terracotta else Palette.Ink,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = 7
            val w = ((maxWidth - 8.dp * (perRow - 1)) / perRow).coerceAtMost(150.dp)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hand.forEach { (i, card) ->
                    androidx.compose.runtime.key(deal, i) { DealtCard(card, w, i % 7, enabled = toBottom - free > 0) { bottomed += i } }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrnateButton("New hand", { newHand(0) }, icon = Icons.Default.Autorenew, style = OrnateStyle.Primary)
            OrnateButton("Mulligan", { newHand(mulligans + 1) }, icon = Icons.Default.Replay, accent = Palette.Plum, enabled = mulligans < 6)
            OrnateButton("Draw", { drawn++ }, icon = Icons.Default.Layers, accent = Palette.Slate, enabled = toBottom - free <= 0 && drawn < shuffle.size)
        }
    }
}

/** A card dealt onto the table: flies in from the deck and settles with a small bounce. */
@Composable
private fun DealtCard(card: PaperCard, width: Dp, order: Int, enabled: Boolean, onBottom: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(order * 90L); shown = true }
    val lift by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "deal")
    val ui = remember(card) { CardUi.fromPaper(card) }
    Box(
        Modifier.graphicsLayer {
            alpha = lift.coerceIn(0f, 1f)
            translationY = (1f - lift) * -260f
            rotationZ = (1f - lift) * (order - 3) * 6f
        },
    ) {
        CardTile(ui, width, showTapped = false, onClick = if (enabled) onBottom else null)
    }
}
