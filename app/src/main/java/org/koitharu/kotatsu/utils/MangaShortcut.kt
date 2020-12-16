package org.koitharu.kotatsu.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ShortcutManager
import android.media.ThumbnailUtils
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.PixelSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.base.domain.MangaDataRepository
import org.koitharu.kotatsu.core.model.Manga
import org.koitharu.kotatsu.reader.ui.ReaderActivity
import org.koitharu.kotatsu.utils.ext.requireBitmap
import org.koitharu.kotatsu.utils.ext.safe

class MangaShortcut(private val manga: Manga) : KoinComponent {

	private val shortcutId = manga.id.toString()
	private val coil by inject<ImageLoader>()
	private val mangaRepository by inject<MangaDataRepository>()

	@RequiresApi(Build.VERSION_CODES.N_MR1)
	suspend fun addAppShortcut(context: Context) {
		val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
		val limit = manager.maxShortcutCountPerActivity
		val builder = buildShortcutInfo(context, manga)
		val shortcuts = manager.dynamicShortcuts
		for (shortcut in shortcuts) {
			if (shortcut.id == shortcutId) {
				builder.setRank(shortcut.rank + 1)
				manager.updateShortcuts(listOf(builder.build().toShortcutInfo()))
				return
			}
		}
		builder.setRank(1)
		if (shortcuts.isNotEmpty() && shortcuts.size >= limit) {
			manager.removeDynamicShortcuts(listOf(shortcuts.minByOrNull { it.rank }!!.id))
		}
		manager.addDynamicShortcuts(listOf(builder.build().toShortcutInfo()))
	}

	suspend fun requestPinShortcut(context: Context): Boolean {
		return ShortcutManagerCompat.requestPinShortcut(
			context,
			buildShortcutInfo(context, manga).build(),
			null
		)
	}

	@RequiresApi(Build.VERSION_CODES.N_MR1)
	fun removeAppShortcut(context: Context) {
		val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
		manager.removeDynamicShortcuts(listOf(shortcutId))
	}

	private suspend fun buildShortcutInfo(
		context: Context,
		manga: Manga
	): ShortcutInfoCompat.Builder {
		val icon = safe {
			val size = getIconSize(context)
			withContext(Dispatchers.IO) {
				val bmp = coil.execute(
					ImageRequest.Builder(context)
						.data(manga.coverUrl)
						.build()
				).requireBitmap()
				ThumbnailUtils.extractThumbnail(bmp, size.width, size.height, 0)
			}
		}
		mangaRepository.storeManga(manga)
		return ShortcutInfoCompat.Builder(context, manga.id.toString())
			.setShortLabel(manga.title)
			.setLongLabel(manga.title)
			.setIcon(icon?.let {
				IconCompat.createWithAdaptiveBitmap(it)
			} ?: IconCompat.createWithResource(context, R.drawable.ic_shortcut_default))
			.setIntent(
				ReaderActivity.newIntent(context, manga.id, null)
					.setAction(ReaderActivity.ACTION_MANGA_READ)
			)
	}

	private fun getIconSize(context: Context): PixelSize {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
			(context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager).let {
				PixelSize(it.iconMaxWidth, it.iconMaxHeight)
			}
		} else {
			(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).launcherLargeIconSize.let {
				PixelSize(it, it)
			}
		}
	}

	companion object {

		@RequiresApi(Build.VERSION_CODES.N_MR1)
		fun clearAppShortcuts(context: Context) {
			val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
			manager.removeAllDynamicShortcuts()
		}
	}
}