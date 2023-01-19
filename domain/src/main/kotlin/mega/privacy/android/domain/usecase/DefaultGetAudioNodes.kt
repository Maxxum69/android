package mega.privacy.android.domain.usecase

import mega.privacy.android.domain.entity.SortOrder
import mega.privacy.android.domain.entity.node.TypedNode
import mega.privacy.android.domain.repository.MediaPlayerRepository
import javax.inject.Inject

/**
 * The implementation of [GetAudioNodes]
 */
class DefaultGetAudioNodes @Inject constructor(
    private val mediaPlayerRepository: MediaPlayerRepository,
    private val addNodeType: AddNodeType,
) : GetAudioNodes {
    override suspend fun invoke(order: SortOrder): List<TypedNode> =
        mediaPlayerRepository.getAudioNodes(order).map { addNodeType(it) }
}