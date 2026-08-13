package com.turbobar.ime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.turbobar.ime.data.PrefixEntry
import com.turbobar.ime.data.TurboBarDatabase
import kotlinx.coroutines.launch

/**
 * A REAL screen, not something drawn inside the keyboard's own overlay.
 * This is the actual fix for the "can't type into the macro dialog's own
 * fields" problem found in on-device testing: Turbo Bar is the active
 * system keyboard, so it can't pop itself up on top of its own view to let
 * you type into it. A normal Activity is a genuinely separate screen from
 * the IME's perspective, so Android shows Turbo Bar normally here, same as
 * it would in any other app's text field.
 *
 * This is a much lower-risk Compose host than the IME's ComposeView —
 * ComponentActivity provides Lifecycle/ViewModelStore/SavedStateRegistry
 * natively, no custom bridge class needed at all.
 */
class MacroEditorActivity : ComponentActivity() {

    private val keyboardState: KeyboardState by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val dao = TurboBarDatabase.getInstance(this@MacroEditorActivity).prefixDao()
                return KeyboardState(dao) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editingId = intent.getLongExtra(EXTRA_EDITING_ID, -1L).takeIf { it != -1L }
        val defaultPrefix = intent.getStringExtra(EXTRA_DEFAULT_PREFIX) ?: ""

        setContent {
            var editingEntry by remember { mutableStateOf<PrefixEntry?>(null) }
            var loaded by remember { mutableStateOf(editingId == null) } // nothing to load if creating new

            LaunchedEffect(editingId) {
                if (editingId != null) {
                    val dao = TurboBarDatabase.getInstance(this@MacroEditorActivity).prefixDao()
                    editingEntry = dao.getById(editingId)
                    loaded = true
                }
            }

            MaterialTheme {
                Surface {
                    if (loaded) {
                        MacroOverlay(
                            editing = editingEntry,
                            defaultPrefix = defaultPrefix,
                            defaultText = "",
                            onSave = { result ->
                                lifecycleScope.launch {
                                    keyboardState.saveMacro(
                                        prefix = result.prefix,
                                        label = result.label,
                                        text = result.text,
                                        insertMode = result.insertMode,
                                        editingId = editingEntry?.id
                                    )
                                    finish()
                                }
                            },
                            onReset = editingEntry?.let { entry ->
                                {
                                    lifecycleScope.launch {
                                        keyboardState.resetEntry(entry.id)
                                        finish()
                                    }
                                }
                            },
                            onCancel = { finish() }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EDITING_ID = "editing_id"
        const val EXTRA_DEFAULT_PREFIX = "default_prefix"
    }
}
