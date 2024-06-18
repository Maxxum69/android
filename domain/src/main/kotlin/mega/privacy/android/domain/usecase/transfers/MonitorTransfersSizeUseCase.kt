package mega.privacy.android.domain.usecase.transfers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import mega.privacy.android.domain.entity.TransfersSizeInfo
import mega.privacy.android.domain.entity.transfer.Transfer
import mega.privacy.android.domain.entity.transfer.TransferState
import mega.privacy.android.domain.entity.transfer.TransferType
import mega.privacy.android.domain.repository.TransferRepository
import javax.inject.Inject

/**
 * Default implementation of [MonitorTransfersSize]
 */
class MonitorTransfersSizeUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    private val transferMap: MutableMap<Int, Transfer> = hashMapOf()

    /**
     * Invoke.
     *
     * @return Flow of [TransfersSizeInfo]
     */

    operator fun invoke(): Flow<TransfersSizeInfo> =
        combine(TransferType.entries.filterNot { it == TransferType.NONE }.map {
            repository.getActiveTransferTotalsByType(it)
        }) { activeTransferTotals ->
            TransfersSizeInfo(
                totalSizeToTransfer = activeTransferTotals.sumOf { it.totalBytes },
                totalSizeTransferred = activeTransferTotals.sumOf { it.transferredBytes },
                pendingUploads = activeTransferTotals
                    .filter { it.transfersType.isUploadType() }
                    .sumOf { it.pendingFileTransfers },
                pendingDownloads = activeTransferTotals
                    .filter { it.transfersType.isDownloadType() }
                    .sumOf { it.pendingFileTransfers },
            )
        }

    @Deprecated(message = "This will be deleted once AppFeatures.UploadWorker flag is deleted")
    fun invokeLegacy(): Flow<TransfersSizeInfo> = repository.monitorTransferEvents()
        .map {
            val transfer = it.transfer
            transferMap[transfer.tag] = transfer

            var totalBytes: Long = 0
            var totalTransferred: Long = 0

            val megaTransfers = transferMap.values.toList()
            megaTransfers.forEach { itemTransfer ->
                with(itemTransfer) {
                    totalBytes += this.totalBytes
                    totalTransferred +=
                        if (state == TransferState.STATE_COMPLETED) this.totalBytes
                        else transferredBytes
                }
            }
            // we only clear cache when all transfer done
            // if we remove in OnTransferFinish it can cause the progress show incorrectly
            if (megaTransfers.all { megaTransfer -> megaTransfer.isFinished }) {
                transferMap.clear()
            }
            TransfersSizeInfo(
                totalSizeToTransfer = totalBytes,
                totalSizeTransferred = totalTransferred
            )
        }
}