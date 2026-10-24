package com.voiceludo.app.net

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

// ============================================================================
// "Continue with Google" — asal Google login.
//
// Flow: phone ka Google account chooser -> Google ID token -> Firebase Auth mein
// sign-in -> Firebase ID token -> yahi token BackendClient.socialLogin() se server
// ko jata hai (server khud verify karta hai, dekhein backend social.go).
//
// WEB_CLIENT_ID = Firebase/Google Cloud ka "Web client" ID (google-services.json mein
// client_type 3). Isay Android client ID se confuse mat karna.
// ============================================================================
object GoogleSignInHelper {

    private const val WEB_CLIENT_ID =
        "427973149136-c5u5mdondn8frdcvjq55qf44eebjdf1p.apps.googleusercontent.com"

    /** Success par Firebase ID token aur (agar mile) email wapis deta hai. */
    suspend fun signIn(
        activityContext: Context,
        onSuccess: (firebaseIdToken: String, email: String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val credentialManager = CredentialManager.create(activityContext)
            // GetSignInWithGoogleOption = "Sign in with Google" button wala flow. Ye har
            // Google account dikhata hai (naye/unauthorized bhi) aur button-tap ke liye
            // Google ka recommended tareeqa hai. Purana GetGoogleIdOption "no credentials"
            // de deta tha jab account pehle kabhi is app se authorize nahi hua tha.
            val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = credentialManager.getCredential(context = activityContext, request = request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                onError("Google login: unexpected response. Please try again.")
                return
            }
            val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
            val googleEmail = googleToken.id

            val auth = FirebaseAuth.getInstance()
            val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
            auth.signInWithCredential(firebaseCredential).addOnCompleteListener { signInTask ->
                val user = auth.currentUser
                if (!signInTask.isSuccessful || user == null) {
                    onError("Google login failed: ${signInTask.exception?.message ?: "unknown error"}")
                    return@addOnCompleteListener
                }
                user.getIdToken(true).addOnCompleteListener { tokenTask ->
                    val idToken = tokenTask.result?.token
                    if (tokenTask.isSuccessful && !idToken.isNullOrBlank()) {
                        onSuccess(idToken, user.email ?: googleEmail)
                    } else {
                        onError("Google login failed: could not get login token.")
                    }
                }
            }
        } catch (e: GetCredentialCancellationException) {
            onError("Google login cancelled.")
        } catch (e: NoCredentialException) {
            // NOTE: Ye error tab bhi aata hai jab APK ka SHA-1 Firebase mein registered na ho
            // (sirf "phone mein account nahi" ka matlab nahi). Isliye saaf detail dikhate hain.
            onError("Google login failed (no credential). Add a Google account on this phone, update Google Play services, and make sure this app's SHA-1 is added in Firebase. [${e.type}]")
        } catch (e: GoogleIdTokenParsingException) {
            onError("Google login failed: invalid Google response.")
        } catch (e: GetCredentialException) {
            onError("Google login failed: ${e.message ?: e.type}")
        } catch (e: Exception) {
            onError("Google login failed: ${e.message ?: "unknown error"}")
        }
    }

    /** Logout par Firebase ka local session bhi saaf kar dene ke liye. */
    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {
        }
    }
}
