package io.github.nmanuelides.mtgreforged.engine

import forge.gamemodes.match.HostedMatch
import forge.gui.download.GuiDownloadService
import forge.gui.interfaces.IGuiBase
import forge.gui.interfaces.IGuiGame
import forge.item.PaperCard
import forge.localinstance.skin.FSkinProp
import forge.localinstance.skin.ISkinImage
import forge.sound.IAudioClip
import forge.sound.IAudioMusic
import forge.sound.SoundSystem
import forge.util.FSerializableFunction
import forge.util.ImageFetcher
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.match.ComposeGuiGame
import org.jupnp.UpnpServiceConfiguration
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.Log
import io.github.nmanuelides.mtgreforged.platform.MainThread
import io.github.nmanuelides.mtgreforged.platform.copyToClipboard
import io.github.nmanuelides.mtgreforged.platform.createAudioClip
import io.github.nmanuelides.mtgreforged.platform.openUrl
import io.github.nmanuelides.mtgreforged.platform.screenScale

/**
 * Forge's view of the platform. The UI thread plays the role of Forge's "EDT";
 * dialogs block the calling engine thread until the Compose UI answers them.
 */
class ReforgedGui(private val context: AppContext, private val assetsDir: String) : IGuiBase {

    private val imageFetcher = CardImageFetcher()

    /** The match currently shown; Forge asks for one per hosted match. */
    @Volatile var currentGame: ComposeGuiGame? = null
        private set

    override fun isRunningOnDesktop() = false
    override fun isLibgdxPort() = true // Forge uses this to pick the mobile code paths.
    override fun getCurrentVersion(): String = BuildConfig.VERSION_NAME
    override fun getAssetsDir() = assetsDir

    override fun invokeInEdtNow(runnable: Runnable) = runnable.run()
    override fun invokeInEdtLater(runnable: Runnable) {
        MainThread.post(runnable)
    }

    override fun invokeInEdtAndWait(proc: Runnable) {
        if (isGuiThread) return proc.run()
        val done = CountDownLatch(1)
        MainThread.post {
            try { proc.run() } finally { done.countDown() }
        }
        done.await()
    }

    override fun runBackgroundTask(message: String?, task: Runnable) {
        Thread(task, "Game BG").apply { isDaemon = true }.start()
    }

    override fun isGuiThread() = MainThread.isCurrent

    override fun getImageFetcher(): ImageFetcher = imageFetcher
    override fun getSkinIcon(skinProp: FSkinProp?): ISkinImage? = null
    override fun getUnskinnedIcon(path: String?): ISkinImage? = null
    override fun getCardArt(card: PaperCard?, backFace: Boolean): ISkinImage? = null
    override fun createLayeredImage(card: PaperCard?, background: FSkinProp?, overlayFilename: String?, opacity: Float): ISkinImage? = null
    override fun clearImageCache() = CardImages.clear()
    override fun encodeSymbols(str: String, formatReminderText: Boolean) = str

    override fun getAvatarCount() = 0
    override fun getSleevesCount() = 0
    override fun getScreenScale() = context.screenScale

    override fun preventSystemSleep(preventSleep: Boolean) {}
    override fun download(service: GuiDownloadService?, callback: Consumer<Boolean>?) {
        callback?.accept(false)
    }

    override fun copyToClipboard(text: String?) {
        context.copyToClipboard(text)
    }

    override fun browseToUrl(url: String) {
        context.openUrl(url)
    }

    override fun showCardList(title: String?, message: String?, list: List<PaperCard>) {
        PromptHost.ask(Prompt.Message(title.orEmpty(), message + "\n\n" + list.joinToString("\n") { it.name }), Unit)
    }

    override fun showBoxedProduct(title: String?, message: String?, list: List<PaperCard>): Boolean {
        showCardList(title, message, list)
        return false
    }

    override fun showBugReportDialog(title: String?, text: String?, showExitAppBtn: Boolean) {
        Log.e("Forge", "$title\n$text")
        PromptHost.ask(Prompt.Message(title ?: "Error", text.orEmpty(), isError = true), Unit)
    }

    override fun showImageDialog(image: ISkinImage?, message: String?, title: String?) {
        PromptHost.ask(Prompt.Message(title.orEmpty(), message.orEmpty()), Unit)
    }

    override fun showOptionDialog(message: String?, title: String?, icon: FSkinProp?, options: List<String>, defaultOption: Int): Int =
        PromptHost.ask(Prompt.Options(title.orEmpty(), message.orEmpty(), options, defaultOption), defaultOption)

    override fun showInputDialog(message: String?, title: String?, icon: FSkinProp?, initialInput: String?, inputOptions: List<String>?, isNumeric: Boolean): String? =
        PromptHost.ask(Prompt.Input(title.orEmpty(), message.orEmpty(), initialInput, inputOptions, isNumeric), null)

    override fun showFileDialog(title: String?, defaultDir: String?): String? = null
    override fun getSaveFile(defaultFile: File?): File? = null

    override fun <T> order(
        title: String?, top: String?, remainingObjectsMin: Int, remainingObjectsMax: Int,
        sourceChoices: List<T>, destChoices: List<T>?,
    ): List<T> {
        val all = sourceChoices + destChoices.orEmpty()
        val order = PromptHost.ask<List<Int>?>(Prompt.Order(title.orEmpty(), all.map { describe(it, null) }), null)
            ?: all.indices.toList()
        return order.map { all[it] }
    }

    override fun <T> getChoices(
        message: String?, min: Int, max: Int, choices: Collection<T>, selected: Collection<T>?,
        display: FSerializableFunction<T, String>?,
    ): List<T> {
        val list = choices.toList()
        if (list.isEmpty()) return emptyList()
        val items = list.map { describe(it, display?.let { f -> { t: T -> f.apply(t) } }) }
        val pre = selected.orEmpty().mapNotNull { s -> list.indexOf(s).takeIf { it >= 0 } }.toSet()
        val picked = PromptHost.ask<List<Int>?>(Prompt.Choose(message.orEmpty(), min, max, items, pre), null)
        return picked?.map { list[it] }.orEmpty()
    }

    override fun chooseCard(title: String?, message: String?, list: List<PaperCard>): PaperCard? =
        getChoices("$title - $message", 1, 1, list, null, null).firstOrNull()

    override fun isSupportedAudioFormat(file: File?) = file?.extension?.lowercase() in setOf("mp3", "wav", "ogg")
    override fun createAudioClip(filename: String?): IAudioClip? =
        filename?.let { SoundSystem.instance.getSoundResource(it) }?.let(::createAudioClip)
    override fun createAudioMusic(filename: String?): IAudioMusic? = null
    override fun startAltSoundSystem(filename: String?, isSynchronized: Boolean) {}

    override fun showSpellShop() {}
    override fun showBazaar() {}

    override fun getNewGuiGame(): IGuiGame = ComposeGuiGame().also { currentGame = it }

    override fun hostMatch(): HostedMatch = HostedMatch()

    override fun getUpnpPlatformService(): UpnpServiceConfiguration? = null
    override fun hasNetGame() = false
}
