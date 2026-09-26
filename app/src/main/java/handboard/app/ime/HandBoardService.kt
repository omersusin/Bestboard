package handboard.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputContentInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import handboard.app.MainActivity
import handboard.app.clipboard.ClipboardHistory
import handboard.app.clipboard.ClipboardItem
import handboard.app.core.theme.*
import handboard.app.layout.LayoutSwitcher
import handboard.app.layout.ui.KeyboardView
import handboard.app.layout.ui.KeyboardWrapper
import handboard.app.prediction.*
import handboard.app.prediction.glide.GlideLib
import handboard.app.prediction.glide.GlideUiState
import handboard.app.settings.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HandBoardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    
    private lateinit var prefs: PreferencesManager
    private val predictor = WordPredictor()
    private var clipboard: ClipboardHistory? = null
    // ponytail: state so predictions + network panels recompose on field switch (was plain var).
    private var isPasswordField by mutableStateOf(false)
    private var isNumberField by mutableStateOf(false)
    private var isPrivateField by mutableStateOf(false)
    private var lastSpaceTime = 0L

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        prefs = PreferencesManager(this)
        // ponytail: bundled gesture decoder (no-op when ABI missing).
        GlideLib.loadBundled()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val t = info?.inputType ?: 0
        val v = t and InputType.TYPE_MASK_VARIATION
        val cls = t and InputType.TYPE_MASK_CLASS
        isPasswordField = v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || (cls == InputType.TYPE_CLASS_NUMBER && (t and InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0)
        isNumberField = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
        // ponytail: honor incognito / no-learning flags like a password (no learning, no suggestions, no net panels).
        val noPersonalized = (info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) ?: 0) != 0
        val noSuggestFlag = (t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        isPrivateField = isPasswordField || noPersonalized || noSuggestFlag
        lastSpaceTime = 0L
    }

    override fun onFinishInput() { super.onFinishInput(); lastSpaceTime = 0L; predictor.onInputSessionEnd() }

    override fun onFinishInputView(f: Boolean) { super.onFinishInputView(f); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }

    // ponytail: over-read + trim from one guarded helper (was 3 inline reads, 1 unguarded).
    private fun inputTextBeforeCursor(n: Int): String = try {
        (currentInputConnection?.getTextBeforeCursor(n + 16, 0)?.toString() ?: "").takeLast(n)
    } catch (_: Exception) { "" }

    private fun getCurrentWord(): String = predictor.getCurrentWord(inputTextBeforeCursor(100))

    /** Delete-then-insert as one editor transaction (was 2 IPCs — flicker + WebView races). */
    private fun replaceWordBeforeCursor(oldLen: Int, newText: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            if (oldLen > 0) ic.deleteSurroundingText(oldLen, 0)
            ic.commitText(newText, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun performBackspace() {
        val ic = currentInputConnection ?: return
        try {
            // ponytail: delete selection first (was codepoint-delete — left selected text behind).
            if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ic.deleteSurroundingTextInCodePoints(1, 0)
        else ic.deleteSurroundingText(1, 0)
    }

    private fun sendKey(code: Int, meta: Int = 0) {
        // ponytail: hoist ic so DOWN never fires without UP (was re-read per event).
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun pasteImage(item: ClipboardItem) {
        val uri = item.imageUri ?: return
        val ic = currentInputConnection ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try { ic.commitContent(InputContentInfo(uri, android.content.ClipDescription("image", arrayOf(item.mimeType)), null), InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null) } catch (_: Exception) { item.text?.let { ic.commitText(it, 1) } }
        } else item.text?.let { ic.commitText(it, 1) }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        return createComposeView(
            lifecycleOwner = this,
            viewModelStoreOwner = this,
            savedStateRegistryOwner = this
        ) {
            val themePref by prefs.themePreference.collectAsState(initial = "system")
            val isSysDark = isSystemInDarkTheme()
            val useDark = when (themePref) { "light" -> false; "dark", "amoled" -> true; else -> isSysDark }
            // ponytail: frosted glass state up front — theme + window blur both key off it.
            val blurPref by prefs.frostedGlassEnabled.collectAsState(initial = true)
            val blurAvailable = remember { FrostedGlass.isAvailable(this@HandBoardService) }
            val blurActive = blurPref && blurAvailable

            HandBoardTheme(darkTheme = useDark, translucent = blurActive) {
                applyKeyboardTheme(themePref, isSysDark, MaterialTheme.colorScheme.primary, blurActive)
                val hs by prefs.keyboardHeight.collectAsState(initial = 1.0f)
                val wp by prefs.keyboardWidth.collectAsState(initial = 100)
                val al by prefs.keyboardAlignment.collectAsState(initial = 1)
                val ln by prefs.selectedLayout.collectAsState(initial = "QWERTY")
                val hap by prefs.hapticEnabled.collectAsState(initial = true)
                val snd by prefs.soundEnabled.collectAsState(initial = false)
                val sc by prefs.suggestionCount.collectAsState(initial = 3)
                val pe by prefs.predictionsEnabled.collectAsState(initial = true)
                val bp by prefs.bottomPadding.collectAsState(initial = 0)
                val nr by prefs.numberRowEnabled.collectAsState(initial = false)
                val ac by prefs.autoCapitalize.collectAsState(initial = true)
                val acorr by prefs.autocorrectEnabled.collectAsState(initial = true)
                val sc2 by prefs.spacebarCursor.collectAsState(initial = true)
                val lk by prefs.largeKeys.collectAsState(initial = false)
                
                val clipboardEnabled by prefs.clipboardEnabled.collectAsState(initial = false)
                val searchEnabled by prefs.searchEnabled.collectAsState(initial = true)
                val currencyEnabled by prefs.currencyEnabled.collectAsState(initial = true)
                val kaomojiEnabled by prefs.kaomojiEnabled.collectAsState(initial = true)
                val phrasesEnabled by prefs.phrasesEnabled.collectAsState(initial = true)
                val translateEnabled by prefs.translateEnabled.collectAsState(initial = true)
                val textEditingEnabled by prefs.textEditingEnabled.collectAsState(initial = true)
                val emojiEnabled by prefs.emojiEnabled.collectAsState(initial = true)

                val multiEnabled by prefs.multilingualEnabled.collectAsState(initial = false)
                val activeDicts by prefs.activeDicts.collectAsState(initial = setOf("en_us"))
                val dictId by prefs.dictionaryId.collectAsState(initial = "en_us")
                androidx.compose.runtime.DisposableEffect(blurActive) {
                    // ponytail: InputMethodService.window is a Dialog — unwrap to the view Window.
                    val w = this@HandBoardService.window?.window
                    if (blurActive) FrostedGlass.applyToWindow(w) else FrostedGlass.clear(w)
                    onDispose { FrostedGlass.clear(w) }
                }

                LaunchedEffect(clipboardEnabled) {
                    // ponytail: destroy first (was create-without-destroy on rapid toggle — leaked listener).
                    clipboard?.destroy(); clipboard = null
                    if (clipboardEnabled) { clipboard = ClipboardHistory(this@HandBoardService); clipboard?.initialize() }
                }

                LaunchedEffect(multiEnabled, activeDicts, dictId) {
                    withContext(Dispatchers.IO) { predictor.loadDictionaries(this@HandBoardService, if (multiEnabled) activeDicts else setOf(dictId)) }
                }

                val ls = remember { LayoutSwitcher(ln) }
                LaunchedEffect(ln) { ls.setLayout(ln) }
                // ponytail: glide session — geometry re-measured per layout.
                val glideUi = remember { GlideUiState() }
                LaunchedEffect(ln) { glideUi.geometry.clear() }
                val glidePref by prefs.glideEnabled.collectAsState(initial = false)
                val splitPref by prefs.splitEnabled.collectAsState(initial = false)

                val sugs = remember { mutableStateListOf<String>() }
                val noLearn = isPasswordField || isNumberField || isPrivateField
                val showPred = pe && !noLearn
                // ponytail: glide needs dict + consent; never in private fields.
                val glideOk = glidePref && !noLearn

                fun updateSuggestions() {
                    sugs.clear(); if (!showPred) return
                    sugs.addAll(predictor.predict(inputTextBeforeCursor(100), sc))
                }

                Column {
                    KeyboardWrapper(widthFraction = wp / 100f, alignment = al) {
                        KeyboardView(
                            layoutSwitcher = ls, preferencesManager = prefs, heightScale = if (lk) hs * 1.25f else hs,
                            hapticEnabled = hap, soundEnabled = snd, numberRowEnabled = nr, spacebarCursor = sc2,
                            clipboardEnabled = clipboardEnabled && !isPasswordField, searchEnabled = searchEnabled && !noLearn, currencyEnabled = currencyEnabled && !noLearn,
                            kaomojiEnabled = kaomojiEnabled, phrasesEnabled = phrasesEnabled, translateEnabled = translateEnabled && !noLearn,
                            textEditingEnabled = textEditingEnabled, emojiEnabled = emojiEnabled,
                            clipboardHistory = if (clipboardEnabled) clipboard else null,
                            // ponytail: glide decode + commit (fresh word — no space-autocorrect rerun).
                            glideEnabled = glideOk,
                            glideUi = glideUi,
                            onGlideDecode = { trail -> predictor.decodeGlide(trail, glideUi.geometry.snapshot(), sc) },
                            onGlideCandidates = { sugs.clear(); sugs.addAll(it) },
                            onGlideCommit = { word ->
                                if (!noLearn) {
                                    currentInputConnection?.commitText("$word ", 1)
                                    predictor.onWordCommitted(word)
                                }
                                updateSuggestions()
                            },
                            // ponytail: split assumes full-width two-thumb; one-hand widths keep unsplit.
                            splitEnabled = splitPref && wp == 100,
                            suggestionBar = if (showPred) { { SuggestionBar(suggestions = sugs, onSuggestionClick = { 
                                val cur = getCurrentWord()
                                replaceWordBeforeCursor(cur.length, "$it ")
                                if (!noLearn) predictor.onWordCommitted(it)
                                updateSuggestions()
                            }) } } else null,
                            onTextInput = { text ->
                                val ic = currentInputConnection
                                if (ic != null) {
                                    // ponytail: hoisted for space-branch + post-commit use (was scoped inside if).
                                    var preWord = ""
                                    var corrected = false
                                    if (text == " ") {
                                        val now = System.currentTimeMillis()
                                        if (now - lastSpaceTime < 400 && !isPasswordField) {
                                            // ponytail: verify the char is really a space (was blind delete).
                                            val before = try { ic.getTextBeforeCursor(1, 0)?.toString() } catch (_: Exception) { null }
                                            if (before == " ") ic.deleteSurroundingText(1, 0)
                                            ic.commitText(". ", 1); lastSpaceTime = 0L; updateSuggestions(); return@KeyboardView
                                        }
                                        lastSpaceTime = now
                                        // ponytail: autocorrect on space + learn typed word for bigrams. Skip passwords/numbers.
                                        // getCurrentWord() AFTER space is empty, so capture before committing.
                                        preWord = getCurrentWord()
                                        corrected = false
                                        if (acorr && !noLearn) {
                                            predictor.autocorrect(preWord)?.let { fix ->
                                                replaceWordBeforeCursor(preWord.length, fix)
                                                predictor.onWordCommitted(fix)
                                                corrected = true
                                            }
                                        }
                                    } else lastSpaceTime = 0L

                                    val final = if (text.length == 1 && text[0].isLetter() && ac && !isPasswordField) {
                                        val b = try { ic.getTextBeforeCursor(2, 0)?.toString() ?: "" } catch (_: Exception) { "" }
                                        // ponytail: ROOT locale (was default — Turkish i→İ broke English caps).
                                        if (b.isEmpty() || b.trimEnd().lastOrNull() in listOf('.', '!', '?', '\n')) text.uppercase(java.util.Locale.ROOT) else text
                                    } else text
                                    ic.commitText(final, 1)

                                    if (text == " " && !corrected) { if (!noLearn && preWord.isNotEmpty()) predictor.onWordCommitted(preWord) }
                                    updateSuggestions()
                                }
                            },
                            onBackspace = { performBackspace(); updateSuggestions() },
                            onEnter = { val w = getCurrentWord(); if (!noLearn && w.isNotEmpty()) predictor.onWordCommitted(w); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER); sugs.clear() },
                            onEmojiInput = { currentInputConnection?.commitText(it, 1) },
                            onCursorMove = { val c = if (it > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT; sendKey(c) },
                            onCursorHome = { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }, onCursorEnd = { sendKey(KeyEvent.KEYCODE_MOVE_END) },
                            onSelectAll = { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }, onCopy = { currentInputConnection?.performContextMenuAction(android.R.id.copy) }, onCut = { currentInputConnection?.performContextMenuAction(android.R.id.cut) }, onPaste = { currentInputConnection?.performContextMenuAction(android.R.id.paste) },
                            onUndo = { sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON) }, onRedo = { sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON) },
                            onPasteImage = { pasteImage(it) },
                            onDismissKeyboard = { requestHideSelf(0) },
                            onOpenSettings = {
                                val intent = Intent(this@HandBoardService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                                requestHideSelf(0)
                            }
                        )
                    }
                    if (bp > 0) Spacer(Modifier.fillMaxWidth().height(bp.dp).background(KeyboardBackground))
                }
            }
        }
    }
    
    override fun onDestroy() { 
        clipboard?.destroy(); 
        clipboard = null; 
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy() 
    }
}
