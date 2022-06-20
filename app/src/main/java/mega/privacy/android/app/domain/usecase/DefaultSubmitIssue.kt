package mega.privacy.android.app.domain.usecase

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import mega.privacy.android.app.domain.entity.Progress
import mega.privacy.android.app.domain.entity.SubmitIssueRequest
import mega.privacy.android.app.domain.repository.LoggingRepository
import mega.privacy.android.app.domain.repository.SupportRepository
import javax.inject.Inject

/**
 * Default submit issue
 *
 * @property loggingRepository
 * @property supportRepository
 * @property createSupportTicket
 * @property formatSupportTicket
 */
class DefaultSubmitIssue @Inject constructor(
        private val loggingRepository: LoggingRepository,
        private val supportRepository: SupportRepository,
        private val createSupportTicket: CreateSupportTicket,
        private val formatSupportTicket: FormatSupportTicket
) : SubmitIssue {
    override suspend fun invoke(request: SubmitIssueRequest): Flow<Progress> {
        return flow {
            val logFileName = uploadLogs(request.includeLogs)
            if (currentCoroutineContext().isActive) {
                val formattedTicket = createFormattedSupportTicket(request.description, logFileName)
                supportRepository.logTicket(formattedTicket)
            }
        }
    }

    private suspend fun FlowCollector<Progress>.uploadLogs(
            includeLogs: Boolean
    ): String? {
        val logs = if (shouldCompressLogs(includeLogs)) loggingRepository.compressLogs() else null
        logs?.let {
            emitAll(
                    supportRepository.uploadFile(it)
                            .map { percentage -> Progress(percentage) }
            )
        }
        return logs?.name
    }

    private suspend fun shouldCompressLogs(includeLogs: Boolean) =
            includeLogs && currentCoroutineContext().isActive

    private suspend fun createFormattedSupportTicket(
            description: String,
            logFileName: String?
    ) = formatSupportTicket(
            createSupportTicket(description, logFileName)
    )

}