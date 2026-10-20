package io.github.nmanuelides.mtgreforged.engine

import forge.StaticData
import forge.deck.Deck
import forge.item.PaperCard
import forge.util.ScryfallRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicInteger
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.isUnmeteredNetwork
import io.github.nmanuelides.mtgreforged.platform.Log

/**
 * Downloads card images ahead of time so the app works offline, like Forge's "download all
 * pictures". Images already on disk are skipped, so stopping and starting again resumes.
 * Runs inside [ImageDownloadService] so it survives leaving the app.
 */
object ImageDownloader {
    private const val TAG = "ImageDownloader"
    // Scryfall allows ~10 requests/s (2/s for name lookups); two workers stay well inside that.
    private const val WORKERS = 2

    /** What to download. Sizes assume ~90 KB per Scryfall "normal" image. */
    enum class Scope(val label: String, val detail: String) {
        Decks("Cards in all decks", "Your decks and every precon: what you'll see in matches."),
        All("Every card", "One printing of every card, for browsing the whole pool offline."),
    }

    data class Progress(
        val running: Boolean = false,
        val scope: Scope? = null,
        val done: Int = 0,
        val total: Int = 0,
        val downloaded: Int = 0,
        val failed: Int = 0,
        val message: String? = null,
    ) {
        val fraction get() = if (total == 0) 0f else done.toFloat() / total
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun estimateMb(images: Int) = images * 90 / 1024

    /** Image keys for [scope]: front faces, plus back faces of double-faced cards. */
    fun keys(scope: Scope): List<String> {
        val cards: Collection<PaperCard> = when (scope) {
            Scope.All -> { Engine.awaitAllCards(); StaticData.instance().commonCards.uniqueCards.filterNot { it.name.startsWith("A-") } }
            Scope.Decks -> Engine.Format.entries
                .flatMap { Engine.decks(it) }
                .flatMap { entry -> runCatching { Engine.loadDeck(entry).allCards() }.getOrDefault(emptyList()) }
                .distinctBy { it.name to it.edition }
        }
        return cards.flatMap { card ->
            listOfNotNull(card.getImageKey(false), card.getImageKey(true).takeIf { card.hasBackFace() })
        }.distinct()
    }

    /**
     * One image, waiting out Scryfall's cool-down (after a 429 Forge skips every request for
     * a while) instead of counting those skips as failures; retried once after the wait.
     */
    private fun waitForCooldown() {
        ScryfallRateLimiter.awaitCooldownCleared({ cancelled }) { waiting -> _progress.update { it.copy(message = waiting) } }
        _progress.update { if (it.running) it.copy(message = null) else it }
    }

    private suspend fun fetchPolitely(key: String): CardImages.DiskResult {
        repeat(2) {
            if (ScryfallRateLimiter.isCoolingDown()) waitForCooldown()
            if (cancelled) return CardImages.DiskResult.AlreadyThere
            val result = runCatching { CardImages.ensureOnDisk(key) }.getOrDefault(CardImages.DiskResult.Failed)
            if (result != CardImages.DiskResult.Failed || !ScryfallRateLimiter.isCoolingDown()) return result
        }
        return CardImages.DiskResult.Failed
    }

    private fun Deck.allCards(): List<PaperCard> = flatMap { section -> section.value.toFlatList() }

    private fun onWifi(context: AppContext) =
        context.isUnmeteredNetwork()

    /** Runs the whole download; returns when done, cancelled, or off Wi-Fi. */
    suspend fun run(context: AppContext, scope: Scope, coroutines: CoroutineScope) {
        cancelled = false
        _progress.value = Progress(running = true, scope = scope, message = "Listing cards…")
        val keys = runCatching { keys(scope) }.getOrElse {
            _progress.value = Progress(scope = scope, message = "Couldn't list cards: ${it.message}")
            return
        }
        _progress.value = Progress(running = true, scope = scope, total = keys.size)
        val next = AtomicInteger(0)
        val downloaded = AtomicInteger(0)
        val failed = AtomicInteger(0)
        var stopReason: String? = null

        (1..WORKERS).map {
            coroutines.async {
                while (coroutines.isActive && !cancelled) {
                    val i = next.getAndIncrement()
                    if (i >= keys.size) break
                    if (i % 50 == 0 && !onWifi(context)) {
                        stopReason = "Paused: not on Wi-Fi"
                        cancelled = true
                        break
                    }
                    when (fetchPolitely(keys[i])) {
                        CardImages.DiskResult.Downloaded -> downloaded.incrementAndGet()
                        CardImages.DiskResult.Failed -> failed.incrementAndGet()
                        CardImages.DiskResult.AlreadyThere -> Unit
                    }
                    _progress.update {
                        it.copy(done = it.done + 1, downloaded = downloaded.get(), failed = failed.get())
                    }
                }
            }
        }.awaitAll()

        val finished = !cancelled
        _progress.update {
            it.copy(
                running = false,
                message = stopReason ?: if (finished) "Done" else "Stopped",
            )
        }
        Log.i(TAG, "scope=$scope total=${keys.size} downloaded=${downloaded.get()} failed=${failed.get()} finished=$finished")
    }
}
