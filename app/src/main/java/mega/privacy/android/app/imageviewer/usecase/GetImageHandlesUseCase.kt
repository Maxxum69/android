package mega.privacy.android.app.imageviewer.usecase

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.subscribeBy
import mega.privacy.android.app.DatabaseHandler
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.di.MegaApi
import mega.privacy.android.app.imageviewer.data.ImageItem
import mega.privacy.android.app.listeners.OptionalMegaRequestListenerInterface
import mega.privacy.android.app.usecase.GetGlobalChangesUseCase
import mega.privacy.android.app.utils.Constants.INVALID_POSITION
import mega.privacy.android.app.utils.ErrorUtils.toThrowable
import mega.privacy.android.app.utils.MegaNodeUtil.isValidForImageViewer
import mega.privacy.android.app.utils.MegaNodeUtil.isVideo
import mega.privacy.android.app.utils.OfflineUtils
import nz.mega.sdk.MegaApiAndroid
import nz.mega.sdk.MegaApiJava.INVALID_HANDLE
import nz.mega.sdk.MegaApiJava.ORDER_PHOTO_ASC
import nz.mega.sdk.MegaError
import nz.mega.sdk.MegaNode
import nz.mega.sdk.MegaNode.CHANGE_TYPE_NEW
import nz.mega.sdk.MegaNode.CHANGE_TYPE_PARENT
import nz.mega.sdk.MegaNode.CHANGE_TYPE_REMOVED
import nz.mega.sdk.MegaRequest
import javax.inject.Inject

class GetImageHandlesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    @MegaApi private val megaApi: MegaApiAndroid,
    private val databaseHandler: DatabaseHandler,
    private val getGlobalChangesUseCase: GetGlobalChangesUseCase
) {

    fun get(
        nodeHandles: LongArray? = null,
        parentNodeHandle: Long? = null,
        nodeFileLink: String? = null,
        sortOrder: Int? = ORDER_PHOTO_ASC,
        isOffline: Boolean = false
    ): Flowable<List<ImageItem>> =
        Flowable.create({ emitter ->
            val items = mutableListOf<ImageItem>()
            when {
                parentNodeHandle != null && parentNodeHandle != INVALID_HANDLE -> {
                    val parentNode = megaApi.getNodeByHandle(parentNodeHandle)
                    if (parentNode != null && megaApi.hasChildren(parentNode)) {
                        items.addChildrenNodes(parentNode, sortOrder ?: ORDER_PHOTO_ASC)
                    } else {
                        emitter.onError(IllegalStateException("Node is null or has no children"))
                        return@create
                    }
                }
                isOffline && nodeHandles != null && nodeHandles.isNotEmpty() -> {
                    items.addOfflineNodeHandles(nodeHandles)
                }
                nodeHandles != null && nodeHandles.isNotEmpty() -> {
                    items.addNodeHandles(nodeHandles)
                }
                !nodeFileLink.isNullOrBlank() -> {
                    megaApi.getPublicNode(nodeFileLink, OptionalMegaRequestListenerInterface(
                        onRequestFinish = { request, error ->
                            if (emitter.isCancelled) return@OptionalMegaRequestListenerInterface

                            if (error.errorCode == MegaError.API_OK) {
                                if (!request.flag) {
                                    val publicNode = request.publicNode
                                    if (publicNode?.isValidForImageViewer() == true) {
                                        items.add(publicNode.toImageItem())
                                    }
                                    emitter.onNext(items)
                                } else {
                                    emitter.onError(IllegalStateException("Invalid key for public node"))
                                }
                            } else {
                                emitter.onError(error.toThrowable())
                            }
                        }
                    ))
                }
                else -> {
                    emitter.onError(IllegalArgumentException("Invalid parameters"))
                    return@create
                }
            }
            if (nodeFileLink.isNullOrBlank()) {
                emitter.onNext(items)
                if (items.isEmpty()) {
                    emitter.onError(IllegalStateException("Invalid image handles"))
                    return@create
                }
            }

            val globalSubscription = getGlobalChangesUseCase.get().subscribeBy(
                onNext = { change ->
                    if (emitter.isCancelled) return@subscribeBy

                    if (change is GetGlobalChangesUseCase.Result.OnNodesUpdate) {
                        change.nodes?.forEach { changedNode ->
                            val index = items.indexOfFirst { it.handle == changedNode.handle }

                            if (changedNode.hasChanged(CHANGE_TYPE_NEW)
                                || changedNode.hasChanged(CHANGE_TYPE_PARENT)) {
                                val hasSameParent = when {
                                    changedNode.parentHandle == null -> { // getParentHandle() can be null
                                        false
                                    }
                                    parentNodeHandle != null -> {
                                        changedNode.parentHandle == parentNodeHandle
                                    }
                                    items.isNotEmpty() -> {
                                        val sampleNodeHandle = items.first().handle
                                        val sampleParentHandle = megaApi.getNodeByHandle(sampleNodeHandle)?.parentHandle
                                        changedNode.parentHandle == sampleParentHandle
                                    }
                                    else -> false
                                }

                                if (hasSameParent) {
                                    if (changedNode.hasChanged(CHANGE_TYPE_PARENT)) {
                                        items[index] = changedNode.toImageItem()
                                    } else if (changedNode.isValidForImageViewer()) {
                                        items.add(changedNode.toImageItem())
                                    }
                                } else if (changedNode.hasChanged(CHANGE_TYPE_PARENT)) {
                                    items.removeAt(index)
                                }
                            } else if (index != INVALID_POSITION) {
                                if (changedNode.hasChanged(CHANGE_TYPE_REMOVED)) {
                                    items.removeAt(index)
                                } else {
                                    items[index] = changedNode.toImageItem()
                                }
                            }
                        }

                        emitter.onNext(items)
                    }
                }
            )

            emitter.setCancellable {
                globalSubscription.dispose()
            }
        }, BackpressureStrategy.LATEST)

    private fun MutableList<ImageItem>.addNodeHandles(nodeHandles: LongArray) {
        nodeHandles.forEach { nodeHandle ->
            val node = megaApi.getNodeByHandle(nodeHandle)
            if (node?.isValidForImageViewer() == true) {
                this.add(node.toImageItem())
            }
        }
    }

    private fun MutableList<ImageItem>.addChildrenNodes(megaNode: MegaNode, sortOrder: Int) {
        megaApi.getChildren(megaNode, sortOrder).forEach { node ->
            if (node.isValidForImageViewer()) {
                this.add(ImageItem(node.handle, node.name, node.isVideo()))
            }
        }
    }

    private fun MutableList<ImageItem>.addOfflineNodeHandles(nodeHandles: LongArray) {
        val offlineNodes = databaseHandler.offlineFiles
        nodeHandles.forEach { nodeHandle ->
            offlineNodes
                .find { offlineNode ->
                    nodeHandle == offlineNode.handle.toLongOrNull() ||
                            nodeHandle == offlineNode.handleIncoming.toLongOrNull()
                }?.let { offlineNode ->
                    val file = OfflineUtils.getOfflineFile(context, offlineNode)
                    if (file.exists()) {
                        this.add(
                            ImageItem(
                                offlineNode.handle.toLong(),
                                offlineNode.name,
                                MimeTypeList.typeForName(offlineNode.name).isVideo,
                                fullSizeUri = file.toUri()
                            )
                        )
                    }
                }
        }
    }

    private fun MegaNode.toImageItem(): ImageItem =
        ImageItem(handle, name, isVideo())
}
