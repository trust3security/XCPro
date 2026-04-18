package com.trust3.xcpro.livefollow.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

internal sealed interface XcGoogleIdTokenRequestResult {
    data class Success(
        val idToken: String
    ) : XcGoogleIdTokenRequestResult

    data object Cancelled : XcGoogleIdTokenRequestResult

    data class Failure(
        val message: String
    ) : XcGoogleIdTokenRequestResult

    data class Unavailable(
        val message: String
    ) : XcGoogleIdTokenRequestResult
}

internal suspend fun requestXcGoogleIdToken(
    context: Context
): XcGoogleIdTokenRequestResult {
    val activity = context.findActivity() ?: return XcGoogleIdTokenRequestResult.Failure(
        "Google sign-in requires an activity context."
    )
    val config = resolveXcGoogleSignInConfig()
    val webClientId = config.serverClientId
        ?: return XcGoogleIdTokenRequestResult.Unavailable(
            config.availabilityNote ?: "Google sign-in is not configured in this build."
        )

    val credentialManager = CredentialManager.create(activity)
    val authorizedRequest = buildGoogleRequest(
        serverClientId = webClientId,
        filterByAuthorizedAccounts = true,
        autoSelectEnabled = true
    )

    return when (
        val authorizedResult = getGoogleIdToken(
            credentialManager = credentialManager,
            activity = activity,
            request = authorizedRequest
        )
    ) {
        is XcGoogleIdTokenRequestResult.Failure -> authorizedResult
        is XcGoogleIdTokenRequestResult.Success -> authorizedResult
        XcGoogleIdTokenRequestResult.Cancelled -> XcGoogleIdTokenRequestResult.Cancelled
        is XcGoogleIdTokenRequestResult.Unavailable -> {
            val fallbackRequest = buildGoogleRequest(
                serverClientId = webClientId,
                filterByAuthorizedAccounts = false,
                autoSelectEnabled = false
            )
            getGoogleIdToken(
                credentialManager = credentialManager,
                activity = activity,
                request = fallbackRequest
            )
        }
    }
}

private suspend fun getGoogleIdToken(
    credentialManager: CredentialManager,
    activity: Activity,
    request: GetCredentialRequest
): XcGoogleIdTokenRequestResult {
    return try {
        val response = credentialManager.getCredential(
            context = activity,
            request = request
        )
        val credential = response.credential as? CustomCredential
            ?: return XcGoogleIdTokenRequestResult.Failure(
                "Google sign-in returned an unexpected credential type."
            )
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return XcGoogleIdTokenRequestResult.Failure(
                "Google sign-in returned an unexpected credential type."
            )
        }
        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (_: GoogleIdTokenParsingException) {
            return XcGoogleIdTokenRequestResult.Failure(
                "Google sign-in returned an invalid ID token payload."
            )
        }
        val idToken = googleCredential.idToken.trim()
        if (idToken.isEmpty()) {
            XcGoogleIdTokenRequestResult.Failure(
                "Google sign-in did not return an ID token."
            )
        } else {
            XcGoogleIdTokenRequestResult.Success(idToken)
        }
    } catch (_: NoCredentialException) {
        XcGoogleIdTokenRequestResult.Unavailable(
            "No eligible Google account is available on this device."
        )
    } catch (_: GetCredentialCancellationException) {
        XcGoogleIdTokenRequestResult.Cancelled
    } catch (exception: GetCredentialException) {
        XcGoogleIdTokenRequestResult.Failure(
            exception.message?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "Google sign-in was unavailable."
        )
    }
}

private fun buildGoogleRequest(
    serverClientId: String,
    filterByAuthorizedAccounts: Boolean,
    autoSelectEnabled: Boolean
): GetCredentialRequest {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
        .setAutoSelectEnabled(autoSelectEnabled)
        .build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
