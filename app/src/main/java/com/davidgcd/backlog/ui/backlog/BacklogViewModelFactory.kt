package com.davidgcd.backlog.ui.backlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.share.ShareLinkService

class BacklogViewModelFactory(
    private val repository: BacklogRepository,
    private val shareLinkService: ShareLinkService? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == BacklogViewModel::class.java)
        return BacklogViewModel(repository, shareLinkService) as T
    }
}
