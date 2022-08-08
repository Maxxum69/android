package mega.privacy.android.app.presentation.settings.chat.imagequality

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import mega.privacy.android.app.presentation.extensions.isDarkMode
import mega.privacy.android.app.presentation.theme.AndroidTheme
import mega.privacy.android.domain.entity.ThemeMode
import mega.privacy.android.domain.usecase.GetThemeMode
import javax.inject.Inject

/**
 * Fragment of [SettingsChatImageQualityActivity] which allows to change the chat image quality setting.
 */
@AndroidEntryPoint
class SettingsChatImageQualityFragment : Fragment() {

    private val viewModel: SettingsChatImageQualityViewModel by viewModels()

    @Inject
    lateinit var getThemeMode: GetThemeMode

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            val themeMode by getThemeMode()
                .collectAsState(initial = ThemeMode.System)
            AndroidTheme(isDark = themeMode.isDarkMode()) {
                ChatImageQualitySettingBody()
            }
        }
    }

    @Composable
    private fun ChatImageQualitySettingBody() {
        val uiState by viewModel.state.collectAsState()
        ChatImageQualityView(
            settingsChatImageQualityState = uiState,
            onOptionChanged = viewModel::setNewChatImageQuality
        )
    }
}