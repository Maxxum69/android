package mega.privacy.android.domain.usecase.photos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mega.privacy.android.domain.entity.photos.Photo
import mega.privacy.android.domain.repository.AlbumRepository
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.fail

@ExperimentalCoroutinesApi
class DownloadPublicAlbumPhotoPreviewUseCaseTest {
    private lateinit var underTest: DownloadPublicAlbumPhotoPreviewUseCase

    private val albumRepository = mock<AlbumRepository>()

    @Before
    fun setUp() {
        underTest = DownloadPublicAlbumPhotoPreviewUseCase(
            albumRepository = albumRepository,
        )
    }

    @Test
    fun `test that use case returns correct result`() = runTest {
        // given
        val photo = mock<Photo.Image>()
        whenever(albumRepository.downloadPublicPreview(photo, {}))
            .thenReturn(Unit)

        // when
        try {
            underTest(photo, {})
        } catch (e: Exception) {
            fail(message = "${e.message}")
        }
    }
}
