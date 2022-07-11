package mega.privacy.android.app.domain.usecase

/**
 * Delete camera upload sync record by local path
 *
 */
interface DeleteSyncRecordByLocalPath {

    /**
     * Invoke
     *
     * @return
     */
    operator fun invoke(localPath: String, isSecondary: Boolean)
}
