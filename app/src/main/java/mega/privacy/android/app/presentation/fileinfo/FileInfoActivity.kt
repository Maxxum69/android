package mega.privacy.android.app.presentation.fileinfo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.palm.composestateevents.EventEffect
import kotlinx.coroutines.launch
import mega.privacy.android.analytics.Analytics
import mega.privacy.android.app.BaseActivity
import mega.privacy.android.app.R
import mega.privacy.android.app.activities.WebViewActivity
import mega.privacy.android.app.activities.contract.DeleteVersionsHistoryActivityContract
import mega.privacy.android.app.activities.contract.SelectFolderToCopyActivityContract
import mega.privacy.android.app.activities.contract.SelectFolderToMoveActivityContract
import mega.privacy.android.app.activities.contract.SelectUsersToShareActivityContract
import mega.privacy.android.app.interfaces.ActionBackupListener
import mega.privacy.android.app.main.FileContactListActivity
import mega.privacy.android.app.main.ManagerActivity
import mega.privacy.android.app.main.controllers.NodeController
import mega.privacy.android.app.modalbottomsheet.FileContactsListBottomSheetDialogFragment
import mega.privacy.android.app.modalbottomsheet.FileContactsListBottomSheetDialogListener
import mega.privacy.android.app.modalbottomsheet.ModalBottomSheetUtil.isBottomSheetDialogShown
import mega.privacy.android.app.namecollision.data.NameCollisionUiEntity
import mega.privacy.android.app.presentation.contact.authenticitycredendials.AuthenticityCredentialsActivity
import mega.privacy.android.app.presentation.extensions.isDarkMode
import mega.privacy.android.app.presentation.fileinfo.model.FileInfoMenuAction
import mega.privacy.android.app.presentation.fileinfo.model.FileInfoOneOffViewEvent
import mega.privacy.android.app.presentation.fileinfo.model.FileInfoViewState
import mega.privacy.android.app.presentation.fileinfo.view.ExtraActionDialog
import mega.privacy.android.app.presentation.fileinfo.view.FileInfoScreen
import mega.privacy.android.app.presentation.security.PasscodeCheck
import mega.privacy.android.app.presentation.tags.TagsActivity
import mega.privacy.android.app.presentation.tags.TagsActivity.Companion.NODE_ID
import mega.privacy.android.app.presentation.transfers.attach.NodeAttachmentView
import mega.privacy.android.app.presentation.transfers.attach.NodeAttachmentViewModel
import mega.privacy.android.app.presentation.transfers.starttransfer.view.StartTransferComponent
import mega.privacy.android.app.sync.fileBackups.FileBackupManager
import mega.privacy.android.app.upgradeAccount.UpgradeAccountActivity
import mega.privacy.android.app.utils.AlertsAndWarnings
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.ContactUtil
import mega.privacy.android.app.utils.LinksUtil
import mega.privacy.android.app.utils.LocationInfo
import mega.privacy.android.app.utils.MegaNodeDialogUtil
import mega.privacy.android.app.utils.MegaNodeDialogUtil.ACTION_BACKUP_SHARE_FOLDER
import mega.privacy.android.app.utils.MegaNodeDialogUtil.showRenameNodeDialog
import mega.privacy.android.app.utils.MegaNodeUtil
import mega.privacy.android.app.utils.MegaNodeUtil.handleLocationClick
import mega.privacy.android.app.utils.MegaNodeUtil.showConfirmationLeaveIncomingShare
import mega.privacy.android.app.utils.MegaNodeUtil.showTakenDownNodeActionNotAvailableDialog
import mega.privacy.android.app.utils.Util
import mega.privacy.android.domain.entity.ThemeMode
import mega.privacy.android.domain.entity.contacts.ContactItem
import mega.privacy.android.domain.entity.node.MoveRequestResult
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.usecase.GetThemeMode
import mega.privacy.android.shared.original.core.ui.theme.OriginalTempTheme
import mega.privacy.android.shared.original.core.ui.utils.showAutoDurationSnackbar
import mega.privacy.mobile.analytics.event.NodeInfoDescriptionAddedMessageDisplayedEvent
import mega.privacy.mobile.analytics.event.NodeInfoDescriptionUpdatedMessageDisplayedEvent
import mega.privacy.mobile.analytics.event.NodeInfoScreenEvent
import nz.mega.sdk.MegaShare
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity for showing file and folder info.
 *
 * @property passCodeFacade [PasscodeCheck] an injected component to enforce a Passcode security check
 * @property getThemeMode [GetThemeMode] application them mode
 */
@AndroidEntryPoint
class FileInfoActivity : BaseActivity() {
    @Inject
    lateinit var passCodeFacade: PasscodeCheck

    @Inject
    lateinit var getThemeMode: GetThemeMode

    private lateinit var selectContactForShareFolderLauncher: ActivityResultLauncher<NodeId>
    private lateinit var versionHistoryLauncher: ActivityResultLauncher<Long>
    private lateinit var copyLauncher: ActivityResultLauncher<LongArray>
    private lateinit var moveLauncher: ActivityResultLauncher<LongArray>

    private val viewModel: FileInfoViewModel by viewModels()
    private val nodeAttachmentViewModel: NodeAttachmentViewModel by viewModels()
    private var adapterType = 0
    private var fileBackupManager: FileBackupManager? = null
    private val nodeController: NodeController by lazy { NodeController(this) }

    private var bottomSheetDialogFragment: FileContactsListBottomSheetDialogFragment? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            retryConnectionsAndSignalPresence()
            if (viewModel.isAvailableOffline().not()) {
                val intent = Intent()
                intent.putExtra(NODE_HANDLE, viewModel.nodeId.longValue)
                setResult(RESULT_OK, intent)
            }
            finish()
        }
    }

    /**
     * on create the activity
     */
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.setNode(readExtrasAndGetHandle() ?: run {
            finish()
            return
        })
        configureActivityResultLaunchers()
        initFileBackupManager()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        Analytics.tracker.trackEvent(NodeInfoScreenEvent)
        setContent {
            val themeMode by getThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.System)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackBarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            OriginalTempTheme(isDark = themeMode.isDarkMode()) {
                EventEffect(
                    event = uiState.oneOffViewEvent,
                    onConsumed = viewModel::consumeOneOffEvent,
                    action = {
                        consumeEvent(it, snackBarHostState)
                    })
                FileInfoScreen(
                    viewState = uiState,
                    snackBarHostState = snackBarHostState,
                    onBackPressed = onBackPressedDispatcher::onBackPressed,
                    onTakeDownLinkClick = this::navigateToLink,
                    onLocationClick = { this.navigateToLocation(uiState.nodeLocationInfo) },
                    availableOfflineChanged = { availableOffline ->
                        viewModel.availableOfflineChanged(availableOffline)
                    },
                    onVersionsClick = this::navigateToVersions,
                    onSetDescriptionClick = viewModel::setNodeDescription,
                    onSharedWithContactClick = { this.navigateToUserDetails(it.contactItem) },
                    onSharedWithContactSelected = { viewModel.contactSelectedInSharedList(it.contactItem.email) },
                    onSharedWithContactUnselected = { viewModel.contactUnselectedInSharedList(it.contactItem.email) },
                    onSharedWithContactMoreOptionsClick = {
                        viewModel.contactToShowOptions(it.contactItem.email)
                    },
                    onShowMoreSharedWithContactsClick = this::navigateToSharedContacts,
                    onPublicLinkCopyClick = viewModel::copyPublicLink,
                    onMenuActionClick = { handleAction(it, uiState) },
                    onVerifyContactClick = this::navigateToVerifyContacts,
                    onAddTagClick = this::navigateToTags,
                    onUpgradeAccountClick = this::navigateToUpgradeAccountScreen,
                    modifier = Modifier.semantics {
                        testTagsAsResourceId = true
                    },
                    getAddress = viewModel::getAddress,
                )
                uiState.requiredExtraAction?.let { action ->
                    ExtraActionDialog(
                        action = action,
                        onRemoveConfirmed = viewModel::removeConfirmed,
                        onPermissionSelected = viewModel::setSharePermissionForUsers,
                        onDismiss = viewModel::extraActionFinished,
                    )
                }
                StartTransferComponent(
                    uiState.downloadEvent,
                    { viewModel.consumeDownloadEvent() },
                    snackBarHostState = snackBarHostState,
                )
                NodeAttachmentView(
                    nodeAttachmentViewModel
                ) { message, chatId ->
                    coroutineScope.launch {
                        val result = snackBarHostState.showAutoDurationSnackbar(
                            message = message.ifBlank { getString(R.string.sent_as_message) },
                            actionLabel = getString(R.string.action_see)
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            startActivity(
                                Intent(this@FileInfoActivity, ManagerActivity::class.java).apply {
                                    action = Constants.ACTION_CHAT_NOTIFICATION_MESSAGE
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    putExtra(Constants.CHAT_ID, chatId)
                                    putExtra(Constants.EXTRA_MOVE_TO_CHAT_SECTION, true)
                                },
                            )
                            finish()
                        }
                    }
                }
                updateContactShareBottomSheet(uiState)
            }
        }
    }

    private fun navigateToUpgradeAccountScreen() {
        startActivity(Intent(this, UpgradeAccountActivity::class.java))
    }

    private fun navigateToTags() {
        startActivity(
            Intent(this, TagsActivity::class.java).apply {
                putExtra(NODE_ID, readExtrasAndGetHandle())
            }
        )
    }

    /**
     * on restart callback
     */
    override fun onRestart() {
        super.onRestart()
        viewModel.setNode(
            handleNode = readExtrasAndGetHandle() ?: run { finish(); return },
            forceUpdate = true,
        )
    }

    private fun navigateToVerifyContacts(email: String) {
        val intent = Intent(this, AuthenticityCredentialsActivity::class.java)
        intent.putExtra(Constants.EMAIL, email)
        startActivity(intent)
    }

    private fun readExtrasAndGetHandle() = intent.extras?.let { extras ->
        val handleNode = extras.getLong(Constants.HANDLE, -1)
        Timber.d("Handle of the selected node: %s", handleNode)
        adapterType = intent.getIntExtra(
            Constants.INTENT_EXTRA_KEY_ADAPTER_TYPE,
            Constants.FILE_BROWSER_ADAPTER
        )
        handleNode.takeIf { it >= 0 }
    } ?: run {
        Timber.w("Extras is NULL")
        null
    }

    /**
     * Initializes the FileBackupManager
     */
    private fun initFileBackupManager() {
        fileBackupManager = FileBackupManager(
            this,
            object : ActionBackupListener {
                override fun actionBackupResult(
                    actionType: Int,
                    operationType: Int,
                    result: MoveRequestResult?,
                    handle: Long,
                ) {
                    if (actionType == ACTION_BACKUP_SHARE_FOLDER && operationType == FileBackupManager.OperationType.OPERATION_EXECUTE) {
                        navigateToShare(NodeId(handle))
                    }
                }
            })
    }

    private fun configureActivityResultLaunchers() {
        configureSelectContactForShareFolderLauncher()
        configureVersionHistoryLauncher()
        configureCopyLauncher()
        configureMoveLauncher()
    }

    private fun configureSelectContactForShareFolderLauncher() {
        selectContactForShareFolderLauncher =
            registerForActivityResult(SelectUsersToShareActivityContract()) { result ->
                if (!viewModel.checkAndHandleIsDeviceConnected()) {
                    return@registerForActivityResult
                }
                result?.let {
                    val contactsData = ArrayList<String>().apply { addAll(result) }
                    if (!viewModel.isFile()) {
                        if (fileBackupManager?.shareFolder(
                                nodeController,
                                longArrayOf(viewModel.nodeId.longValue),
                                contactsData,
                                MegaShare.ACCESS_READ
                            ) != true
                        ) {
                            viewModel.initiateChangePermission(contactsData)
                        }
                    } else {
                        Timber.w("ERROR, the file is not folder")
                    }
                }
            }
    }

    private fun configureVersionHistoryLauncher() {
        versionHistoryLauncher =
            registerForActivityResult(DeleteVersionsHistoryActivityContract()) { result ->
                if (!viewModel.checkAndHandleIsDeviceConnected()) {
                    return@registerForActivityResult
                }
                if (result == viewModel.nodeId.longValue) {
                    viewModel.deleteHistoryVersions()
                }
            }
    }

    private fun configureCopyLauncher() {
        copyLauncher =
            registerForActivityResult(SelectFolderToCopyActivityContract()) { result ->
                result?.second?.let { selectedFolderNode ->
                    viewModel.copyNodeCheckingCollisions(parentHandle = NodeId(selectedFolderNode))
                }
            }
    }

    private fun configureMoveLauncher() {
        moveLauncher =
            registerForActivityResult(SelectFolderToMoveActivityContract()) { result ->
                result?.second?.let { selectedFolderNode ->
                    viewModel.moveNodeCheckingCollisions(parentHandle = NodeId(selectedFolderNode))
                }
            }
    }

    private fun handleAction(action: FileInfoMenuAction, viewState: FileInfoViewState) {
        when (action) {
            FileInfoMenuAction.Copy -> navigateToCopy()
            FileInfoMenuAction.Move -> navigateToMove()
            FileInfoMenuAction.Delete -> viewModel.initiateRemoveNode(sendToRubbish = false)
            FileInfoMenuAction.MoveToRubbishBin -> viewModel.initiateRemoveNode(sendToRubbish = true)
            FileInfoMenuAction.Rename -> showRenameDialog()
            FileInfoMenuAction.Download -> downloadNode()
            FileInfoMenuAction.GetLink, FileInfoMenuAction.ManageLink -> navigateToGetLink()
            FileInfoMenuAction.RemoveLink -> viewModel.initiateRemoveLink()
            FileInfoMenuAction.ShareFolder -> showShareFolderDialog()
            FileInfoMenuAction.Leave -> showConfirmLeaveDialog()
            FileInfoMenuAction.SendToChat -> nodeAttachmentViewModel.startAttachNodes(
                listOf(NodeId(viewModel.node.handle))
            )

            FileInfoMenuAction.DisputeTakedown -> navigateToDisputeTakeDown()
            FileInfoMenuAction.SelectionModeAction.ChangePermission -> {
                viewModel.initiateChangePermission(null)
            }

            FileInfoMenuAction.SelectionModeAction.ClearSelection -> viewModel.unselectAllContacts()
            FileInfoMenuAction.SelectionModeAction.Remove -> {
                viewModel.initiateRemoveContacts(viewState.outShareContactsSelected)
            }

            FileInfoMenuAction.SelectionModeAction.SelectAll -> viewModel.selectAllVisibleContacts()
        }
    }

    private fun navigateToLink(link: String) {
        val uriUrl = Uri.parse(link)
        val launchBrowser = Intent(this, WebViewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setData(uriUrl)
        startActivity(launchBrowser)
    }

    private fun navigateToLocation(locationInfo: LocationInfo?) {
        locationInfo?.let {
            handleLocationClick(this, adapterType, locationInfo)
        }
    }

    private fun navigateToVersions() {
        versionHistoryLauncher.launch(viewModel.nodeId.longValue)
    }

    private fun navigateToShare(nodeId: NodeId = viewModel.nodeId) {
        selectContactForShareFolderLauncher.launch(nodeId)
    }

    private fun navigateToSharedContacts() {
        startActivity(
            Intent(this, FileContactListActivity::class.java).apply {
                putExtra(Constants.NAME, viewModel.nodeId.longValue)
            }
        )
    }

    private fun navigateToUserDetails(contactItem: ContactItem) {
        ContactUtil.openContactInfoActivity(this, contactItem.email)
    }

    private fun navigateToCopy() = copyLauncher.launch(longArrayOf(viewModel.nodeId.longValue))
    private fun navigateToMove() = moveLauncher.launch(longArrayOf(viewModel.nodeId.longValue))

    private fun navigateToDisputeTakeDown() =
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(Uri.parse(Constants.DISPUTE_URL))
        )

    private fun navigateToGetLink() {
        if (showTakenDownNodeActionNotAvailableDialog(viewModel.node, this)) {
            return
        }
        LinksUtil.showGetLinkActivity(this, viewModel.nodeId.longValue)
    }

    private fun downloadNode() {
        viewModel.startDownloadNode()
    }

    private fun showConfirmLeaveDialog() {
        showConfirmationLeaveIncomingShare(
            this,
            this,
            viewModel.node
        )
    }

    private fun showShareFolderDialog() {
        val nodeType = MegaNodeUtil.checkBackupNodeTypeByHandle(megaApi, viewModel.node)
        if (nodeType != MegaNodeDialogUtil.BACKUP_NONE) {
            // Display a warning dialog when sharing a Backup folder and limit folder
            // access to read-only
            fileBackupManager?.defaultActionBackupNodeCallback?.let {
                fileBackupManager?.shareBackupsFolder(
                    nodeController = nodeController,
                    megaNode = viewModel.node,
                    nodeType = nodeType,
                    actionBackupNodeCallback = it,
                )
            }
        } else {
            navigateToShare()
        }
    }

    private fun showRenameDialog() =
        showRenameNodeDialog(this, viewModel.node, this, null)

    private suspend fun consumeEvent(
        event: FileInfoOneOffViewEvent,
        snackBarHostState: SnackbarHostState,
    ) {
        when (event) {
            FileInfoOneOffViewEvent.NotConnected -> {
                Util.showErrorAlertDialog(
                    getString(R.string.error_server_connection_problem),
                    false,
                    this
                )
            }

            FileInfoOneOffViewEvent.NodeDeleted -> {
                //the node has been deleted, this screen has no more sense
                finish()
            }

            FileInfoOneOffViewEvent.PublicLinkCopiedToClipboard -> {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    //Android 13 and up shows a system notification, so no need to show the toast
                    snackBarHostState.showAutoDurationSnackbar(getString(R.string.file_properties_get_link))
                }
            }

            is FileInfoOneOffViewEvent.GeneralError -> {
                snackBarHostState.showAutoDurationSnackbar(getString(R.string.general_error))
            }

            is FileInfoOneOffViewEvent.CollisionDetected -> {
                val list = ArrayList<NameCollisionUiEntity>()
                list.add(event.collision)
                legacyNameCollisionActivityContract?.launch(list)
            }

            is FileInfoOneOffViewEvent.Finished -> {
                if (event.exception == null) {
                    event.successMessage(this)?.let {
                        snackBarHostState.showAutoDurationSnackbar(it)
                    }
                    sendBroadcast(
                        Intent(Constants.BROADCAST_ACTION_INTENT_FILTER_UPDATE_FULL_SCREEN).setPackage(
                            applicationContext.packageName
                        )
                    )
                } else {
                    Timber.e(event.exception)
                    if (!manageCopyMoveException(event.exception)) {
                        event.failMessage(this)?.let {
                            snackBarHostState.showAutoDurationSnackbar(it)
                        }
                    }
                }
            }

            is FileInfoOneOffViewEvent.Message -> {
                snackBarHostState.showAutoDurationSnackbar(getString(event.message))
                if (event is FileInfoOneOffViewEvent.Message.NodeDescriptionAdded) {
                    Analytics.tracker.trackEvent(NodeInfoDescriptionAddedMessageDisplayedEvent)
                }
                if (event is FileInfoOneOffViewEvent.Message.NodeDescriptionUpdated) {
                    Analytics.tracker.trackEvent(NodeInfoDescriptionUpdatedMessageDisplayedEvent)
                }
            }

            is FileInfoOneOffViewEvent.OverDiskQuota -> AlertsAndWarnings.showOverDiskQuotaPaywallWarning()
        }
    }

    private fun updateContactShareBottomSheet(viewState: FileInfoViewState) =
        viewState.contactToShowOptions?.takeIf {
            !bottomSheetDialogFragment.isBottomSheetDialogShown() //this is not compose yet, so we need to check if it's already shown
        }?.let { email ->
            Timber.d("showNodeOptionsPanel")
            bottomSheetDialogFragment =
                FileContactsListBottomSheetDialogFragment(
                    viewModel.getShareFromEmail(email),
                    viewModel.node,
                    object : FileContactsListBottomSheetDialogListener {
                        override fun changePermissions(userEmail: String) {
                            viewModel.initiateChangePermission(listOf(userEmail))
                        }

                        override fun removeFileContactShare(userEmail: String) {
                            viewModel.initiateRemoveContacts(listOf(userEmail))
                        }

                        override fun fileContactsDialogDismissed() {
                            viewModel.contactToShowOptions(null)
                        }

                    }
                )
            bottomSheetDialogFragment?.show(
                supportFragmentManager,
                bottomSheetDialogFragment?.tag
            )
        }

    companion object {
        /**
         * key to return the handle to the calling activity
         */
        const val NODE_HANDLE = "NODE_HANDLE"
    }
}