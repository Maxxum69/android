package mega.privacy.android.app.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mega.privacy.android.app.featuretoggle.AppFeatures
import mega.privacy.android.app.main.ManagerActivity
import mega.privacy.android.app.main.megachat.ChatActivity
import mega.privacy.android.app.presentation.bottomsheet.model.NodeDeviceCenterInformation
import mega.privacy.android.app.presentation.meeting.chat.ChatHostActivity
import mega.privacy.android.app.presentation.meeting.chat.model.EXTRA_ACTION
import mega.privacy.android.app.presentation.meeting.chat.model.EXTRA_LINK
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.qualifier.ApplicationScope
import mega.privacy.android.domain.usecase.featureflag.GetFeatureFlagValueUseCase
import mega.privacy.android.navigation.MegaNavigator
import javax.inject.Inject

/**
 * Mega navigator impl
 * Centralized navigation logic instead of call navigator separately
 * We will replace with navigation component in the future
 */
internal class MegaNavigatorImpl @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val getFeatureFlagValueUseCase: GetFeatureFlagValueUseCase,
) : MegaNavigator,
    AppNavigatorImpl {

    override fun openChat(
        context: Context,
        chatId: Long?,
        action: String?,
        link: String?,
        text: String?,
        messageId: Long?,
        isOverQuota: Int?,
        flags: Int,
    ) {
        applicationScope.launch {
            val intent = if (getFeatureFlagValueUseCase(AppFeatures.NewChatActivity)) {
                Intent(context, ChatHostActivity::class.java)
            } else {
                Intent(context, ChatActivity::class.java)
            }.apply {
                this.action = action
                putExtra(EXTRA_ACTION, action)
                link?.let {
                    this.data = Uri.parse(link) // support legacy chat screen
                    putExtra(EXTRA_LINK, link) // support new chat screen
                }
                text?.let { putExtra(Constants.SHOW_SNACKBAR, text) }
                chatId?.let { putExtra(Constants.CHAT_ID, chatId) }
                messageId?.let { putExtra("ID_MSG", messageId) }
                isOverQuota?.let { putExtra("IS_OVERQUOTA", isOverQuota) }
                if (flags > 0) setFlags(flags)
            }
            context.startActivity(intent)
        }
    }

    override fun openDeviceCenterFolderNodeOptions(
        activity: Activity,
        isBackupsFolder: Boolean,
        nodeName: String,
        nodeHandle: Long,
        nodeStatus: String,
        nodeStatusColorInt: Int?,
        @DrawableRes nodeIcon: Int,
        @DrawableRes nodeStatusIcon: Int?,
    ) {
        (activity as? ManagerActivity)?.showNodeOptionsPanel(
            nodeId = NodeId(nodeHandle),
            nodeDeviceCenterInformation = NodeDeviceCenterInformation(
                name = nodeName,
                status = nodeStatus,
                statusColorInt = nodeStatusColorInt,
                icon = nodeIcon,
                statusIcon = nodeStatusIcon,
                isBackupsFolder = isBackupsFolder,
            )
        )
    }
}