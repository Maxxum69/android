package test.mega.privacy.android.app.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.domain.usecase.*
import mega.privacy.android.app.globalmanagement.SortOrderManagementInterface
import nz.mega.sdk.MegaApiJava
import nz.mega.sdk.MegaNode
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class DefaultGetRubbishBinChildrenNodeTest {

    private lateinit var underTest: GetRubbishBinChildrenNode
    private val getNodeByHandle = mock<GetNodeByHandle>()
    private val getChildrenNode = mock<GetChildrenNode>()
    private val getRubbishBinNode = mock<GetRubbishBinNode>()
    private val sortOrderManagement = mock<SortOrderManagementInterface>()

    @Before
    fun setUp() {
        underTest = DefaultGetRubbishBinChildrenNode(
            getNodeByHandle,
            getChildrenNode,
            getRubbishBinNode,
            sortOrderManagement
        )
    }

    @Test
    fun `test that invoke with -1L invoke getRubbishBinNode`() = runTest {
        underTest(-1L)

        verify(getRubbishBinNode).invoke()
    }

    @Test
    fun `test that invoke with value except -1L invoke getNodeByHandle`() = runTest {
        val parentHandle = 0L
        underTest(parentHandle)

        verify(getNodeByHandle).invoke(parentHandle)
    }

    @Test
    fun `test that -1L invoke getChildrenNode with result of getRubbishBinNode`() = runTest {
        val result = mock<MegaNode>{}
        whenever(getRubbishBinNode()).thenReturn(result)
        underTest(-1L)

        verify(getChildrenNode).invoke(result, MegaApiJava.ORDER_NONE)
    }

    @Test
    fun `test that -1L invoke getChildrenNode with result of getNodeByHandle`() = runTest {
        val result = mock<MegaNode>{}
        val parentHandle = 0L
        whenever(getNodeByHandle(parentHandle)).thenReturn(result)
        underTest(parentHandle)

        verify(getChildrenNode).invoke(result, MegaApiJava.ORDER_NONE)
    }

    @Test
    fun `test that underTest is invoked with value of get order sort management`() = runTest {
        val sortOrder = MegaApiJava.ORDER_DEFAULT_ASC
        whenever(sortOrderManagement.getOrderCloud()).thenReturn(sortOrder)
        whenever(getRubbishBinNode()).thenReturn(mock())
        underTest(-1L)

        verify(getChildrenNode).invoke(any(), eq(sortOrder))
    }
}