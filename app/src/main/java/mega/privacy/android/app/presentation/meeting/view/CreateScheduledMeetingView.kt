package mega.privacy.android.app.presentation.meeting.view

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.palm.composestateevents.EventEffect
import de.palm.composestateevents.consumed
import kotlinx.coroutines.launch
import mega.privacy.android.app.R
import mega.privacy.android.app.presentation.extensions.description
import mega.privacy.android.app.presentation.extensions.icon
import mega.privacy.android.app.presentation.extensions.meeting.StringId
import mega.privacy.android.app.presentation.extensions.meeting.getUntilZonedDateTime
import mega.privacy.android.app.presentation.extensions.title
import mega.privacy.android.app.presentation.meeting.model.CreateScheduledMeetingState
import mega.privacy.android.app.presentation.meeting.model.ScheduleMeetingAction
import mega.privacy.android.app.presentation.meeting.model.ScheduledMeetingManagementState
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.core.ui.controls.controlssliders.MegaSwitch
import mega.privacy.android.core.ui.controls.dialogs.ConfirmationWithRadioButtonsDialog
import mega.privacy.android.core.ui.controls.dialogs.MegaAlertDialog
import mega.privacy.android.core.ui.controls.divider.CustomDivider
import mega.privacy.android.core.ui.controls.textfields.GenericDescriptionTextField
import mega.privacy.android.core.ui.controls.textfields.GenericTitleTextField
import mega.privacy.android.core.ui.theme.AndroidTheme
import mega.privacy.android.core.ui.theme.extensions.black_white
import mega.privacy.android.core.ui.theme.extensions.grey_alpha_038_white_alpha_038
import mega.privacy.android.core.ui.theme.extensions.textColorSecondary
import mega.privacy.android.domain.entity.chat.ChatScheduledRules
import mega.privacy.android.domain.entity.meeting.OccurrenceFrequencyType
import mega.privacy.android.domain.entity.meeting.RecurrenceDialogOption
import mega.privacy.android.domain.entity.meeting.WaitingRoomReminders
import mega.privacy.android.domain.entity.meeting.ScheduledMeetingType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private var is24hourFormat: Boolean = false
private val timeFormatter by lazy {
    DateTimeFormatter
        .ofPattern(if (is24hourFormat) "HH:mm" else "hh:mma")
        .withZone(ZoneId.systemDefault())
}
private val dateFormatter by lazy {
    DateTimeFormatter
        .ofPattern("E',' d MMM',' yyyy")
        .withZone(ZoneId.systemDefault())
}

/**
 * Create scheduled meeting View
 */
@Composable
internal fun CreateScheduledMeetingView(
    state: CreateScheduledMeetingState,
    managementState: ScheduledMeetingManagementState,
    onDiscardClicked: () -> Unit,
    onAcceptClicked: () -> Unit,
    onStartTimeClicked: () -> Unit,
    onStartDateClicked: () -> Unit,
    onEndTimeClicked: () -> Unit,
    onEndDateClicked: () -> Unit,
    onDismiss: () -> Unit,
    onResetSnackbarMessage: () -> Unit,
    onDiscardMeetingDialog: () -> Unit,
    onCloseWarningClicked: () -> Unit,
    onDescriptionValueChange: (String) -> Unit,
    onTitleValueChange: (String) -> Unit,
    onScrollChange: (Boolean) -> Unit,
    onButtonClicked: (ScheduleMeetingAction) -> Unit = {},
    onRecurrenceDialogOptionClicked: (RecurrenceDialogOption) -> Unit,
) {

    is24hourFormat = DateFormat.is24HourFormat(LocalContext.current)
    val listState = rememberLazyListState()
    val firstItemVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()

    val shouldShowWarningDialog =
        state.enabledWaitingRoomOption && state.enabledAllowAddParticipantsOption &&
                managementState.waitingRoomReminder == WaitingRoomReminders.Enabled

    Scaffold(
        scaffoldState = scaffoldState,
        snackbarHost = {
            SnackbarHost(hostState = it) { data ->
                Snackbar(
                    snackbarData = data,
                    backgroundColor = MaterialTheme.colors.black_white
                )
            }
        },
        topBar = {
            ScheduleMeetingAppBar(
                state = state,
                onAcceptClicked = onAcceptClicked,
                onDiscardClicked = onDiscardClicked,
                onValueChange = onTitleValueChange,
                elevation = shouldShowWarningDialog || !firstItemVisible
            )
        }
    ) { paddingValues ->
        BackPressHandler(onBackPressed = onDiscardClicked)

        DiscardMeetingAlertDialog(
            state = state,
            onKeepEditing = { onDismiss() },
            onDiscard = { onDiscardMeetingDialog() })

        RecurringMeetingDialog(
            state = state,
            onOptionSelected = onRecurrenceDialogOptionClicked,
            onDiscard = onDismiss
        )

        Column {
            if (shouldShowWarningDialog) {
                WaitingRoomWarningDialog(
                    onCloseClicked = onCloseWarningClicked
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.padding(paddingValues)
            ) {

                item(key = "Schedule meeting start date and time") {
                    ScheduledMeetingDateAndTime(
                        state = state,
                        isStart = true,
                        onDateClicked = onStartDateClicked,
                        onTimeClicked = onStartTimeClicked
                    )
                }

                item(key = "Schedule meeting end date and time") {
                    ScheduledMeetingDateAndTime(
                        state = state,
                        isStart = false,
                        onDateClicked = onEndDateClicked,
                        onTimeClicked = onEndTimeClicked
                    )
                }

                items(state.buttons) { button ->
                    ActionButton(
                        state = state,
                        isCallInProgress = managementState.isCallInProgress,
                        action = button,
                        onButtonClicked = onButtonClicked
                    )
                }

                val keyItemDescription = "Schedule meeting add description"
                item(key = keyItemDescription) {
                    if (state.isEditingDescription || state.descriptionText.isNotEmpty()) {
                        AddDescriptionButton(
                            description = state.descriptionText,
                            onValueChange = onDescriptionValueChange,
                            onSizeChange = {
                                coroutineScope.launch {
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keyItemDescription }?.index?.let { index ->
                                        listState.scrollToItem(index = index + 1)
                                    }
                                }
                            }
                        )
                    }
                }

                // Empty item to allow auto scroll to bottom when the description text field size changes.
                item(key = "Scroll to bottom") { }
            }
        }

        EventEffect(
            event = state.snackbarMessageContent, onConsumed = onResetSnackbarMessage
        ) {
            scaffoldState.snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    SnackbarHost(modifier = Modifier.padding(8.dp), hostState = snackbarHostState)

    onScrollChange(!firstItemVisible)
}

@Composable
private fun BackPressHandler(
    backPressedDispatcher: OnBackPressedDispatcher? =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
    onBackPressed: () -> Unit,
) {
    val currentOnBackPressed by rememberUpdatedState(newValue = onBackPressed)

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBackPressed()
            }
        }
    }

    DisposableEffect(key1 = backPressedDispatcher) {
        backPressedDispatcher?.addCallback(backCallback)

        onDispose {
            backCallback.remove()
        }
    }
}

/**
 * Scheduled meeting date and time
 *
 * @param state [CreateScheduledMeetingState]
 */
@Composable
private fun ScheduledMeetingDateAndTime(
    state: CreateScheduledMeetingState,
    isStart: Boolean,
    onDateClicked: () -> Unit,
    onTimeClicked: () -> Unit,
) {
    Box(
        Modifier
            .testTag(DATE_AND_TIME_TAG)
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp)
            .height(56.dp)
    ) {
        Text(
            text = dateFormatter.format(if (isStart) state.startDate else state.endDate),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onDateClicked() },
            style = MaterialTheme.typography.subtitle1.copy(
                color = MaterialTheme.colors.onPrimary
            ),
        )

        Text(
            text = timeFormatter.format(if (isStart) state.startDate else state.endDate),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { onTimeClicked() },
            style = MaterialTheme.typography.subtitle1.copy(
                color = MaterialTheme.colors.onPrimary
            ),
        )
    }
}

/**
 * Add description to the scheduled meeting.
 *
 * @param description           Description text
 * @param onValueChange         When description changes
 */
@Composable
private fun AddDescriptionButton(
    description: String,
    onValueChange: (String) -> Unit,
    onSizeChange: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 18.dp, end = 16.dp, top = 1.dp, bottom = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 17.dp, end = 36.dp)
                        .clip(RectangleShape)
                        .wrapContentSize(Alignment.TopCenter)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sched_meeting_description),
                        contentDescription = "${stringResource(id = R.string.meetings_schedule_meeting_add_description_label)} icon",
                        tint = MaterialTheme.colors.textColorSecondary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    GenericDescriptionTextField(
                        value = description.ifEmpty { "" },
                        charLimit = Constants.MAX_DESCRIPTION_SIZE,
                        onValueChange = { text ->
                            onValueChange(text)
                        },
                        initiallyFocused = description.isEmpty(),
                        placeholderId = R.string.meetings_schedule_meeting_add_description_label,
                        charLimitErrorId = R.string.meetings_schedule_meeting_meeting_description_too_long_error,
                        titleId = R.string.meetings_scheduled_meeting_info_scheduled_meeting_description_label,
                        onSizeChange = onSizeChange
                    )
                }
            }
        }

        CustomDivider(withStartPadding = false)
    }
}

@Composable
private fun ActionButton(
    state: CreateScheduledMeetingState,
    isCallInProgress: Boolean,
    action: ScheduleMeetingAction,
    onButtonClicked: (ScheduleMeetingAction) -> Unit = {},
) {
    Column(modifier = Modifier
        .testTag(ACTION_BUTTON_TAG)
        .fillMaxWidth()
        .clickable {
            if (action != ScheduleMeetingAction.WaitingRoom || !isCallInProgress) {
                onButtonClicked(action)
            }
        }) {
        if ((action != ScheduleMeetingAction.AddDescription || (!state.isEditingDescription && state.descriptionText.isEmpty())) && (action != ScheduleMeetingAction.EndRecurrence || state.rulesSelected.freq != OccurrenceFrequencyType.Invalid)) {
            ActionOption(
                state = state,
                action = action,
                isEnabled = action != ScheduleMeetingAction.WaitingRoom || !isCallInProgress,
                isChecked = when (action) {
                    ScheduleMeetingAction.MeetingLink -> state.enabledMeetingLinkOption
                    ScheduleMeetingAction.AllowNonHostAddParticipants -> state.enabledAllowAddParticipantsOption
                    ScheduleMeetingAction.SendCalendarInvite -> state.enabledSendCalendarInviteOption
                    ScheduleMeetingAction.WaitingRoom -> state.enabledWaitingRoomOption
                    else -> true
                },
                hasSwitch = when (action) {
                    ScheduleMeetingAction.Recurrence,
                    ScheduleMeetingAction.EndRecurrence,
                    ScheduleMeetingAction.AddParticipants,
                    ScheduleMeetingAction.AddDescription,
                    -> false

                    else -> true
                }
            )

            CustomDivider(
                withStartPadding = when (action) {
                    ScheduleMeetingAction.Recurrence,
                    ScheduleMeetingAction.EndRecurrence,
                    ScheduleMeetingAction.AddParticipants,
                    ScheduleMeetingAction.SendCalendarInvite,
                    ScheduleMeetingAction.WaitingRoom,
                    -> true

                    else -> false
                }
            )

            if (action == ScheduleMeetingAction.Recurrence && state.showMonthlyRecurrenceWarning) {
                state.rulesSelected.monthDayList?.let { list ->
                    list.first().let { day ->
                        Text(
                            modifier = Modifier.padding(
                                start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp
                            ),
                            style = MaterialTheme.typography.subtitle2.copy(
                                color = MaterialTheme.colors.textColorSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            text = pluralStringResource(
                                R.plurals.meetings_schedule_meeting_recurrence_monthly_description,
                                day,
                                day
                            ),
                        )
                        CustomDivider(withStartPadding = true)
                    }
                }
            }
        }
    }
}

/**
 * Schedule meeting App bar view
 *
 * @param state                     [CreateScheduledMeetingState]
 * @param onAcceptClicked           When on accept scheduled meeting is clicked
 * @param onDiscardClicked          When on discard is clicked
 * @param onValueChange             When title changes
 * @param elevation                 True if it has elevation. False, if it does not.
 */
@Composable
private fun ScheduleMeetingAppBar(
    state: CreateScheduledMeetingState,
    onAcceptClicked: () -> Unit,
    onDiscardClicked: () -> Unit,
    onValueChange: (String) -> Unit,
    elevation: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = if (elevation) AppBarDefaults.TopAppBarElevation else 0.dp,
        color = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    IconButton(onClick = onDiscardClicked) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_close),
                            contentDescription = "Cancel schedule meeting button",
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.CenterEnd)
                ) {
                    IconButton(onClick = {
                        if (!state.isCreatingMeeting && state.isValid()) {
                            onAcceptClicked()
                        }
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_confirm),
                            contentDescription = "Accept schedule meeting button",
                            tint = if (state.isValid()) MaterialTheme.colors.secondary
                            else MaterialTheme.colors.grey_alpha_038_white_alpha_038
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 15.dp, bottom = 20.dp)
            ) {
                GenericTitleTextField(
                    value = state.meetingTitle.ifEmpty { "" },
                    isEmptyValueError = state.isEmptyTitleError,
                    placeholderId = R.string.meetings_schedule_meeting_name_hint,
                    shouldInitialFocus = state.type == ScheduledMeetingType.Creation,
                    onValueChange = { text ->
                        onValueChange(text)
                    },
                    charLimitErrorId = R.string.meetings_schedule_meeting_meeting_name_too_long_error,
                    emptyValueErrorId = R.string.meetings_schedule_meeting_empty_meeting_name_error,
                    charLimit = Constants.MAX_TITLE_SIZE
                )
            }
        }
    }
}

/**
 * Show action buttons options
 *
 * @param state         [CreateScheduledMeetingState]
 * @param action        [ScheduleMeetingAction]
 * @param isChecked     True, if the option must be checked. False if not
 * @param isEnabled     True, if the option must be enabled. False if not
 * @param hasSwitch     True, if the option has a switch. False if not
 */
@Composable
private fun ActionOption(
    state: CreateScheduledMeetingState,
    action: ScheduleMeetingAction,
    isChecked: Boolean,
    isEnabled: Boolean,
    hasSwitch: Boolean,
) {
    Row(
        verticalAlignment = if (action == ScheduleMeetingAction.MeetingLink) Alignment.Top else Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
        ) {
            action.icon?.let { icon ->
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RectangleShape)
                        .wrapContentSize(Alignment.TopCenter)
                ) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = "${action.name} icon",
                        tint = MaterialTheme.colors.textColorSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Text(
                    modifier = Modifier
                        .padding(
                            if (action == ScheduleMeetingAction.EndRecurrence) 54.dp else 32.dp,
                            end = 15.dp
                        ),
                    style = MaterialTheme.typography.subtitle1.copy(
                        color = MaterialTheme.colors.onPrimary
                    ),
                    text = stringResource(id = action.title),
                )

                var subtitle: String? = null

                when (action) {
                    ScheduleMeetingAction.MeetingLink,
                    ScheduleMeetingAction.SendCalendarInvite,
                    ScheduleMeetingAction.WaitingRoom,
                    -> {
                        action.description?.let { description ->
                            subtitle = stringResource(id = description)
                        }
                    }

                    ScheduleMeetingAction.AddParticipants -> {
                        if (state.numOfParticipants > 1) subtitle =
                            stringResource(
                                id = R.string.number_of_participants,
                                state.numOfParticipants
                            )
                    }

                    ScheduleMeetingAction.Recurrence -> {
                        subtitle = getScheduledMeetingFrequencyText(
                            state.rulesSelected,
                            state.isWeekdays(),
                            state.getStartWeekDay(),
                            state.getStartMonthDay()
                        )
                    }

                    ScheduleMeetingAction.EndRecurrence -> {
                        val untilZonedDateTime = state.rulesSelected.getUntilZonedDateTime()
                        subtitle = when (untilZonedDateTime) {
                            null -> stringResource(id = R.string.meetings_schedule_meeting_recurrence_never_label)
                            else -> getScheduledMeetingEndRecurrenceText(untilZonedDateTime)
                        }
                    }

                    else -> {}
                }

                subtitle?.let {
                    Text(
                        modifier = Modifier
                            .padding(
                                start = if (action == ScheduleMeetingAction.EndRecurrence) 54.dp else 32.dp,
                                top = if (action == ScheduleMeetingAction.MeetingLink || action == ScheduleMeetingAction.SendCalendarInvite) 10.dp else if (action == ScheduleMeetingAction.WaitingRoom) 18.dp else 0.dp,
                                end = 15.dp
                            ),
                        style = MaterialTheme.typography.body2.copy(
                            color = MaterialTheme.colors.textColorSecondary
                        ),
                        text = it,
                    )
                }
            }
        }

        if (hasSwitch) {
            Box(
                modifier = Modifier
                    .wrapContentSize(if (action == ScheduleMeetingAction.MeetingLink) Alignment.TopEnd else Alignment.Center)
            ) {
                MegaSwitch(
                    checked = isChecked,
                    enabled = isEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Dialogue to discard the meeting
 *
 * @param state                     [CreateScheduledMeetingState]
 * @param onKeepEditing             When continue editing the meeting.
 * @param onDiscard                 When discard the meeting.
 */
@Composable
private fun DiscardMeetingAlertDialog(
    state: CreateScheduledMeetingState,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (state.discardMeetingDialog) {
        MegaAlertDialog(
            text = stringResource(id = R.string.meetings_schedule_meeting_discard_meeting_dialog_title),
            confirmButtonText = stringResource(id = R.string.meetings_schedule_meeting_discard_meeting_dialog_discard_option),
            cancelButtonText = stringResource(id = R.string.meetings_schedule_meeting_discard_meeting_dialog_keep_editing_option),
            onConfirm = onDiscard,
            onDismiss = onKeepEditing,
        )
    }
}

/**
 * Dialogue to recurring meeting
 *
 * @param state                     [CreateScheduledMeetingState]
 * @param onOptionSelected          When continue editing the meeting.
 * @param onDiscard                 When discard the meeting.
 */
@Composable
private fun RecurringMeetingDialog(
    state: CreateScheduledMeetingState,
    onOptionSelected: (RecurrenceDialogOption) -> Unit,
    onDiscard: () -> Unit,
) {
    if (state.recurringMeetingDialog) {
        ConfirmationWithRadioButtonsDialog(
            titleText = stringResource(id = R.string.meetings_schedule_meeting_recurrence_label),
            buttonText = stringResource(id = R.string.general_cancel),
            radioOptions = state.getRecurrenceDialogOptionList(),
            initialSelectedOption = state.getRecurrenceDialogOptionSelected(),
            onOptionSelected = onOptionSelected,
            onDismissRequest = onDiscard,
            optionDescriptionMapper = { option ->
                when (option) {
                    RecurrenceDialogOption.Never -> stringResource(id = RecurrenceDialogOption.Never.StringId)
                    RecurrenceDialogOption.EveryDay -> stringResource(id = RecurrenceDialogOption.EveryDay.StringId)
                    RecurrenceDialogOption.EveryWeek -> stringResource(id = RecurrenceDialogOption.EveryWeek.StringId)
                    RecurrenceDialogOption.EveryMonth -> stringResource(id = RecurrenceDialogOption.EveryMonth.StringId)
                    RecurrenceDialogOption.Custom -> stringResource(id = RecurrenceDialogOption.Custom.StringId)
                    RecurrenceDialogOption.Customised -> getScheduledMeetingFrequencyText(
                        rules = state.rulesSelected,
                        state.isWeekdays(),
                        state.getStartWeekDay(),
                        state.getStartMonthDay(),
                        true
                    )
                }
            }
        )
    }
}

internal const val DATE_AND_TIME_TAG = "create_scheduled_meeting:date_and_time"
internal const val ACTION_BUTTON_TAG = "create_scheduled_meeting:action_button"

/**
 * Discard Meeting Alert Dialog Preview
 */
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DarkPreviewDiscardMeetingAlertDialog")
@Composable
fun PreviewDiscardMeetingAlertDialog() {
    AndroidTheme(isDark = isSystemInDarkTheme()) {
        DiscardMeetingAlertDialog(state = CreateScheduledMeetingState(
            meetingTitle = "Title meeting",
            rulesSelected = ChatScheduledRules(),
            participantItemList = emptyList(),
            buttons = ScheduleMeetingAction.values().asList(),
            snackbarMessageContent = consumed(),
            discardMeetingDialog = true,
        ),
            onKeepEditing = {},
            onDiscard = {})
    }
}

/**
 * Recurring Meeting Dialog Preview
 */
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DarkPreviewRecurringMeetingDialog")
@Composable
fun PreviewRecurringMeetingDialog() {
    AndroidTheme(isDark = isSystemInDarkTheme()) {
        RecurringMeetingDialog(state = CreateScheduledMeetingState(
            meetingTitle = "Title meeting",
            rulesSelected = ChatScheduledRules(),
            participantItemList = emptyList(),
            buttons = ScheduleMeetingAction.values().asList(),
            snackbarMessageContent = consumed(),
            discardMeetingDialog = true,
        ),
            onOptionSelected = {},
            onDiscard = {})
    }
}

/**
 * Create scheduled meeting View Preview
 */
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DarkPreviewCreateScheduledMeetingView")
@Composable
private fun PreviewCreateScheduledMeetingView() {
    AndroidTheme(isDark = isSystemInDarkTheme()) {
        CreateScheduledMeetingView(
            state = CreateScheduledMeetingState(
                meetingTitle = "Title meeting",
                rulesSelected = ChatScheduledRules(),
                participantItemList = emptyList(),
                buttons = ScheduleMeetingAction.values().asList(),
                snackbarMessageContent = consumed()
            ),
            managementState = ScheduledMeetingManagementState(),
            onButtonClicked = {},
            onDiscardClicked = {},
            onAcceptClicked = {},
            onStartTimeClicked = {},
            onStartDateClicked = {},
            onEndTimeClicked = {},
            onEndDateClicked = {},
            onScrollChange = {},
            onDismiss = {},
            onResetSnackbarMessage = {},
            onDiscardMeetingDialog = {},
            onDescriptionValueChange = {},
            onTitleValueChange = {},
            onRecurrenceDialogOptionClicked = {},
            onCloseWarningClicked = {},
        )
    }
}