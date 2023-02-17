package mega.privacy.android.domain.usecase.imageviewer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mega.privacy.android.domain.entity.offline.OfflineNodeInformation
import mega.privacy.android.domain.repository.ImageRepository
import mega.privacy.android.domain.repository.NodeRepository
import mega.privacy.android.domain.usecase.GetOfflineFile
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File


@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGetImageByOfflineNodeHandleTest {
    private lateinit var underTest: GetImageByOfflineNodeHandle

    private val nodeRepository = mock<NodeRepository>()
    private val imageRepository = mock<ImageRepository>()
    private val getOfflineFile = mock<GetOfflineFile>()

    private val nodeHandle = 1L
    private val highPriority = false
    private val offlineNodeInformation = mock<OfflineNodeInformation>()

    @Before
    fun setUp() {
        underTest =
            DefaultGetImageByOfflineNodeHandle(nodeRepository, getOfflineFile, imageRepository)
    }

    @Test
    fun `test that offline node information is retrieved on invoke`() {
        runTest {
            val file = mock<File> {
                on { exists() }.thenReturn(true)
            }
            whenever(nodeRepository.getOfflineNodeInformation(nodeHandle)).thenReturn(
                offlineNodeInformation
            )
            whenever(getOfflineFile(offlineNodeInformation)).thenReturn(file)
            underTest.invoke(
                nodeHandle = nodeHandle,
                highPriority = highPriority,
            )
            verify(nodeRepository, times(1)).getOfflineNodeInformation(nodeHandle)
            verify(getOfflineFile, times(1)).invoke(offlineNodeInformation)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test that exception is thrown when offline node information returned is null`() {
        runTest {
            whenever(nodeRepository.getOfflineNodeInformation(nodeHandle)).thenReturn(null)

            underTest.invoke(
                nodeHandle = nodeHandle,
                highPriority = highPriority,
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test that exception is thrown when offline file does not exist`() {
        runTest {
            val file = mock<File> {
                on { exists() }.thenReturn(false)
            }
            whenever(nodeRepository.getOfflineNodeInformation(nodeHandle)).thenReturn(
                offlineNodeInformation
            )
            whenever(getOfflineFile(offlineNodeInformation)).thenReturn(file)
            underTest.invoke(
                nodeHandle = nodeHandle,
                highPriority = highPriority,
            )
        }
    }

    @Test
    fun `test that image repository function is invoked when offline file exists`() {
        runTest {
            val file = mock<File> {
                on { exists() }.thenReturn(true)
            }
            whenever(nodeRepository.getOfflineNodeInformation(nodeHandle)).thenReturn(
                offlineNodeInformation
            )
            whenever(getOfflineFile(offlineNodeInformation)).thenReturn(file)
            underTest.invoke(
                nodeHandle = nodeHandle,
                highPriority = highPriority,
            )
            verify(imageRepository, times(1)).getImageByOfflineFile(
                offlineNodeInformation,
                file,
                highPriority
            )
        }
    }
}