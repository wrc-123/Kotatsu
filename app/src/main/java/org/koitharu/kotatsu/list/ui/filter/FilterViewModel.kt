package org.koitharu.kotatsu.list.ui.filter

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.base.domain.MangaDataRepository
import org.koitharu.kotatsu.base.ui.BaseViewModel
import org.koitharu.kotatsu.core.model.MangaTag
import org.koitharu.kotatsu.core.model.SortOrder
import org.koitharu.kotatsu.core.parser.RemoteMangaRepository
import org.koitharu.kotatsu.utils.ext.replaceWith
import java.util.*

class FilterViewModel(
	private val repository: RemoteMangaRepository,
	dataRepository: MangaDataRepository,
) : BaseViewModel(), OnFilterChangedListener {

	val filter = MutableLiveData<List<FilterItem>>()
	val result = MutableLiveData<FilterState>()
	private var job: Job? = null
	private var selectedSortOrder: SortOrder? = repository.sortOrders.firstOrNull()
	private val selectedTags = HashSet<MangaTag>()
	private var searchQuery: String = ""
	private val localTagsDeferred = viewModelScope.async(Dispatchers.Default) {
		dataRepository.findTags(repository.source)
	}
	private var availableTagsDeferred = loadTagsAsync()

	override fun onSortItemClick(item: FilterItem.Sort) {
		selectedSortOrder = item.order
		updateFilters(updateResults = true)
	}

	override fun onTagItemClick(item: FilterItem.Tag) {
		val isModified = if (item.isChecked) {
			selectedTags.remove(item.tag)
		} else {
			selectedTags.add(item.tag)
		}
		if (isModified) {
			updateFilters(updateResults = true)
		}
	}

	fun updateState(state: FilterState?) {
		if (state != null) {
			selectedSortOrder = state.sortOrder
			selectedTags.replaceWith(state.tags)
		}
		if (job == null) {
			showFilter()
		} else {
			updateFilters(updateResults = false)
		}
	}

	fun performSearch(query: String) {
		if (searchQuery != query) {
			searchQuery = query
			updateFilters(updateResults = false)
		}
	}

	@AnyThread
	private fun updateFilters(updateResults: Boolean) {
		val previousJob = job
		val query = searchQuery
		job = launchJob(Dispatchers.Default) {
			previousJob?.cancelAndJoin()
			if (query.isNotEmpty()) {
				showFilteredTags(query)
				return@launchJob
			}
			val tags = tryLoadTags()
			val localTags = localTagsDeferred.await()
			val sortOrders = repository.sortOrders
			val list = ArrayList<FilterItem>(sortOrders.size + (tags?.size ?: 1) + 2)
			list.add(FilterItem.Header(R.string.sort_order))
			sortOrders.sortedBy { it.ordinal }.mapTo(list) {
				FilterItem.Sort(it, isSelected = it == selectedSortOrder)
			}
			if (tags == null || tags.isNotEmpty() || selectedTags.isNotEmpty()) {
				list.add(FilterItem.Header(R.string.genres))
				val mappedTags = TreeSet<FilterItem.Tag>(compareBy({ !it.isChecked }, { it.tag.title }))
				localTags.mapTo(mappedTags) { FilterItem.Tag(it, isChecked = it in selectedTags) }
				tags?.mapTo(mappedTags) { FilterItem.Tag(it, isChecked = it in selectedTags) }
				selectedTags.mapTo(mappedTags) { FilterItem.Tag(it, isChecked = true) }
				list.addAll(mappedTags)
				if (tags == null) {
					list.add(FilterItem.Error(R.string.filter_load_error))
				}
			}
			ensureActive()
			filter.postValue(list)
		}
		if (updateResults) {
			result.postValue(FilterState(selectedSortOrder, selectedTags))
		}
	}

	private fun showFilter() {
		job = launchJob(Dispatchers.Default) {
			val sortOrders = repository.sortOrders
			val list = ArrayList<FilterItem>(sortOrders.size + selectedTags.size + 3)
			list.add(FilterItem.Header(R.string.sort_order))
			sortOrders.sortedBy { it.ordinal }.mapTo(list) {
				FilterItem.Sort(it, isSelected = it == selectedSortOrder)
			}
			if (selectedTags.isNotEmpty()) {
				list.add(FilterItem.Header(R.string.genres))
				selectedTags.sortedBy { it.title }.mapTo(list) {
					FilterItem.Tag(it, isChecked = it in selectedTags)
				}
			}
			list.add(FilterItem.Loading)
			filter.postValue(list)
			updateFilters(updateResults = false)
		}
	}

	@WorkerThread
	private suspend fun showFilteredTags(query: String) {
		val tags = tryLoadTags()
		val localTags = localTagsDeferred.await()
		val list = ArrayList<FilterItem>()
		val mappedTags = TreeSet<FilterItem.Tag>(compareBy({ !it.isChecked }, { it.tag.title }))
		localTags.mapNotNullTo(mappedTags) {
			if (it.title.contains(query, ignoreCase = true)) {
				FilterItem.Tag(it, isChecked = it in selectedTags)
			} else {
				null
			}
		}
		tags?.mapNotNullTo(mappedTags) {
			if (it.title.contains(query, ignoreCase = true)) {
				FilterItem.Tag(it, isChecked = it in selectedTags)
			} else {
				null
			}
		}
		selectedTags.mapNotNullTo(mappedTags) {
			if (it.title.contains(query, ignoreCase = true)) {
				FilterItem.Tag(it, isChecked = true)
			} else {
				null
			}
		}
		list.addAll(mappedTags)
		if (tags == null) {
			list.add(FilterItem.Error(R.string.filter_load_error))
		}
		if (list.isEmpty()) {
			list.add(FilterItem.Error(R.string.nothing_found))
		}
		currentCoroutineContext().ensureActive()
		filter.postValue(list)
	}

	private suspend fun tryLoadTags(): Set<MangaTag>? {
		val shouldRetryOnError = availableTagsDeferred.isCompleted
		val result = availableTagsDeferred.await()
		if (result == null && shouldRetryOnError) {
			availableTagsDeferred = loadTagsAsync()
			return availableTagsDeferred.await()
		}
		return result
	}

	private fun loadTagsAsync() = viewModelScope.async(Dispatchers.Default) {
		runCatching {
			repository.getTags()
		}.onFailure { error ->
			if (BuildConfig.DEBUG) {
				error.printStackTrace()
			}
		}.getOrNull()
	}
}