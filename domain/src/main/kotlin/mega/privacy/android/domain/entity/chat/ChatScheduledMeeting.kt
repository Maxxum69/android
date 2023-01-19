package mega.privacy.android.domain.entity.chat

/**
 * Chat scheduled meeting
 *
 * @property chatId
 * @property schedId
 * @property parentSchedId
 * @property organizerUserId
 * @property timezone
 * @property startDateTime
 * @property endDateTime
 * @property title
 * @property description
 * @property attributes
 * @property overrides
 * @property flags
 * @property rules
 * @property changes            Changes [ScheduledMeetingChanges].
 */
data class ChatScheduledMeeting constructor(
    val chatId: Long,
    val schedId: Long,
    val parentSchedId: Long?,
    val organizerUserId: Long?,
    val timezone: String? = null,
    val startDateTime: Long? = null,
    val endDateTime: Long? = null,
    val title: String? = "",
    val description: String? = "",
    val attributes: String?,
    val overrides: Long? = null,
    val flags: ChatScheduledFlags? = null,
    val rules: ChatScheduledRules? = null,
    val changes: ScheduledMeetingChanges? = null,
)
