package test.mega.privacy.android.app.presentation.transfers.startdownload

import android.net.Uri
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import de.palm.composestateevents.StateEventWithContentTriggered
import de.palm.composestateevents.triggered
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.presentation.mapper.file.FileSizeStringMapper
import mega.privacy.android.app.presentation.transfers.TransfersConstants
import mega.privacy.android.app.presentation.transfers.starttransfer.StartTransfersComponentViewModel
import mega.privacy.android.app.presentation.transfers.starttransfer.model.StartTransferEvent
import mega.privacy.android.app.presentation.transfers.starttransfer.model.StartTransferJobInProgress
import mega.privacy.android.app.presentation.transfers.starttransfer.model.TransferTriggerEvent
import mega.privacy.android.core.test.extension.CoroutineMainDispatcherExtension
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.entity.node.TypedFileNode
import mega.privacy.android.domain.entity.node.TypedFolderNode
import mega.privacy.android.domain.entity.transfer.MultiTransferEvent
import mega.privacy.android.domain.usecase.SetStorageDownloadAskAlwaysUseCase
import mega.privacy.android.domain.usecase.SetStorageDownloadLocationUseCase
import mega.privacy.android.domain.usecase.chat.message.SendChatAttachmentsUseCase
import mega.privacy.android.domain.usecase.file.TotalFileSizeOfNodesUseCase
import mega.privacy.android.domain.usecase.network.IsConnectedToInternetUseCase
import mega.privacy.android.domain.usecase.node.GetFilePreviewDownloadPathUseCase
import mega.privacy.android.domain.usecase.offline.GetOfflinePathForNodeUseCase
import mega.privacy.android.domain.usecase.setting.IsAskBeforeLargeDownloadsSettingUseCase
import mega.privacy.android.domain.usecase.setting.SetAskBeforeLargeDownloadsSettingUseCase
import mega.privacy.android.domain.usecase.transfers.active.ClearActiveTransfersIfFinishedUseCase
import mega.privacy.android.domain.usecase.transfers.active.MonitorActiveTransferFinishedUseCase
import mega.privacy.android.domain.usecase.transfers.active.MonitorOngoingActiveTransfersUseCase
import mega.privacy.android.domain.usecase.transfers.chatuploads.SetAskedResumeTransfersUseCase
import mega.privacy.android.domain.usecase.transfers.chatuploads.ShouldAskForResumeTransfersUseCase
import mega.privacy.android.domain.usecase.transfers.downloads.GetCurrentDownloadSpeedUseCase
import mega.privacy.android.domain.usecase.transfers.downloads.GetOrCreateStorageDownloadLocationUseCase
import mega.privacy.android.domain.usecase.transfers.downloads.SaveDoNotPromptToSaveDestinationUseCase
import mega.privacy.android.domain.usecase.transfers.downloads.ShouldAskDownloadDestinationUseCase
import mega.privacy.android.domain.usecase.transfers.downloads.ShouldPromptToSaveDestinationUseCase
import mega.privacy.android.domain.usecase.transfers.downloads.StartDownloadsWithWorkerUseCase
import mega.privacy.android.domain.usecase.transfers.paused.PauseAllTransfersUseCase
import mega.privacy.android.domain.usecase.transfers.uploads.StartUploadsWithWorkerUseCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(CoroutineMainDispatcherExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StartTransfersComponentViewModelTest {

    private lateinit var underTest: StartTransfersComponentViewModel

    private val getOfflinePathForNodeUseCase: GetOfflinePathForNodeUseCase = mock()
    private val startDownloadsWithWorkerUseCase: StartDownloadsWithWorkerUseCase = mock()
    private val isConnectedToInternetUseCase: IsConnectedToInternetUseCase = mock()
    private val clearActiveTransfersIfFinishedUseCase =
        mock<ClearActiveTransfersIfFinishedUseCase>()
    private val totalFileSizeOfNodesUseCase = mock<TotalFileSizeOfNodesUseCase>()
    private val fileSizeStringMapper = mock<FileSizeStringMapper>()
    private val isAskBeforeLargeDownloadsSettingUseCase =
        mock<IsAskBeforeLargeDownloadsSettingUseCase>()
    private val setAskBeforeLargeDownloadsSettingUseCase =
        mock<SetAskBeforeLargeDownloadsSettingUseCase>()
    private val getOrCreateStorageDownloadLocationUseCase =
        mock<GetOrCreateStorageDownloadLocationUseCase>()
    private val monitorOngoingActiveTransfersUseCase = mock<MonitorOngoingActiveTransfersUseCase>()
    private val getCurrentDownloadSpeedUseCase = mock<GetCurrentDownloadSpeedUseCase>()
    private val getFilePreviewDownloadPathUseCase = mock<GetFilePreviewDownloadPathUseCase>()
    private val shouldAskDownloadDestinationUseCase = mock<ShouldAskDownloadDestinationUseCase>()
    private val shouldPromptToSaveDestinationUseCase = mock<ShouldPromptToSaveDestinationUseCase>()
    private val saveDoNotPromptToSaveDestinationUseCase =
        mock<SaveDoNotPromptToSaveDestinationUseCase>()
    private val setStorageDownloadAskAlwaysUseCase = mock<SetStorageDownloadAskAlwaysUseCase>()
    private val setStorageDownloadLocationUseCase = mock<SetStorageDownloadLocationUseCase>()
    private val monitorActiveTransferFinishedUseCase = mock<MonitorActiveTransferFinishedUseCase>()
    private val sendChatAttachmentsUseCase = mock<SendChatAttachmentsUseCase>()
    private val shouldAskForResumeTransfersUseCase = mock<ShouldAskForResumeTransfersUseCase>()
    private val setAskedResumeTransfersUseCase = mock<SetAskedResumeTransfersUseCase>()
    private val pauseAllTransfersUseCase = mock<PauseAllTransfersUseCase>()
    private val startUploadWithWorkerUseCase = mock<StartUploadsWithWorkerUseCase>()

    private val node: TypedFileNode = mock()
    private val nodes = listOf(node)
    private val parentNode: TypedFolderNode = mock()
    private val startDownloadEvent = TransferTriggerEvent.StartDownloadNode(nodes)

    @BeforeAll
    fun setup() {
        underTest = StartTransfersComponentViewModel(
            getOfflinePathForNodeUseCase = getOfflinePathForNodeUseCase,
            getOrCreateStorageDownloadLocationUseCase = getOrCreateStorageDownloadLocationUseCase,
            getFilePreviewDownloadPathUseCase = getFilePreviewDownloadPathUseCase,
            startDownloadsWithWorkerUseCase = startDownloadsWithWorkerUseCase,
            clearActiveTransfersIfFinishedUseCase = clearActiveTransfersIfFinishedUseCase,
            isConnectedToInternetUseCase = isConnectedToInternetUseCase,
            totalFileSizeOfNodesUseCase = totalFileSizeOfNodesUseCase,
            fileSizeStringMapper = fileSizeStringMapper,
            isAskBeforeLargeDownloadsSettingUseCase = isAskBeforeLargeDownloadsSettingUseCase,
            setAskBeforeLargeDownloadsSettingUseCase = setAskBeforeLargeDownloadsSettingUseCase,
            monitorOngoingActiveTransfersUseCase = monitorOngoingActiveTransfersUseCase,
            getCurrentDownloadSpeedUseCase = getCurrentDownloadSpeedUseCase,
            shouldAskDownloadDestinationUseCase = shouldAskDownloadDestinationUseCase,
            shouldPromptToSaveDestinationUseCase = shouldPromptToSaveDestinationUseCase,
            saveDoNotPromptToSaveDestinationUseCase = saveDoNotPromptToSaveDestinationUseCase,
            setStorageDownloadAskAlwaysUseCase = setStorageDownloadAskAlwaysUseCase,
            setStorageDownloadLocationUseCase = setStorageDownloadLocationUseCase,
            monitorActiveTransferFinishedUseCase = monitorActiveTransferFinishedUseCase,
            sendChatAttachmentsUseCase = sendChatAttachmentsUseCase,
            shouldAskForResumeTransfersUseCase = shouldAskForResumeTransfersUseCase,
            setAskedResumeTransfersUseCase = setAskedResumeTransfersUseCase,
            pauseAllTransfersUseCase = pauseAllTransfersUseCase,
            startUploadWithWorkerUseCase = startUploadWithWorkerUseCase,
        )
    }

    @BeforeEach
    fun resetMocks() {
        reset(
            getOfflinePathForNodeUseCase,
            getOrCreateStorageDownloadLocationUseCase,
            getFilePreviewDownloadPathUseCase,
            clearActiveTransfersIfFinishedUseCase,
            startDownloadsWithWorkerUseCase,
            isConnectedToInternetUseCase,
            totalFileSizeOfNodesUseCase,
            fileSizeStringMapper,
            isAskBeforeLargeDownloadsSettingUseCase,
            setAskBeforeLargeDownloadsSettingUseCase,
            monitorOngoingActiveTransfersUseCase,
            getCurrentDownloadSpeedUseCase,
            shouldAskDownloadDestinationUseCase,
            shouldPromptToSaveDestinationUseCase,
            saveDoNotPromptToSaveDestinationUseCase,
            setStorageDownloadAskAlwaysUseCase,
            setStorageDownloadLocationUseCase,
            monitorActiveTransferFinishedUseCase,
            sendChatAttachmentsUseCase,
            shouldAskForResumeTransfersUseCase,
            setAskedResumeTransfersUseCase,
            pauseAllTransfersUseCase,
            startUploadWithWorkerUseCase,
            node,
            parentNode,
        )
        initialStub()
    }

    private fun initialStub() {
        whenever(monitorOngoingActiveTransfersUseCase(any())).thenReturn(emptyFlow())
        whenever(monitorActiveTransferFinishedUseCase(any())).thenReturn(MutableSharedFlow())
        whenever(startDownloadsWithWorkerUseCase(any(), any(), any())).thenReturn(emptyFlow())
        whenever(
            startUploadWithWorkerUseCase(
                mapOf(uploadUri.toString() to null),
                parentId
            )
        ).thenReturn(emptyFlow())
    }

    @ParameterizedTest
    @MethodSource("provideStartEvents")
    fun `test that clearActiveTransfersIfFinishedUseCase is invoked when startTransfer is invoked`(
        startEvent: TransferTriggerEvent,
    ) = runTest {
        commonStub()
        underTest.startTransfer(startEvent)
        verify(clearActiveTransfersIfFinishedUseCase).invoke(startEvent.type)
    }

    @ParameterizedTest
    @MethodSource("provideStartDownloadEvents")
    fun `test that start download use case is invoked with correct parameters when download is started`(
        startEvent: TransferTriggerEvent.DownloadTriggerEvent,
    ) = runTest {
        commonStub()
        if (startEvent is TransferTriggerEvent.StartDownloadForOffline) {
            whenever(getOfflinePathForNodeUseCase(any())).thenReturn(DESTINATION)
        } else if (startEvent is TransferTriggerEvent.StartDownloadForPreview) {
            whenever(getFilePreviewDownloadPathUseCase()).thenReturn(DESTINATION)
        }
        underTest.startTransfer(startEvent)
        verify(startDownloadsWithWorkerUseCase).invoke(
            nodes,
            DESTINATION,
            startEvent.isHighPriority
        )
    }

    @ParameterizedTest
    @MethodSource("provideStartChatUploadEvents")
    fun `test that send chat attachments use case is invoked with correct parameters when chat upload is started`(
        startEvent: TransferTriggerEvent.StartChatUpload,
    ) = runTest {
        commonStub()
        underTest.startTransfer(startEvent)
        verify(sendChatAttachmentsUseCase).invoke(
            listOf(uploadUri.toString()).associateWith { null },
            false,
            CHAT_ID,
        )
    }

    @Test
    fun `test that no connection event is emitted when monitorConnectivityUseCase is false and start a download`() =
        runTest {
            commonStub()
            whenever(isConnectedToInternetUseCase()).thenReturn(false)
            underTest.startTransfer(TransferTriggerEvent.StartDownloadNode(nodes))
            assertCurrentEventIsEqualTo(StartTransferEvent.NotConnected)
        }

    @Test
    fun `test that cancel event is emitted when start download nodes is invoked with empty list`() =
        runTest {
            commonStub()
            underTest.startTransfer(
                TransferTriggerEvent.StartDownloadNode(listOf())
            )
            assertCurrentEventIsEqualTo(StartTransferEvent.Message.TransferCancelled)
        }

    @Test
    fun `test that cancel event is emitted when start download nodes is invoked with null node`() =
        runTest {
            commonStub()
            underTest.startTransfer(
                TransferTriggerEvent.StartDownloadForOffline(null)
            )
            assertCurrentEventIsEqualTo(StartTransferEvent.Message.TransferCancelled)
        }

    @Test
    fun `test that job in progress is set to ProcessingFiles when start download use case starts`() =
        runTest {
            commonStub()
            stubStartDownload(flow {
                awaitCancellation()
            })
            underTest.startTransfer(TransferTriggerEvent.StartDownloadNode(nodes))
            assertThat(underTest.uiState.value.jobInProgressState)
                .isEqualTo(StartTransferJobInProgress.ScanningTransfers)
        }

    @Test
    fun `test that FinishDownloadProcessing event is emitted if start download use case emits an event with scanning finished true`() =
        runTest {
            commonStub()
            val triggerEvent = TransferTriggerEvent.StartDownloadNode(nodes)
            underTest.startTransfer(triggerEvent)
            assertThat(underTest.uiState.value.jobInProgressState).isNull()
            assertCurrentEventIsEqualTo(
                StartTransferEvent.FinishDownloadProcessing(null, 1, 1, 0, triggerEvent)
            )
        }

    @Test
    fun `test that FinishDownloadProcessing event is emitted if start download use case finishes correctly`() =
        runTest {
            commonStub()
            val triggerEvent = TransferTriggerEvent.StartDownloadNode(nodes)
            underTest.startTransfer(triggerEvent)
            assertCurrentEventIsEqualTo(
                StartTransferEvent.FinishDownloadProcessing(null, 1, 1, 0, triggerEvent)
            )
        }

    @Test
    fun `test that NotSufficientSpace event is emitted if start download use case returns NotSufficientSpace`() =
        runTest {
            commonStub()
            stubStartDownload(flowOf(MultiTransferEvent.InsufficientSpace))
            underTest.startTransfer(TransferTriggerEvent.StartDownloadNode(nodes))
            assertCurrentEventIsEqualTo(StartTransferEvent.Message.NotSufficientSpace)
        }

    @ParameterizedTest(name = "when StartDownloadUseCase finishes with {0}, then {1} is emitted")
    @MethodSource("provideDownloadNodeParameters")
    fun `test that a specific StartDownloadTransferEvent is emitted`(
        multiTransferEvent: MultiTransferEvent,
        startTransferEvent: StartTransferEvent,
    ) = runTest {
        commonStub()
        stubStartDownload(flowOf(multiTransferEvent))
        underTest.startTransfer(startDownloadEvent)
        assertCurrentEventIsEqualTo(startTransferEvent)
    }

    @ParameterizedTest
    @MethodSource("provideStartDownloadEvents")
    fun `test that ConfirmLargeDownload is emitted when a large download is started`(
        startEvent: TransferTriggerEvent.DownloadTriggerEvent,
    ) = runTest {
        commonStub()
        whenever(isAskBeforeLargeDownloadsSettingUseCase()).thenReturn(true)
        whenever(totalFileSizeOfNodesUseCase(any())).thenReturn(TransfersConstants.CONFIRM_SIZE_MIN_BYTES + 1)
        val size = "x MB"
        whenever(fileSizeStringMapper(any())).thenReturn(size)
        underTest.startTransfer(startEvent)
        assertCurrentEventIsEqualTo(
            StartTransferEvent.ConfirmLargeDownload(size, startEvent)
        )
    }

    @ParameterizedTest
    @MethodSource("provideStartDownloadEvents")
    fun `test that setAskBeforeLargeDownloadsSettingUseCase is invoked when specified in start download`(
        startEvent: TransferTriggerEvent.DownloadTriggerEvent,
    ) = runTest {
        commonStub()
        underTest.startDownloadWithoutConfirmation(startEvent, true)
        verify(setAskBeforeLargeDownloadsSettingUseCase).invoke(false)
    }

    @ParameterizedTest
    @MethodSource("provideStartDownloadEvents")
    fun `test that setAskBeforeLargeDownloadsSettingUseCase is not invoked when not specified in start download`(
        startEvent: TransferTriggerEvent.DownloadTriggerEvent,
    ) = runTest {
        commonStub()
        underTest.startDownloadWithoutConfirmation(startEvent, false)
        verifyNoInteractions(setAskBeforeLargeDownloadsSettingUseCase)
    }

    @Test
    fun `test that AskDestination event is triggered when a download starts and shouldAskDownloadDestinationUseCase is true`() =
        runTest {
            commonStub()
            whenever(shouldAskDownloadDestinationUseCase()).thenReturn(true)
            val event = TransferTriggerEvent.StartDownloadNode(nodes)
            underTest.startDownloadWithoutConfirmation(event)
            assertCurrentEventIsEqualTo(StartTransferEvent.AskDestination(event))
        }

    @Test
    fun `test that promptSaveDestination state is updated when startDownloadWithDestination is invoked and shouldPromptToSaveDestinationUseCase is true`() =
        runTest {
            commonStub()
            val uriString = "content:/destination"
            val destinationUri = mock<Uri> {
                on { toString() } doReturn uriString
            }
            val startDownloadNode = TransferTriggerEvent.StartDownloadNode(nodes)
            whenever(shouldPromptToSaveDestinationUseCase()).thenReturn(true)

            underTest.startDownloadWithDestination(startDownloadNode, destinationUri)

            assertThat(underTest.uiState.value.promptSaveDestination)
                .isInstanceOf(StateEventWithContentTriggered::class.java)
            assertThat((underTest.uiState.value.promptSaveDestination as StateEventWithContentTriggered).content)
                .isEqualTo(uriString)

        }

    @Test
    fun `test that saveDoNotPromptToSaveDestinationUseCase is invoked when doNotPromptToSaveDestinationAgain is invoked`() =
        runTest {
            underTest.doNotPromptToSaveDestinationAgain()
            verify(saveDoNotPromptToSaveDestinationUseCase).invoke()
        }

    @Test
    fun `test that setStorageDownloadLocationUseCase is invoked when saveDestination is invoked`() =
        runTest {
            val destination = "destination"
            underTest.saveDestination(destination)
            verify(setStorageDownloadLocationUseCase).invoke(destination)
        }

    @Test
    fun `test that setStorageDownloadAskAlwaysUseCase is set to false when saveDestination is invoked`() =
        runTest {
            underTest.saveDestination("destination")
            verify(setStorageDownloadAskAlwaysUseCase).invoke(false)
        }

    @Test
    fun `test that finish downloading event is emitted when monitorActiveTransferFinishedUseCase emits a value and transferTriggerEvent is StartDownloadNode`() =
        runTest {
            val monitorActiveTransferFinishedFlow = MutableSharedFlow<Int>()
            setup()
            commonStub()
            whenever(monitorActiveTransferFinishedUseCase(any()))
                .thenReturn(monitorActiveTransferFinishedFlow)

            underTest.startTransfer(TransferTriggerEvent.StartDownloadNode(listOf(mock()))) //to set lastTriggerEvent to StartDownloadNode
            underTest.onResume(mock())
            underTest.uiState.test {
                val finished = 2
                awaitItem() //current value doesn't matter
                monitorActiveTransferFinishedFlow.emit(finished)
                val expected =
                    triggered(StartTransferEvent.MessagePlural.FinishDownloading(finished))

                val actual = awaitItem().oneOffViewEvent

                assertThat(actual).isEqualTo(expected)
            }
        }

    @Test
    fun `test that paused transfers event is emitted when shouldAskForResumeTransfersUseCase is true and start a chat upload`() =
        runTest {
            commonStub()
            whenever(shouldAskForResumeTransfersUseCase()).thenReturn(true)
            underTest.startTransfer(
                TransferTriggerEvent.StartChatUpload.Files(CHAT_ID, listOf(uploadUri))
            )
            assertCurrentEventIsEqualTo(StartTransferEvent.PausedTransfers)
        }

    @Test
    fun `test that no connection event is emitted when monitorConnectivityUseCase is false and start an upload`() =
        runTest {
            commonStub()
            whenever(isConnectedToInternetUseCase()).thenReturn(false)

            underTest.startTransfer(
                TransferTriggerEvent.StartUpload.Files(
                    listOf(uploadUri),
                    parentId
                )
            )

            assertCurrentEventIsEqualTo(StartTransferEvent.NotConnected)
        }

    @Test
    fun `test that cancel event is emitted when start upload files is invoked with empty list`() =
        runTest {
            commonStub()

            underTest.startTransfer(
                TransferTriggerEvent.StartUpload.Files(listOf(), parentId)
            )

            assertCurrentEventIsEqualTo(StartTransferEvent.Message.TransferCancelled)
        }

    @Test
    fun `test that job in progress is set to ProcessingFiles when start upload use case starts`() =
        runTest {
            commonStub()
            stubStartUpload(flow {
                awaitCancellation()
            })

            underTest.startTransfer(
                TransferTriggerEvent.StartUpload.Files(
                    listOf(uploadUri),
                    parentId
                )
            )

            assertThat(underTest.uiState.value.jobInProgressState)
                .isEqualTo(StartTransferJobInProgress.ScanningTransfers)
        }

    @ParameterizedTest
    @MethodSource("provideStartUploadEvents")
    fun `test that start upload use case is invoked with correct parameters when upload is started`(
        startEvent: TransferTriggerEvent.StartUpload,
    ) = runTest {
        commonStub()

        underTest.startTransfer(startEvent)

        verify(startUploadWithWorkerUseCase).invoke(mapOf(uploadUri.toString() to null), parentId)
    }

    @Test
    fun `test that FinishUploadProcessing event is emitted if start upload use case emits an event with scanning finished true`() =
        runTest {
            commonStub()

            val triggerEvent = TransferTriggerEvent.StartUpload.Files(listOf(uploadUri), parentId)
            underTest.startTransfer(triggerEvent)

            assertThat(underTest.uiState.value.jobInProgressState).isNull()
            assertCurrentEventIsEqualTo(
                StartTransferEvent.FinishUploadProcessing(1)
            )
        }

    @Test
    fun `test that FinishUploadProcessing event is emitted if start upload use case finishes correctly`() =
        runTest {
            commonStub()

            val triggerEvent = TransferTriggerEvent.StartUpload.Files(listOf(uploadUri), parentId)
            underTest.startTransfer(triggerEvent)

            assertCurrentEventIsEqualTo(
                StartTransferEvent.FinishUploadProcessing(1)
            )
        }

    @Test
    fun `test that NotSufficientSpace event is emitted if start upload use case returns NotSufficientSpace`() =
        runTest {
            commonStub()
            stubStartUpload(flowOf(MultiTransferEvent.InsufficientSpace))

            underTest.startTransfer(
                TransferTriggerEvent.StartUpload.Files(
                    listOf(uploadUri),
                    parentId
                )
            )

            assertCurrentEventIsEqualTo(StartTransferEvent.Message.NotSufficientSpace)
        }

    @Test
    fun `test that finish uploading event is emitted when monitorActiveTransferFinishedUseCase emits a value and transferTriggerEvent is StartUpload`() =
        runTest {
            val monitorActiveTransferFinishedFlow = MutableSharedFlow<Int>()
            setup()
            commonStub()
            whenever(monitorActiveTransferFinishedUseCase(any()))
                .thenReturn(monitorActiveTransferFinishedFlow)

            underTest.startTransfer(
                TransferTriggerEvent.StartUpload.Files(
                    listOf(uploadUri),
                    parentId
                )
            ) //to set lastTriggerEvent to StartUpload
            underTest.onResume(mock())
            underTest.uiState.test {
                val finished = 1
                awaitItem() //current value doesn't matter
                monitorActiveTransferFinishedFlow.emit(finished)
                val expected =
                    triggered(StartTransferEvent.MessagePlural.FinishUploading(finished))

                val actual = awaitItem().oneOffViewEvent

                assertThat(actual).isEqualTo(expected)
            }
        }

    private fun provideDownloadNodeParameters() = listOf(
        Arguments.of(
            mock<MultiTransferEvent.SingleTransferEvent> {
                on { scanningFinished } doReturn true
            },
            StartTransferEvent.FinishDownloadProcessing(null, 1, 0, 0, startDownloadEvent),
        ),
        Arguments.of(
            MultiTransferEvent.InsufficientSpace,
            StartTransferEvent.Message.NotSufficientSpace,
        ),
    )

    private fun provideStartDownloadEvents() = listOf(
        TransferTriggerEvent.StartDownloadNode(nodes),
        TransferTriggerEvent.StartDownloadForOffline(node),
        TransferTriggerEvent.StartDownloadForPreview(node),

        )

    private fun provideStartChatUploadEvents() = listOf(
        TransferTriggerEvent.StartChatUpload.Files(CHAT_ID, listOf(uploadUri)),
    )

    private fun provideStartUploadEvents() = listOf(
        TransferTriggerEvent.StartUpload.Files(listOf(uploadUri), parentId)
    )

    private fun provideStartEvents() = provideStartDownloadEvents() +
            provideStartChatUploadEvents() + provideStartUploadEvents()

    private fun assertCurrentEventIsEqualTo(event: StartTransferEvent) {
        assertThat(underTest.uiState.value.oneOffViewEvent)
            .isInstanceOf(StateEventWithContentTriggered::class.java)
        assertThat((underTest.uiState.value.oneOffViewEvent as StateEventWithContentTriggered).content)
            .isEqualTo(event)
    }

    private suspend fun commonStub() {
        whenever(isAskBeforeLargeDownloadsSettingUseCase()).thenReturn(false)
        whenever(node.id).thenReturn(nodeId)
        whenever(node.parentId).thenReturn(parentId)
        whenever(parentNode.id).thenReturn(parentId)

        whenever(getOrCreateStorageDownloadLocationUseCase()).thenReturn(DESTINATION)

        whenever(isConnectedToInternetUseCase()).thenReturn(true)
        whenever(totalFileSizeOfNodesUseCase(any())).thenReturn(1)
        whenever(shouldAskDownloadDestinationUseCase()).thenReturn(false)
        stubStartTransfers(
            flowOf(
                mock<MultiTransferEvent.SingleTransferEvent> {
                    on { scanningFinished } doReturn true
                    on { allTransfersUpdated } doReturn true
                    on { startedFiles } doReturn 1
                })
        )
    }

    private fun stubStartTransfers(flow: Flow<MultiTransferEvent>) {
        stubStartDownload(flow)
        stubStartChatUpload(flow)
        stubStartUpload(flow)
    }

    private fun stubStartDownload(flow: Flow<MultiTransferEvent>) {
        whenever(
            startDownloadsWithWorkerUseCase(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            )
        ).thenReturn(flow)
    }

    private fun stubStartChatUpload(flow: Flow<MultiTransferEvent>) {
        whenever(sendChatAttachmentsUseCase(any(), anyOrNull(), any())).thenReturn(flow)
    }

    private fun stubStartUpload(flow: Flow<MultiTransferEvent>) {
        whenever(
            startUploadWithWorkerUseCase(
                mapOf(uploadUri.toString() to null),
                parentId
            )
        ).thenReturn(flow)
    }

    companion object {
        private const val NODE_HANDLE = 10L
        private const val PARENT_NODE_HANDLE = 12L
        private const val CHAT_ID = 20L
        private val uploadUri = mock<Uri>()
        private val nodeId = NodeId(NODE_HANDLE)
        private val parentId = NodeId(PARENT_NODE_HANDLE)
        private const val DESTINATION = "/destination/"
    }
}
