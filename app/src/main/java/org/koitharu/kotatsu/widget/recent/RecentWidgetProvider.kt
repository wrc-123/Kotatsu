package org.koitharu.kotatsu.widget.recent

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.details.ui.DetailsActivity

class RecentWidgetProvider : AppWidgetProvider() {

	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		appWidgetIds.forEach { id ->
			val views = RemoteViews(context.packageName, R.layout.widget_recent)
			val adapter = Intent(context, RecentWidgetService::class.java)
			adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
			adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
			views.setRemoteAdapter(R.id.stackView, adapter)
			val intent = Intent(context, DetailsActivity::class.java)
			intent.action = DetailsActivity.ACTION_MANGA_VIEW
			views.setPendingIntentTemplate(
				R.id.stackView, PendingIntent.getActivity(
					context,
					0,
					intent,
					PendingIntent.FLAG_UPDATE_CURRENT
				)
			)
			views.setEmptyView(R.id.stackView, R.id.textView_holder)
			appWidgetManager.updateAppWidget(id, views)
		}
		appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.stackView)
	}
}