package com.vintagevinyl.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vintagevinyl.domain.library.LibraryIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * The music library: MediaStore tracks merged with imported files, indexed for browsing and
 * search. Rescans automatically when MediaStore changes (debounced) or when imports change.
 */
class LibraryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val imported: ImportedTracksStore,
) {
    private val source = MediaStoreSource(context)

    private val _index = MutableStateFlow(LibraryIndex.EMPTY)
    val index: StateFlow<LibraryIndex> = _index.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var scanJob: Job? = null
    private var debounceJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1_500.milliseconds)
                refresh()
            }
        }
    }

    init {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        // Re-merge whenever the set of imported files changes (also performs the first scan).
        scope.launch { imported.tracks.collect { refresh() } }
    }

    /** Call after the permission dialog returns. */
    fun onPermissionResult() {
        _hasPermission.value = checkPermission()
        refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = scope.launch {
            _scanning.value = true
            try {
                val granted = checkPermission()
                _hasPermission.value = granted
                val scanned = if (granted) withContext(Dispatchers.IO) { source.query() } else emptyList()
                val merged = scanned + imported.tracks.value
                _index.value = withContext(Dispatchers.Default) { LibraryIndex.build(merged) }
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun checkPermission(): Boolean = ContextCompat.checkSelfPermission(context, audioPermission()) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        fun audioPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
