package mega.privacy.android.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mega.privacy.android.domain.entity.ChatRoomLastMessage
import mega.privacy.android.domain.entity.chat.ChatListItemChanges
import mega.privacy.android.domain.entity.chat.CombinedChatRoom
import mega.privacy.android.domain.entity.chat.MeetingRoomItem
import mega.privacy.android.domain.repository.ChatRepository
import mega.privacy.android.domain.repository.GetMeetingsRepository
import javax.inject.Inject

/**
 * Default get meetings use case implementation.
 */
class DefaultGetMeetings @Inject constructor(
    private val chatRepository: ChatRepository,
    private val getMeetingsRepository: GetMeetingsRepository,
    private val meetingRoomMapper: MeetingRoomMapper,
) : GetMeetings {

    override fun invoke(mutex: Mutex): Flow<List<MeetingRoomItem>> =
        flow {
            val meetings = mutableListOf<MeetingRoomItem>()

            meetings.addChatRooms(mutex)
            emit(meetings)

            emitAll(
                merge(
                    meetings.updateFields(mutex),
                    meetings.addScheduledMeetings(mutex),
                    meetings.monitorMutedChats(mutex),
                    meetings.monitorChatCalls(mutex),
                    meetings.monitorChatItems(mutex),
                    meetings.monitorScheduledMeetings(mutex)
                )
            )
        }

    private suspend fun MutableList<MeetingRoomItem>.addChatRooms(mutex: Mutex) {
        chatRepository.getMeetingChatRooms()?.forEach { chatRoom ->
            if (!chatRoom.isArchived) {
                add(chatRoom.toMeetingRoomItem())
            }
        }
        sortMeetings(mutex)
    }

    private suspend fun MutableList<MeetingRoomItem>.updateFields(mutex: Mutex): Flow<MutableList<MeetingRoomItem>> =
        getMeetingsRepository.getUpdatedMeetingItems(this, mutex)

    private suspend fun MutableList<MeetingRoomItem>.addScheduledMeetings(mutex: Mutex): Flow<MutableList<MeetingRoomItem>> =
        flow {
            val iterator = listIterator()
            var hasNext: Boolean
            do {
                mutex.withLock {
                    hasNext = iterator.hasNext()
                    if (!hasNext) return@withLock

                    val item = iterator.next()
                    val updatedItem = getScheduledMeetingItem(item)
                    if (updatedItem != null) {
                        iterator.set(updatedItem)
                        emit(this@addScheduledMeetings)
                    }
                    hasNext = iterator.hasNext()
                }
            } while (hasNext)

            sortMeetings(mutex)
            emit(this@addScheduledMeetings)
        }

    private suspend fun MutableList<MeetingRoomItem>.monitorMutedChats(mutex: Mutex): Flow<MutableList<MeetingRoomItem>> =
        chatRepository.monitorMutedChats()
            .map {
                apply {
                    val existingIndex = indexOfFirst { it.isMuted != !chatRepository.isChatNotifiable(it.chatId) }
                    if (existingIndex != -1) {
                        val existingItem = get(existingIndex)
                        val updatedItem = existingItem.copy(
                            isMuted = !existingItem.isMuted,
                        )
                        mutex.withLock { set(existingIndex, updatedItem) }
                    }
                }
            }

    private suspend fun MutableList<MeetingRoomItem>.monitorChatCalls(mutex: Mutex): Flow<MutableList<MeetingRoomItem>> =
        chatRepository.monitorChatCallUpdates()
            .filter { any { meeting -> meeting.chatId == it.chatId } }
            .map { chatCall ->
                apply {
                    val chatRoom = chatRepository.getCombinedChatRoom(chatCall.chatId) ?: return@apply
                    val currentItemIndex = indexOfFirst { it.chatId == chatCall.chatId }
                    val currentItem = get(currentItemIndex)
                    val updatedItem = currentItem.copy(
                        highlight = chatRoom.unreadCount > 0 || chatRoom.isCallInProgress
                                || chatRoom.lastMessageType == ChatRoomLastMessage.CallStarted,
                        lastTimestamp = chatRoom.lastTimestamp
                    )

                    if (currentItem != updatedItem) {
                        mutex.withLock { set(currentItemIndex, updatedItem) }
                        sortMeetings(mutex)
                    }
                }
            }

    private suspend fun MutableList<MeetingRoomItem>.monitorChatItems(mutex: Mutex): Flow<MutableList<MeetingRoomItem>> =
        chatRepository.monitorChatListItemUpdates()
            .map { chatListItem ->
                apply {
                    val currentItemIndex = indexOfFirst { it.chatId == chatListItem.chatId }

                    if (chatListItem.isArchived ||
                        chatListItem.isDeleted ||
                        chatListItem.changes == ChatListItemChanges.Deleted ||
                        chatListItem.changes == ChatListItemChanges.Closed
                    ) {
                        if (currentItemIndex != -1) {
                            mutex.withLock { removeAt(currentItemIndex) }
                        }
                        return@apply
                    }

                    val newItem = chatRepository.getCombinedChatRoom(chatListItem.chatId)
                        ?.takeIf(CombinedChatRoom::isMeeting)
                        ?.toMeetingRoomItem()
                        ?.let { getMeetingsRepository.getUpdatedMeetingItem(it) }
                        ?: return@apply

                    val newUpdatedItem = getScheduledMeetingItem(newItem) ?: newItem
                    if (currentItemIndex != -1) {
                        val currentItem = get(currentItemIndex)
                        if (currentItem != newUpdatedItem) {
                            mutex.withLock { set(currentItemIndex, newUpdatedItem) }
                            sortMeetings(mutex)
                        }
                    } else {
                        mutex.withLock { add(newUpdatedItem) }
                        sortMeetings(mutex)
                    }
                }
            }

    private suspend fun MutableList<MeetingRoomItem>.monitorScheduledMeetings(mutex: Mutex): Flow<MutableList<MeetingRoomItem>> =
        chatRepository.monitorScheduledMeetingsUpdates()
            .filter { any { meeting -> meeting.chatId == it.chatId } }
            .map { scheduledMeeting ->
                apply {
                    val currentItemIndex = indexOfFirst { it.chatId == scheduledMeeting.chatId }
                    val currentItem = get(currentItemIndex)
                    val isPending = currentItem.isActive && scheduledMeeting.isPending()
                    val updatedItem = currentItem.copy(
                        schedId = scheduledMeeting.schedId,
                        scheduledStartTimestamp = scheduledMeeting.startDateTime,
                        scheduledEndTimestamp = scheduledMeeting.endDateTime,
                        isRecurring = currentItem.isRecurring,
                        isPending = isPending,
                    )
                    if (currentItem != updatedItem) {
                        mutex.withLock { set(currentItemIndex, updatedItem) }
                        sortMeetings(mutex)
                    }
                }
            }

    private suspend fun CombinedChatRoom.toMeetingRoomItem(): MeetingRoomItem =
        meetingRoomMapper.invoke(this,
            chatRepository::isChatNotifiable,
            chatRepository::isChatLastMessageGeolocation
        )

    private suspend fun getScheduledMeetingItem(item: MeetingRoomItem): MeetingRoomItem? =
        chatRepository.getScheduledMeetingsByChat(item.chatId)?.let { schedMeetings ->
            if (schedMeetings.isEmpty()) return null
            val parentSchedMeeting = schedMeetings.first()
            val schedMeeting = schedMeetings.firstOrNull { it.isPending() } ?: parentSchedMeeting

            item.copy(
                schedId = parentSchedMeeting.schedId,
                scheduledStartTimestamp = schedMeeting.startDateTime,
                scheduledEndTimestamp = schedMeeting.endDateTime,
                isRecurring = parentSchedMeeting.rules != null,
                isPending = item.isActive && schedMeeting.isPending(),
            )
        }

    private suspend fun MutableList<MeetingRoomItem>.sortMeetings(mutex: Mutex) {
        mutex.withLock {
            sortWith(
                compareByDescending(MeetingRoomItem::isPending)
                    .thenComparing { firstMeeting, secondMeeting ->
                        when {
                            firstMeeting.isPending && !secondMeeting.isPending -> 1
                            firstMeeting.isPending && secondMeeting.isPending -> {
                                when {
                                    firstMeeting.scheduledStartTimestamp!! > secondMeeting.scheduledStartTimestamp!! -> 1
                                    firstMeeting.scheduledStartTimestamp < secondMeeting.scheduledStartTimestamp -> -1
                                    else -> 0
                                }
                            }
                            !firstMeeting.isPending && !secondMeeting.isPending -> {
                                when {
                                    firstMeeting.highlight && !secondMeeting.highlight -> -1
                                    !firstMeeting.highlight && secondMeeting.highlight -> 1
                                    firstMeeting.lastTimestamp > secondMeeting.lastTimestamp -> -1
                                    firstMeeting.lastTimestamp < secondMeeting.lastTimestamp -> 1
                                    else -> 0
                                }
                            }
                            else -> 0
                        }
                    }
            )
        }
    }
}
