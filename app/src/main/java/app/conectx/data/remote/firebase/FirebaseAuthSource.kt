package app.conectx.data.remote.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Auth wrapper. Uses anonymous auth to get a stable UID for
 * the Realtime Database without requiring any personal data.
 *
 * The anonymous UID is used as the authorId for sync records when
 * Firebase transport is active. It maps to the same user even across
 * app restarts (Firebase persists anonymous sessions).
 */
@Singleton
class FirebaseAuthSource @Inject constructor(
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "FirebaseAuth"
    }

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    val uid: String?
        get() = auth.currentUser?.uid

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    /**
     * Signs in anonymously. If already signed in, returns the existing user.
     * Call this early in the app lifecycle (e.g. from SyncEngine.start()).
     */
    suspend fun ensureSignedIn(): Result<FirebaseUser> {
        auth.currentUser?.let { return Result.success(it) }

        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user ?: return Result.failure(IllegalStateException("No user after sign-in"))
            Log.d(TAG, "Anonymous sign-in: uid=${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
