package test.mega.privacy.android.app.presentation.meeting.chat.view.message.link

import com.google.common.truth.Truth
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.presentation.meeting.chat.view.message.link.ChatGroupLinkContent
import mega.privacy.android.app.presentation.meeting.chat.view.message.link.ChatLinksMessageViewModel
import mega.privacy.android.app.presentation.meeting.chat.view.message.link.ContactLinkContent
import mega.privacy.android.app.presentation.meeting.chat.view.message.link.FileLinkContent
import mega.privacy.android.app.presentation.meeting.chat.view.message.link.FolderLinkContent
import mega.privacy.android.core.test.extension.CoroutineMainDispatcherExtension
import mega.privacy.android.domain.entity.ChatRequest
import mega.privacy.android.domain.entity.FolderInfo
import mega.privacy.android.domain.entity.contacts.ContactLink
import mega.privacy.android.domain.entity.node.TypedFileNode
import mega.privacy.android.domain.usecase.CheckChatLinkUseCase
import mega.privacy.android.domain.usecase.contact.GetContactFromLinkUseCase
import mega.privacy.android.domain.usecase.filelink.GetPublicLinkInformationUseCase
import mega.privacy.android.domain.usecase.filelink.GetPublicNodeUseCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(CoroutineMainDispatcherExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ChatLinksMessageViewModelTest {
    private lateinit var underTest: ChatLinksMessageViewModel
    private val getContactFromLinkUseCase: GetContactFromLinkUseCase = mock()
    private val checkChatLinkUseCase: CheckChatLinkUseCase = mock()
    private val getPublicLinkInformationUseCase: GetPublicLinkInformationUseCase = mock()
    private val getPublicNodeUseCase: GetPublicNodeUseCase = mock()

    @BeforeEach
    fun resetMocks() {
        underTest = ChatLinksMessageViewModel(
            getContactFromLinkUseCase,
            checkChatLinkUseCase,
            getPublicLinkInformationUseCase,
            getPublicNodeUseCase
        )
    }

    @Test
    fun `test that load contact info return correctly`() = runTest {
        val link = "link"
        val contactLink = mock<ContactLink>()
        whenever(getContactFromLinkUseCase(link)).thenReturn(contactLink)
        underTest.loadContactInfo(link)
        // make sure it is called only once because the other call we load from cache
        verify(getContactFromLinkUseCase).invoke(link)
        Truth.assertThat(underTest.loadContactInfo(link))
            .isEqualTo(ContactLinkContent(contactLink, link))
    }

    @Test
    fun `test that load chat link info return correctly when link is valid`() = runTest {
        val link = "link"
        val numberOfParticipants = 1L
        val name = "name"
        val chatId = 12L
        val request = mock<ChatRequest> {
            on { number } doReturn numberOfParticipants
            on { text } doReturn name
            on { chatHandle } doReturn chatId
        }
        whenever(checkChatLinkUseCase(link)).thenReturn(request)
        Truth.assertThat(underTest.loadChatLinkInfo(link))
            .isEqualTo(
                ChatGroupLinkContent(
                    numberOfParticipants,
                    name,
                    chatId,
                    link,
                )
            )
    }

    @Test
    fun `test that load chat link info return correctly when link is invalid`() = runTest {
        val link = "link"
        whenever(checkChatLinkUseCase(link)).thenThrow(IllegalStateException())
        Truth.assertThat(underTest.loadChatLinkInfo(link))
            .isEqualTo(
                ChatGroupLinkContent(
                    numberOfParticipants = -1,
                    name = "",
                    link = link,
                    chatId = -1,
                )
            )
    }

    @Test
    fun `test that load folder link info return correctly`() = runTest {
        val link = "link"
        val folderInfo = mock<FolderInfo>()
        whenever(getPublicLinkInformationUseCase(link)).thenReturn(folderInfo)
        Truth.assertThat(underTest.loadFolderLinkInfo(link))
            .isEqualTo(
                FolderLinkContent(
                    folderInfo,
                    link
                )
            )
        underTest.loadFolderLinkInfo(link)
        // make sure it is called only once because the other call we load from cache
        verify(getPublicLinkInformationUseCase).invoke(link)
    }

    @Test
    fun `test that load file link info return correctly`() = runTest {
        val link = "link"
        val fileNode = mock<TypedFileNode>()
        whenever(getPublicNodeUseCase(link)).thenReturn(fileNode)
        Truth.assertThat(underTest.loadFileLinkInfo(link))
            .isEqualTo(
                FileLinkContent(
                    fileNode,
                    link
                )
            )
        underTest.loadFileLinkInfo(link)
        // make sure it is called only once because the other call we load from cache
        verify(getPublicNodeUseCase).invoke(link)
    }
}