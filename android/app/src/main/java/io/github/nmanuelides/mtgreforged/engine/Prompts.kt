package io.github.nmanuelides.mtgreforged.engine

import android.os.Looper
import android.util.Log
import forge.game.card.CardView
import io.github.nmanuelides.mtgreforged.match.CardUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/** One option in a choice/order/distribute prompt. [card] is set when the option is a card. */
data class PromptItem(val label: String, val card: CardUi? = null)

/**
 * Questions the engine asks the player. The engine thread blocks in [PromptHost.ask]
 * until the Compose UI calls [PendingPrompt.answer].
 */
sealed interface Prompt {
    val title: String

    /** Result: Unit. */
    data class Message(override val title: String, val message: String, val isError: Boolean = false) : Prompt

    /** Result: index of the chosen option. */
    data class Options(
        override val title: String,
        val message: String,
        val options: List<String>,
        val defaultOption: Int,
        val card: CardUi? = null,
    ) : Prompt

    /** Result: String, or null if cancelled. */
    data class Input(
        override val title: String,
        val message: String,
        val initial: String?,
        val suggestions: List<String>?,
        val numeric: Boolean,
    ) : Prompt

    /** Result: List<Int> of chosen indices, or null if cancelled. */
    data class Choose(
        override val title: String,
        val min: Int,
        val max: Int,
        val items: List<PromptItem>,
        val preselected: Set<Int> = emptySet(),
    ) : Prompt

    /** Result: List<Int>, a permutation of item indices. */
    data class Order(override val title: String, val items: List<PromptItem>) : Prompt

    /** Result: List<Int> amounts per target (same order), or null if skipped. */
    data class Distribute(
        override val title: String,
        val source: CardUi?,
        val total: Int,
        val targets: List<PromptItem>,
        val atLeastOne: Boolean,
        val initial: List<Int>,
        val maySkip: Boolean,
    ) : Prompt
}

class PendingPrompt(val id: Long, val prompt: Prompt, private val future: CompletableFuture<Any?>) {
    fun answer(result: Any?) {
        future.complete(result)
    }
}

object PromptHost {
    private val ids = AtomicLong()
    private val queue = ArrayDeque<PendingPrompt>()
    private val _current = MutableStateFlow<PendingPrompt?>(null)
    val current: StateFlow<PendingPrompt?> = _current.asStateFlow()

    /**
     * Shows [prompt] and blocks until it is answered. Must not be called on the main
     * thread; there the [fallback] is returned so the UI never deadlocks.
     */
    @Suppress("UNCHECKED_CAST")
    fun <R> ask(prompt: Prompt, fallback: R): R {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w("PromptHost", "Prompt requested on main thread, using fallback: ${prompt.title}")
            return fallback
        }
        val future = CompletableFuture<Any?>()
        val pending = PendingPrompt(ids.incrementAndGet(), prompt, future)
        synchronized(queue) {
            queue.addLast(pending)
            if (_current.value == null) _current.value = queue.first()
        }
        return try {
            (future.get() ?: fallback) as R
        } finally {
            synchronized(queue) {
                queue.remove(pending)
                _current.value = queue.firstOrNull()
            }
        }
    }

    /** Releases every waiting engine thread, e.g. when a match is abandoned. */
    fun cancelAll() {
        synchronized(queue) {
            queue.toList().forEach { it.answer(null) }
        }
    }
}

/** Convenience used by both the IGuiBase and IGuiGame implementations. */
internal fun <T> describe(item: T, display: ((T) -> String)?): PromptItem {
    val label = display?.invoke(item) ?: item.toString()
    val card = (item as? CardView)?.let(CardUi::from)
    return PromptItem(label, card)
}
