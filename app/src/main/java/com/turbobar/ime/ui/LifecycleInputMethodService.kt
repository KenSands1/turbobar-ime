package com.turbobar.ime.ui

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.setViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose expects to run inside something that owns a Lifecycle, a
 * ViewModelStore, and a SavedStateRegistry — normally an Activity or
 * Fragment provides all three automatically. An InputMethodService provides
 * none of them, so this class builds and manages them by hand, tied to the
 * IME's OWN lifecycle events (onCreate / onCreateInputView / onStartInputView
 * / onFinishInputView / onDestroy) rather than an Activity's.
 *
 * This is the piece you specifically asked to confirm was bound correctly —
 * the mapping is:
 *   - service onCreate()        -> lifecycle INITIALIZED -> CREATED
 *   - onCreateInputView()       -> ComposeView is built here, tree owners
 *                                   attached, content set ONCE
 *   - onStartInputView()        -> lifecycle moves to STARTED -> RESUMED
 *                                   (keyboard is now visible/interactive)
 *   - onFinishInputView()       -> lifecycle drops back to CREATED
 *                                   (keyboard hidden, but view kept alive —
 *                                   onCreateInputView is NOT guaranteed to
 *                                   be called again on next show)
 *   - service onDestroy()       -> lifecycle DESTROYED
 *
 * NOTE: this file has not been compiled — I have no Kotlin/Android toolchain
 * in my environment. The pattern itself (LifecycleRegistry + ViewModelStore +
 * SavedStateRegistryController manually driven from IME callbacks) is a
 * well-established one for Compose-in-non-Activity hosts, but treat this as
 * a first draft to verify in Android Studio, not proven-correct code.
 */
abstract class LifecycleInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()
    }

    /** Call this from your onCreateInputView() AFTER building the ComposeView,
     *  before returning it, so Compose can find the tree owners. */
    protected fun ComposeView.attachToImeLifecycle() {
        setViewTreeLifecycleOwner(this@LifecycleInputMethodService)
        setViewTreeViewModelStoreOwner(this@LifecycleInputMethodService)
        setViewTreeSavedStateRegistryOwner(this@LifecycleInputMethodService)
        // DisposeOnDetachedFromWindowOrReleasedFromPool avoids leaking
        // composition state across the not-guaranteed-to-recreate view.
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
