package mega.privacy.android.app.usecase.call

import io.reactivex.rxjava3.core.Single
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.listeners.OptionalMegaChatRequestListenerInterface
import mega.privacy.android.app.usecase.toMegaException
import mega.privacy.android.app.utils.CallUtil
import mega.privacy.android.app.utils.Constants.START_CALL_AUDIO_ENABLE
import nz.mega.sdk.*
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE
import javax.inject.Inject

/**
 * Use case to start call
 *
 * @property megaChatApi    MegaChatApi required to call the SDK
 */
class AnswerCallUseCase @Inject constructor(
    private val megaChatApi: MegaChatApiAndroid
) {
    /**
     * Call Result.
     *
     * @property chatHandle       Chat ID
     * @property enableVideo      Video ON
     * @property enableAudio      Audio ON
     */
    data class AnswerCallResult(
        val chatHandle: Long = MEGACHAT_INVALID_HANDLE,
        val enableVideo: Boolean = false,
        val enableAudio: Boolean = false,
    )

    fun answerCall(
        chatId: Long,
        enableVideo: Boolean,
        enableAudio: Boolean,
        enableSpeaker: Boolean
    ): Single<AnswerCallResult> =
        Single.create { emitter ->
            megaChatApi.answerChatCall(
                chatId,
                enableVideo,
                enableAudio,
                OptionalMegaChatRequestListenerInterface(
                    onRequestFinish = { request: MegaChatRequest, error: MegaChatError ->
                        if (emitter.isDisposed) return@OptionalMegaChatRequestListenerInterface

                        val requestChatId = request.chatHandle
                        if (error.errorCode == MegaError.API_OK) {
                            CallUtil.addChecksForACall(requestChatId, enableSpeaker)
                            MegaApplication.getChatManagement().addJoiningCallChatId(requestChatId)
                            megaChatApi.getChatCall(requestChatId)?.let { call ->
                                MegaApplication.getChatManagement()
                                    .setRequestSentCall(call.callId, false)
                            }

                            val enabledAudio: Boolean = request.paramType == START_CALL_AUDIO_ENABLE
                            val enabledVideo = request.flag

                            emitter.onSuccess(
                                AnswerCallResult(
                                    requestChatId,
                                    enabledVideo,
                                    enabledAudio
                                )
                            )
                        } else {
                            MegaApplication.getInstance().removeRTCAudioManagerRingIn()
                            megaChatApi.getChatCall(request.chatHandle)?.let { call ->
                                CallUtil.clearIncomingCallNotification(call.callId)
                            }
                            emitter.onError(error.toMegaException())
                        }
                    })
            )
        }
}