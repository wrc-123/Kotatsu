package org.koitharu.kotatsu.remotelist

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.list.ui.filter.FilterViewModel
import org.koitharu.kotatsu.remotelist.ui.RemoteListViewModel

val remoteListModule
	get() = module {

		viewModel { params ->
			RemoteListViewModel(get(named(params.get<MangaSource>())), get())
		}

		viewModel { params ->
			FilterViewModel(get(named(params.get<MangaSource>())), params.get())
		}
	}