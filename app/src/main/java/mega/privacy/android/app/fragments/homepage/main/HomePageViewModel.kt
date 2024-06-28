package mega.privacy.android.app.fragments.homepage.main

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeremyliao.liveeventbus.LiveEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import mega.privacy.android.app.utils.Constants.EVENT_CHAT_STATUS_CHANGE
import mega.privacy.android.domain.usecase.login.MonitorLogoutUseCase
import mega.privacy.android.domain.usecase.network.IsConnectedToInternetUseCase
import mega.privacy.android.domain.usecase.network.MonitorConnectivityUseCase
import mega.privacy.android.domain.usecase.notifications.MonitorHomeBadgeCountUseCase
import nz.mega.sdk.MegaBanner
import javax.inject.Inject

@HiltViewModel
class HomePageViewModel @Inject constructor(
    private val repository: HomepageRepository,
    isConnectedToInternetUseCase: IsConnectedToInternetUseCase,
    monitorConnectivityUseCase: MonitorConnectivityUseCase,
    private val monitorLogoutUseCase: MonitorLogoutUseCase,
    private val monitorHomeBadgeCountUseCase: MonitorHomeBadgeCountUseCase,
) : ViewModel() {
    private val _notificationCount = MutableLiveData<Int>()
    private val _avatar = MutableLiveData<Bitmap>()
    private val _chatStatus = MutableLiveData<Int>()
    private val _bannerList: MutableLiveData<MutableList<MegaBanner>?> =
        repository.getBannerListLiveData()

    val notificationCount: LiveData<Int> = _notificationCount
    val avatar: LiveData<Bitmap> = _avatar
    val chatStatus: LiveData<Int> = _chatStatus
    val bannerList: LiveData<MutableList<MegaBanner>?> = _bannerList

    /**
     * Is network connected state
     */
    val isConnected = isConnectedToInternetUseCase()

    /**
     * Monitor Internet Connectivity
     */
    val monitorConnectivity = monitorConnectivityUseCase()

    private val chatOnlineStatusObserver = androidx.lifecycle.Observer<Int> {
        _chatStatus.value = it
    }

    init {
        LiveEventBus.get(EVENT_CHAT_STATUS_CHANGE, Int::class.java)
            .observeForever(chatOnlineStatusObserver)

        viewModelScope.launch {
            monitorHomeBadgeCountUseCase().conflate().collect {
                _notificationCount.value = it
            }
        }
        viewModelScope.launch { monitorLogoutUseCase().collect { repository.logout() } }
    }

    override fun onCleared() {
        super.onCleared()

        LiveEventBus.get(EVENT_CHAT_STATUS_CHANGE, Int::class.java)
            .removeObserver(chatOnlineStatusObserver)
    }

    fun isRootNodeNull() = repository.isRootNodeNull()

    /**
     * Get banner list from the server or from memory cache
     */
    fun getBanners() {
        viewModelScope.launch { repository.loadBannerList() }
    }

    /**
     * Dismiss the banner for this account.
     * The banner would never be given again by the server once being dismissed
     */
    fun dismissBanner(banner: MegaBanner) {
        repository.dismissBanner(banner.id)
        _bannerList.value?.remove(banner)
        _bannerList.value?.let {
            _bannerList.value = it
        }
    }

}
