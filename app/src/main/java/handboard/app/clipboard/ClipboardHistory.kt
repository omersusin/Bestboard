package handboard.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf

data class ClipboardItem(
    val text: String? = null,
    val imageUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val mimeType: String = "text/plain",
    // ponytail: stable key for LazyColumn (was timestamp — collides on rapid copies).
    val id: String = java.util.UUID.randomUUID().toString()
) {
    val isImage: Boolean get() = imageUri != null
    val displayText: String get() = text ?: "[Image]"
}

class ClipboardHistory(private val context: Context) {

    private val maxItems = 20
    private var clipboardManager: ClipboardManager? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    val items = mutableStateListOf<ClipboardItem>()

    fun initialize() {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        listener = ClipboardManager.OnPrimaryClipChangedListener { readCurrentClip() }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        readCurrentClip()
    }

    fun destroy() {
        listener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        listener = null
        clipboardManager = null
    }

    fun readCurrentClip() {
        try {
            val clip = clipboardManager?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val description = clip.description
            // ponytail: never retain clips the source marked sensitive (e.g. password managers).
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                description?.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true
            ) return

            val item = clip.getItemAt(0)
            val text = item.text?.toString()
            val uri = item.uri

            val isImage = description != null &&
                (0 until description.mimeTypeCount).any {
                    description.getMimeType(it)?.startsWith("image/") == true
                }

            val clipItem = if (isImage && uri != null) {
                // ponytail: take the actual image mime (was index 0, often text).
                val mime = (0 until (description?.mimeTypeCount ?: 0))
                    .mapNotNull { description?.getMimeType(it) }
                    .firstOrNull { it.startsWith("image/") } ?: "image/*"
                ClipboardItem(text = text, imageUri = uri, mimeType = mime)
            } else if (!text.isNullOrBlank()) {
                ClipboardItem(text = text)
            } else return

            if (items.isNotEmpty()) {
                val last = items.first()
                if (last.text == clipItem.text && last.imageUri == clipItem.imageUri) return
            }

            items.add(0, clipItem)
            if (items.size > maxItems) items.removeAt(items.lastIndex)
        } catch (_: Exception) { }
    }

    fun removeItem(item: ClipboardItem) { items.remove(item) }
    fun clearAll() {
        items.clear()
        // ponytail: Clear All also clears the system clip (was in-memory only).
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) clipboardManager?.clearPrimaryClip()
        } catch (_: Exception) { }
    }
}
