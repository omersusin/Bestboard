package handboard.app.layout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import handboard.app.prediction.glide.GlidePoint
import handboard.app.prediction.glide.GlideUiState
import handboard.app.clipboard.ClipboardHistory
import handboard.app.clipboard.ClipboardItem
import handboard.app.clipboard.ClipboardView
import handboard.app.core.theme.KeyboardBackground
import handboard.app.core.theme.NumberRowBackground
import handboard.app.emoji.EmojiView
import handboard.app.emoji.KaomojiView
import handboard.app.search.SearchPanel
import handboard.app.currency.CurrencyPanel
import handboard.app.layout.KeyAction
import handboard.app.layout.KeyData
import handboard.app.layout.KeyStyle
import handboard.app.layout.KeyboardLayer
import handboard.app.layout.KeyboardState
import handboard.app.layout.LayoutSwitcher
import handboard.app.settings.PreferencesManager
import kotlinx.coroutines.launch

@Composable
fun KeyboardView(
    layoutSwitcher: LayoutSwitcher,
    preferencesManager: PreferencesManager,
    heightScale: Float = 1f,
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = false,
    numberRowEnabled: Boolean = false,
    spacebarCursor: Boolean = true,
    clipboardEnabled: Boolean = false, searchEnabled: Boolean = true, currencyEnabled: Boolean = true,
    kaomojiEnabled: Boolean = true, phrasesEnabled: Boolean = true, translateEnabled: Boolean = true,
    textEditingEnabled: Boolean = true, emojiEnabled: Boolean = true,
    clipboardHistory: ClipboardHistory? = null,
    suggestionBar: (@Composable () -> Unit)? = null,
    // ponytail: glide typing — trail capture + decode, all no-ops when disabled/null.
    glideEnabled: Boolean = false,
    glideUi: GlideUiState? = null,
    onGlideDecode: (List<GlidePoint>) -> List<String> = { emptyList() },
    onGlideCandidates: (List<String>) -> Unit = {},
    onGlideCommit: (String) -> Unit = {},
    onTextInput: (String) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit,
    onEmojiInput: (String) -> Unit = onTextInput,
    onCursorMove: (Int) -> Unit = {}, onCursorHome: () -> Unit = {}, onCursorEnd: () -> Unit = {},
    onSelectAll: () -> Unit = {}, onCopy: () -> Unit = {}, onCut: () -> Unit = {},
    onPaste: () -> Unit = {}, onUndo: () -> Unit = {}, onRedo: () -> Unit = {},
    onPasteImage: (ClipboardItem) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onDismissKeyboard: () -> Unit = {}
) {
    val state = remember { KeyboardState() }
    val layout = layoutSwitcher.currentLayout
    var currentPanel by remember { mutableStateOf(KeyboardPanel.KEYBOARD) }
    val scope = rememberCoroutineScope()
    var panelQuery by remember { mutableStateOf("") } 

    // ★ WebView açık mı durumu (Böylece klavyeyi ve toolbarı gizleyebiliriz)
    var isBrowserOpen by remember { mutableStateOf(false) }

    val hasSymbolRows2 = layout.symbolRows2.isNotEmpty()
    val currentRows = when (state.currentLayer) {
        KeyboardLayer.LETTERS -> layout.letterRows; KeyboardLayer.SYMBOLS -> layout.symbolRows
        KeyboardLayer.SYMBOLS2 -> if (hasSymbolRows2) layout.symbolRows2 else layout.symbolRows
    }
    val numberRow = listOf("1","2","3","4","5","6","7","8","9","0").map { KeyData(it, KeyAction.Text(it), 1f, KeyStyle.NORMAL) }

    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(KeyboardBackground)) {
        
        // Öneriler Çubuğu (Klavye Normal ve Browser Kapalı İken)
        if (currentPanel == KeyboardPanel.KEYBOARD && !isBrowserOpen) suggestionBar?.invoke()

        val isInputPanel = currentPanel == KeyboardPanel.SEARCH || currentPanel == KeyboardPanel.TRANSLATE || currentPanel == KeyboardPanel.CURRENCY

        // WebView Açık Değilse Toolbar Göster
        if (!isBrowserOpen) {
            LayoutToolbar(
                currentLayoutName = layoutSwitcher.currentLayoutName, currentPanel = currentPanel,
                searchEnabled = searchEnabled, currencyEnabled = currencyEnabled, clipboardEnabled = clipboardEnabled,
                kaomojiEnabled = kaomojiEnabled, phrasesEnabled = phrasesEnabled, translateEnabled = translateEnabled,
                textEditingEnabled = textEditingEnabled, emojiEnabled = emojiEnabled,
                onSwitchLayout = {
                    layoutSwitcher.nextLayout(); state.switchToLetters(); currentPanel = KeyboardPanel.KEYBOARD
                    scope.launch { preferencesManager.setSelectedLayout(layoutSwitcher.currentLayoutName) }
                },
                onSwitchPanel = { currentPanel = it; panelQuery = "" },
                onOpenSettings = onOpenSettings
            )
        }

        // Panel Yönlendirme (Browser açılırsa klavye gizlenir)
        if (currentPanel == KeyboardPanel.SEARCH) {
            SearchPanel(
                query = panelQuery, 
                onQueryChange = { panelQuery = it }, 
                onTextCommit = { onTextInput(it); currentPanel = KeyboardPanel.KEYBOARD; panelQuery = ""; isBrowserOpen = false }, 
                onClose = { currentPanel = KeyboardPanel.KEYBOARD; panelQuery = ""; isBrowserOpen = false }, 
                onDismissKeyboard = onDismissKeyboard,
                onBrowserStateChange = { isOpen -> isBrowserOpen = isOpen }, // Browser state yönetimi
                maxHeight = if (isBrowserOpen) 400 else 260 // WebView açılınca yüksekliği arttırıyoruz
            )
        } else if (currentPanel == KeyboardPanel.TRANSLATE) {
            TranslatePanel(query = panelQuery, onQueryChange = { panelQuery = it }, onInsertText = { onTextInput(it); currentPanel = KeyboardPanel.KEYBOARD; panelQuery = "" }, onClose = { currentPanel = KeyboardPanel.KEYBOARD; panelQuery = "" })
        } else if (currentPanel == KeyboardPanel.CURRENCY) {
            CurrencyPanel(query = panelQuery, onQueryChange = { panelQuery = it }, onResultCommit = { onTextInput(it); currentPanel = KeyboardPanel.KEYBOARD; panelQuery = "" }, onClose = { currentPanel = KeyboardPanel.KEYBOARD; panelQuery = "" })
        }

        // Klavye Tuşlarını Sadece BROWSER KAPALIYKEN ve GEÇERLİ PANEL AÇIKKEN Çiz
        if (!isBrowserOpen && (currentPanel == KeyboardPanel.KEYBOARD || isInputPanel)) {
            // ponytail: glide session — window offset for trail coords, decode throttle, commit wrapper.
            var gridWindow by remember { mutableStateOf(Offset.Zero) }
            val commitGlide: (String) -> Unit = { word -> state.onTextCommitted(); onGlideCommit(word) }
            val glideActiveNow = glideEnabled && glideUi != null &&
                currentPanel == KeyboardPanel.KEYBOARD && state.currentLayer == KeyboardLayer.LETTERS
            key(layoutSwitcher.currentLayoutName, state.currentLayer) {
                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        .padding(horizontal = 4.dp).padding(bottom = 6.dp)
                        .onGloballyPositioned { gridWindow = it.positionInWindow() }
                        .pointerInput(glideActiveNow) {
                            if (!glideActiveNow || glideUi == null) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val t0 = System.currentTimeMillis()
                                val pts = mutableListOf(
                                    GlidePoint(down.position.x + gridWindow.x, down.position.y + gridWindow.y, 0L)
                                )
                                var gliding = false
                                var lastDecode = 0L
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Release) break
                                    if (event.type != PointerEventType.Move) continue
                                    val change = event.changes.firstOrNull() ?: continue
                                    val now = System.currentTimeMillis()
                                    pts.add(GlidePoint(change.position.x + gridWindow.x, change.position.y + gridWindow.y, now - t0))
                                    if (!gliding && (change.position - down.position).getDistance() > 48f) {
                                        gliding = true
                                        glideUi.active = true
                                    }
                                    if (gliding) {
                                        glideUi.trail.clear()
                                        glideUi.trail.addAll(pts)
                                        // ponytail: decode at most ~15/s (fuzzy over trie per move is not free).
                                        if (now - lastDecode > 64L) {
                                            lastDecode = now
                                            onGlideCandidates(onGlideDecode(pts.toList()))
                                        }
                                    }
                                }
                                glideUi.active = false
                                glideUi.trail.clear()
                                if (gliding) {
                                    val final = onGlideDecode(pts)
                                    if (final.isNotEmpty()) commitGlide(final.first())
                                    else onGlideCandidates(emptyList())
                                }
                            }
                        }
                        .drawBehind {
                            val ui = glideUi
                            if (ui != null && ui.trail.size > 1) {
                                val local = ui.trail.map { Offset(it.x - gridWindow.x, it.y - gridWindow.y) }
                                drawPoints(local, PointMode.Polygon, handboard.app.core.theme.ShiftActiveBackground, strokeWidth = 12f)
                            }
                        }
                ) {
                    if (numberRowEnabled && state.currentLayer == KeyboardLayer.LETTERS) {
                        Row(modifier = Modifier.fillMaxWidth().background(NumberRowBackground).padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            numberRow.forEach { kd -> KeyView(modifier = Modifier.weight(1f), keyData = kd, isShifted = false, isCapsLock = false, currentLayer = state.currentLayer, heightScale = heightScale * 0.8f, hapticEnabled = hapticEnabled, soundEnabled = soundEnabled, isGliding = { glideUi?.active == true }, onClick = { if (isInputPanel) panelQuery += kd.label else onTextInput(kd.label) }) }
                        }
                    }

                    currentRows.forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            row.forEach { kd ->
                                KeyView(
                                    modifier = Modifier.weight(kd.widthWeight), keyData = kd, isShifted = state.shouldUpperCase, isCapsLock = state.isCapsLock, currentLayer = state.currentLayer, heightScale = heightScale, hapticEnabled = hapticEnabled, soundEnabled = soundEnabled,
                                    onCursorMove = if (spacebarCursor && kd.action is KeyAction.Space && currentPanel == KeyboardPanel.KEYBOARD) { { dir -> onCursorMove(dir) } } else null,
                                    onAltChar = { if (isInputPanel) panelQuery += it else onTextInput(it) },
                                    // ponytail: glide arbitration + geometry (letters only, window coords).
                                    isGliding = { glideUi?.active == true },
                                    onKeyPlaced = if (glideEnabled) { { ch, rect -> if (ch.isLetter()) glideUi?.geometry?.set(ch, rect) } } else null,
                                    onClick = {
                                        if (isInputPanel) {
                                            when (val act = kd.action) {
                                                is KeyAction.Text -> { val chr = if (state.shouldUpperCase && state.currentLayer == KeyboardLayer.LETTERS) act.char.uppercase() else act.char; panelQuery += chr }
                                                is KeyAction.Space -> panelQuery += " "
                                                is KeyAction.Backspace -> if (panelQuery.isNotEmpty()) panelQuery = panelQuery.dropLast(1)
                                                is KeyAction.Shift -> state.handleShiftPress(hasSymbolRows2)
                                                is KeyAction.SwitchToSymbols -> state.switchToSymbols()
                                                is KeyAction.SwitchToLetters -> state.switchToLetters()
                                                else -> {}
                                            }
                                        } else {
                                            when (val action = kd.action) {
                                                is KeyAction.Text -> { val text = if (state.shouldUpperCase && state.currentLayer == KeyboardLayer.LETTERS) action.char.uppercase() else action.char; onTextInput(text); state.onTextCommitted() }
                                                KeyAction.Space -> { onTextInput(" "); state.onTextCommitted() }
                                                KeyAction.Backspace -> onBackspace()
                                                KeyAction.Enter -> onEnter()
                                                KeyAction.Shift -> state.handleShiftPress(hasSymbolRows2)
                                                KeyAction.SwitchToSymbols -> state.switchToSymbols()
                                                KeyAction.SwitchToLetters -> state.switchToLetters()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else if (currentPanel == KeyboardPanel.EMOJI) EmojiView(heightScale = heightScale, onEmojiClick = { onEmojiInput(it) }, onBackspace = onBackspace)
        else if (currentPanel == KeyboardPanel.CLIPBOARD) { if (clipboardHistory != null) ClipboardView(clipboardHistory = clipboardHistory, heightScale = heightScale, onPasteText = { onTextInput(it) }, onPasteImage = { onPasteImage(it) }, onClearAll = { clipboardHistory.clearAll() }) }
        else if (currentPanel == KeyboardPanel.KAOMOJI) KaomojiView(heightScale = heightScale, onKaomojiClick = { onTextInput(it) })
        else if (currentPanel == KeyboardPanel.PHRASES) PhrasesPanel(onCommitText = { onTextInput(it); currentPanel = KeyboardPanel.KEYBOARD }, onClose = { currentPanel = KeyboardPanel.KEYBOARD })
        else if (currentPanel == KeyboardPanel.TEXT_EDITING) TextEditingBar(onCursorLeft = { onCursorMove(-1) }, onCursorRight = { onCursorMove(1) }, onCursorHome = onCursorHome, onCursorEnd = onCursorEnd, onSelectAll = onSelectAll, onCopy = onCopy, onCut = onCut, onPaste = onPaste, onUndo = onUndo, onRedo = onRedo, onClose = { currentPanel = KeyboardPanel.KEYBOARD })
    }
}
