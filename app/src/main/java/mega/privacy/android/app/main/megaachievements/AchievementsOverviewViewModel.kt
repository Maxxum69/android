package mega.privacy.android.app.main.megaachievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mega.privacy.android.app.utils.Util
import mega.privacy.android.domain.entity.achievement.AchievementType
import mega.privacy.android.domain.entity.achievement.AchievementsOverview
import mega.privacy.android.domain.entity.achievement.AwardedAchievementInvite
import mega.privacy.android.domain.usecase.achievements.AreAchievementsEnabledUseCase
import mega.privacy.android.domain.usecase.achievements.GetAccountAchievementsOverview
import mega.privacy.android.domain.usecase.achievements.IsAddPhoneRewardEnabledUseCase
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * View Model for achievements related data
 */
@HiltViewModel
class AchievementsOverviewViewModel @Inject constructor(
    private val getAccountAchievementsOverview: GetAccountAchievementsOverview,
    private val areAchievementsEnabled: AreAchievementsEnabledUseCase,
    private val isAddPhoneRewardEnabled: IsAddPhoneRewardEnabledUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsUIState())

    /**
     * Achievements state
     */
    val state = _state.asStateFlow()

    init {
        logAchievementsEnabled()
        getAchievementsOverview()
        updateAddPhoneReward()
    }

    private fun updateAddPhoneReward() {
        viewModelScope.launch {
            val phoneRewardEnabled = isAddPhoneRewardEnabled()
            _state.update {
                it.copy(hasAddPhoneReward = phoneRewardEnabled)
            }
        }
    }

    private fun logAchievementsEnabled() {
        viewModelScope.launch {
            Timber.d("Achievements are enabled: ${areAchievementsEnabled()}")
        }
    }

    private fun getAchievementsOverview() {
        viewModelScope.launch {
            runCatching { getAccountAchievementsOverview() }
                .onSuccess { overview ->
                    _state.value = AchievementsUIState(
                        achievementsOverview = overview,
                        currentStorage = overview.currentStorageInBytes,
                        areAllRewardsExpired = overview.areAllRewardsExpired(),
                        hasReferrals = overview.hasAnyReferrals(),
                        referralsStorage = overview.referralsStorage(),
                        referralsAwardStorage = overview.achievedStorageFromReferralsInBytes,
                        installAppStorage = overview.installAppStorage(),
                        installAppAwardDaysLeft = overview.installAppAwardDaysLeft(),
                        installAppAwardStorage = overview.installAppAwardStorage(),
                        addPhoneStorage = overview.addPhoneStorage(),
                        addPhoneAwardDaysLeft = overview.addPhoneAwardDaysLeft(),
                        addPhoneAwardStorage = overview.addPhoneAwardStorage(),
                        installDesktopStorage = overview.installDesktopStorage(),
                        installDesktopAwardDaysLeft = overview.installDesktopAwardDaysLeft(),
                        installDesktopAwardStorage = overview.installDesktopAwardStorage(),
                        hasRegistrationAward = overview.hasRegistrationAward(),
                        registrationAwardDaysLeft = overview.registrationAwardDaysLeft(),
                        registrationAwardStorage = overview.registrationAwardStorage(),
                    )
                }
                .onFailure { _state.value = AchievementsUIState(showError = true) }
        }
    }

    private fun AchievementsOverview.registrationAwardDaysLeft() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_WELCOME
        }?.expirationTimestampInDays?.getDaysLeft()

    private fun AchievementsOverview.registrationAwardStorage() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_WELCOME
        }?.rewardedStorageInBytes ?: 0

    private fun AchievementsOverview.hasRegistrationAward() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_WELCOME
        } != null

    private fun AchievementsOverview.installDesktopAwardDaysLeft() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_DESKTOP_INSTALL
        }?.expirationTimestampInDays?.getDaysLeft()

    private fun AchievementsOverview.installDesktopAwardStorage() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_DESKTOP_INSTALL
        }?.rewardedStorageInBytes ?: 0

    private fun AchievementsOverview.addPhoneAwardDaysLeft() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_ADD_PHONE
        }?.expirationTimestampInDays?.getDaysLeft()

    private fun AchievementsOverview.addPhoneAwardStorage() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_ADD_PHONE
        }?.rewardedStorageInBytes ?: 0

    private fun AchievementsOverview.installAppAwardDaysLeft() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_MOBILE_INSTALL
        }?.expirationTimestampInDays?.getDaysLeft()

    private fun AchievementsOverview.installAppAwardStorage() =
        this.awardedAchievements.firstOrNull {
            it.type == AchievementType.MEGA_ACHIEVEMENT_MOBILE_INSTALL
        }?.rewardedStorageInBytes ?: 0

    private fun AchievementsOverview.installDesktopStorage() = this.allAchievements.firstOrNull {
        it.type == AchievementType.MEGA_ACHIEVEMENT_DESKTOP_INSTALL
    }?.grantStorageInBytes

    private fun AchievementsOverview.addPhoneStorage() = this.allAchievements.firstOrNull {
        it.type == AchievementType.MEGA_ACHIEVEMENT_ADD_PHONE
    }?.grantStorageInBytes

    private fun AchievementsOverview.installAppStorage() = this.allAchievements.firstOrNull {
        it.type == AchievementType.MEGA_ACHIEVEMENT_MOBILE_INSTALL
    }?.grantStorageInBytes

    private fun AchievementsOverview.referralsStorage() = this.allAchievements.firstOrNull {
        it.type == AchievementType.MEGA_ACHIEVEMENT_INVITE
    }?.grantStorageInBytes

    private fun AchievementsOverview.hasAnyReferrals() =
        this.achievedStorageFromReferralsInBytes > 0 || this.achievedTransferFromReferralsInBytes > 0

    /**
     * Check if all invite achievements are expired
     */
    private fun AchievementsOverview.areAllRewardsExpired(): Boolean {
        var expiredAwardCount = 0
        this.awardedAchievements.forEach { award ->
            if (award is AwardedAchievementInvite && award.expirationTimestampInDays.getDaysLeft() < 0) {
                expiredAwardCount++
            } else {
                Timber.d("Type of achievement: ${award.type}")
            }
        }
        return expiredAwardCount == this.awardedAchievements.size
    }

    /**
     * Gets the numbers of days left from the expiration timestamp received
     */
    private fun Long.getDaysLeft(): Long {
        val start = Util.calculateDateFromTimestamp(this)
        val end = Calendar.getInstance()
        val startTime = start.timeInMillis
        val endTime = end.timeInMillis
        val diffTime = startTime - endTime
        return diffTime / TimeUnit.DAYS.toMillis(1)
    }
}
