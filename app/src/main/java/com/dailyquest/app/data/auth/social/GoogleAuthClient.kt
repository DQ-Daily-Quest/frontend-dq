package com.dailyquest.app.data.auth.social

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/** Credential Manager의 "Sign in with Google"로 ID Token을 얻는다. 기기에 없는 새 계정도 선택할 수 있다. */
class GoogleAuthClient(
    private val serverClientId: String,
    private val appContext: Context,
) : SocialAuthClient {
    private val manager = CredentialManager.create(appContext)

    override suspend fun signIn(context: Context): SocialSignInResult {
        if (serverClientId.isBlank()) return SocialSignInResult.NotConfigured
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            // 계정 선택 UI를 띄우려면 Activity Context가 필요하다.
            val credential = manager.getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (token.isBlank()) SocialSignInResult.MissingIdToken else SocialSignInResult.Token(token)
            } else {
                SocialSignInResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            SocialSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SocialSignInResult.NoAccount
        } catch (e: GetCredentialInterruptedException) {
            SocialSignInResult.Network
        } catch (e: GoogleIdTokenParsingException) {
            SocialSignInResult.MissingIdToken
        } catch (e: GetCredentialException) {
            SocialSignInResult.Failed
        }
    }

    override suspend fun signOut() {
        runCatching { manager.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
