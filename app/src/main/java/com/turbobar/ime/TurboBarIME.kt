package com.turbobar.ime

import android.content.ClipDescription
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.turbobar.ime.data.EntryKind
import com.turbobar.ime.data.InsertMode
import com.turbobar.ime.data.PrefixEntry
import com.turbobar.ime.data.SeedLoader
import com.turbobar.ime.data.TurboBarDatabase
import com.turbobar.ime.data.resolvedText
import com.turbobar.ime.qr.QrGenerator
import com.turbobar.ime.ui.KeyboardCallbacks
import com.turbobar.ime.ui.KeyboardScreen
import com.turbobar.ime.ui.KeyboardState
import com.turbobar.ime.ui.LifecycleInputMethodService
import com.turbobar.ime.ui.MacroOverlay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Native rewrite — no WebView. UI is Jetpack Compose hosted in a ComposeView
 * attached to this service's own Lifecycle/ViewModelStore/SavedStateRegistry
 * (see LifecycleInputMethodService). Data is Room, consolidated into one
 * table for both shipped words and macros (see data/PrefixEntry.kt) — this
 * replaces the earlier static JSON lookup table entirely, per your direction
 * to move the database work up ahead of the SEED submission.
 *
 * REMINDER: none of this has been compiled. I have no Kotlin/Android
 * toolchain in my environment — everything here is a careful first draft
 * built from well-established patterns, not verified working code. Treat
 * Android Studio's first build attempt as the real test, and expect to fix
 * real errors, especially around Compose-in-IME specifics (see the note on
 * AlertDialog below) that I can't be fully certain about without compiling.
 */
class TurboBarIME : LifecycleInputMethodService() {

    private lateinit var database: TurboBarDatabase
    private lateinit var keyboardState: KeyboardState

    override fun onCreate() {
        super.onCreate()
        database = TurboBarDatabase.getInstance(this)
        keyboardState = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return KeyboardState(database.prefixDao()) as T
                }
            }
        )[KeyboardState::class.java]

        lifecycleScope.launch {
            SeedLoader.seedIfEmpty(this@TurboBarIME, database.prefixDao())
        }
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        composeView.attachToImeLifecycle()
        attachOwnersToWindowDecorView() // belt-and-suspenders — see comment in LifecycleInputMethodService

        composeView.setContent {
            var dialogState by remember { mutableStateOf<DialogRequest?>(null) }
            val currentPrefix by keyboardState.prefix.collectAsState()

            MaterialTheme {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(modifier = Modifier.fillMaxWidth()) {
                        KeyboardScreen(
                            state = keyboardState,
                            imageInsertSupported = supportsImageInsert(),
                            callbacks = KeyboardCallbacks(
                                onLetter = { c ->
                                    val cased = if (keyboardState.shiftMode.value != com.turbobar.ime.ui.ShiftMode.NONE)
                                        c.uppercaseChar() else c
                                    currentInputConnection?.commitText(cased.toString(), 1)
                                    keyboardState.onLetterTyped(c)
                                },
                                onSpace = {
                                    currentInputConnection?.commitText(" ", 1)
                                    keyboardState.onWordBoundary()
                                },
                                onSymbol = { s ->
                                    currentInputConnection?.commitText(s, 1)
                                    keyboardState.onWordBoundary()
                                },
                                onBackspace = {
                                    currentInputConnection?.deleteSurroundingText(1, 0)
                                    keyboardState.onBackspace()
                                },
                                onShift = { keyboardState.cycleShift() },
                                onSlotTap = { entry -> commitSlot(entry) },
                                onSlotLongPress = { _, entry ->
                                    dialogState = DialogRequest(entry)
                                }
                            )
                        )
                    }

                    // Layered ON TOP of the keyboard within the SAME Box/window,
                    // not a separate system dialog — see MacroOverlay's own doc
                    // comment for why (a real AlertDialog took the whole keyboard
                    // down on first device test instead of showing anything).
                    dialogState?.let { req ->
                        MacroOverlay(
                            editing = req.entry?.takeIf { it.kind == EntryKind.MACRO },
                            defaultPrefix = currentPrefix,
                            defaultText = "", // a native build can read real draft text via
                                              // InputConnection.getTextBeforeCursor() here —
                                              // deferred in this pass, see KeyboardState's
                                              // limitation note
                            onSave = { result ->
                                lifecycleScope.launch {
                                    keyboardState.saveMacro(
                                        prefix = result.prefix,
                                        label = result.label,
                                        text = result.text,
                                        insertMode = result.insertMode,
                                        editingId = req.entry?.id
                                    )
                                }
                                dialogState = null
                            },
                            onReset = req.entry?.let { entry ->
                                {
                                    lifecycleScope.launch { keyboardState.resetEntry(entry.id) }
                                    dialogState = null
                                }
                            },
                            onCancel = { dialogState = null }
                        )
                    }
                }
            }
        }

        return composeView
    }

    private fun commitSlot(entry: PrefixEntry) {
        val sourceLen = keyboardState.currentPrefixLength()
        val ic = currentInputConnection ?: return

        if (entry.insertMode == InsertMode.QR) {
            val committed = commitQrImage(entry.resolvedText.trim())
            if (!committed) {
                // field can't take images, or generation failed — fall back
                // to inserting the raw payload as text rather than losing it
                ic.deleteSurroundingText(sourceLen, 0)
                ic.commitText(entry.resolvedText, 1)
            } else {
                ic.deleteSurroundingText(sourceLen, 0)
            }
            keyboardState.consumePrefixOnCommit()
            return
        }

        ic.deleteSurroundingText(sourceLen, 0)
        ic.commitText(entry.resolvedText, 1)
        keyboardState.consumePrefixOnCommit()
    }

    private fun supportsImageInsert(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val mimeTypes = info.contentMimeTypes
        return mimeTypes?.any { it.startsWith("image/") } ?: false
    }

    private fun commitQrImage(payload: String): Boolean {
        val ic = currentInputConnection ?: return false
        val editorInfo = currentInputEditorInfo ?: return false
        val mimeTypes = editorInfo.contentMimeTypes
        val imageMime = mimeTypes?.firstOrNull { it.startsWith("image/") } ?: return false

        return try {
            val bitmap: Bitmap = QrGenerator.generate(payload)
            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            val dir = File(cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "turbobar_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { it.write(bytes) }

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val description = ClipDescription("Turbo Bar QR code", arrayOf(imageMime))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val info = InputContentInfo(uri, description, null)
                ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private data class DialogRequest(val entry: PrefixEntry?)
}

