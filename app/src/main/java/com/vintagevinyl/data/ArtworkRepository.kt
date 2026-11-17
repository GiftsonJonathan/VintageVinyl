package com.vintagevinyl.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.core.net.toUri
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Loads and caches album artwork (a small in-memory LRU; no image library needed).
 *
 * Strategy per track: Android 10+ asks MediaStore for a thumbnail; otherwise (and for imported
 * files) the picture embedded in the file's tags is read; older MediaStore tracks fall back to
 * the legacy album-art provider. A "no artwork" result is remembered too, so the lists do not
 * retry the same files while scrolling.
 */
class ArtworkRepository(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** @param sizePx requested edge length; the cache keys on a rounded bucket to share bitmaps. */
    suspend fun load(track: Track, sizePx: Int): Bitmap? {
        val bucket = if (sizePx <= 160) 160 else if (sizePx <= 320) 320 else 640
        val key = "${track.albumId}:${track.source}:${if (track.source == TrackSource.IMPORTED) track.id else ""}:$bucket"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val bitmap = withContext(Dispatchers.IO) { decode(track, bucket) }
        if (bitmap != null) cache.put(key, bitmap) else missing.add(key)
        return bitmap
    }

    private fun decode(track: Track, size: Int): Bitmap? = try {
        when {
            track.source == TrackSource.MEDIA_STORE && Build.VERSION.SDK_INT >= 29 && track.mediaStoreId != null ->
                context.contentResolver.loadThumbnail(track.uri.toUri(), Size(size, size), null)
            track.source == TrackSource.MEDIA_STORE && Build.VERSION.SDK_INT < 29 ->
                legacyAlbumArt(track.albumId, size) ?: embedded(track, size)
            else -> embedded(track, size)
        }
    } catch (_: Exception) {
        // loadThumbnail throws FileNotFoundException when there is no artwork.
        runCatching { embedded(track, size) }.getOrNull()
    }

    private fun embedded(track: Track, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, track.uri.toUri())
            val bytes = retriever.embeddedPicture ?: return null
            decodeSampled(bytes, size)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun legacyAlbumArt(albumId: Long, size: Int): Bitmap? {
        val uri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input -> decodeSampled(input.readBytes(), size) }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
