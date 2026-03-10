package app.conectx.data.remote.supabase

import android.util.Log
import app.conectx.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies activation codes against the Supabase backend.
 *
 * The web app (conectx.app) generates activation codes after Stripe
 * checkout. Each code encodes:
 *   - pass type: "mundial" (full World Cup) or "partido" (single match)
 *   - expiry: epoch timestamp
 *   - userId: the Supabase user ID linked to this purchase
 *
 * This API calls a Supabase Edge Function to verify the code and
 * return the pass details.
 */
@Singleton
class ActivationApi @Inject constructor() {

    companion object {
        private const val TAG = "ActivationApi"

        // TODO: replace with actual Supabase project URL
        private const val BASE_URL = "https://your-project.supabase.co/functions/v1"
    }

    data class ActivationResult(
        val valid: Boolean,
        val userId: String,
        val passType: String,    // "mundial" or "partido"
        val expiresAt: Long,     // epoch millis
        val message: String      // user-facing message on error
    )

    /**
     * Verifies an activation code against the Supabase backend.
     * Returns a result with pass details if valid.
     *
     * In debug builds, accepts any non-empty code so the app can be
     * tested end-to-end without a live backend.
     */
    suspend fun verify(code: String): Result<ActivationResult> {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG mode — auto-accepting code '$code'")
            return Result.success(
                ActivationResult(
                    valid = true,
                    userId = UUID.randomUUID().toString(),
                    passType = "mundial",
                    expiresAt = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000,
                    message = ""
                )
            )
        }

        return try {
            val url = URL("$BASE_URL/verify-activation")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            // Send the code
            val body = JSONObject().apply {
                put("code", code.trim().uppercase())
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            // Read response
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299)
                connection.inputStream else connection.errorStream
            val responseBody = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            connection.disconnect()

            if (responseCode !in 200..299) {
                val error = try {
                    JSONObject(responseBody).optString("message", "Error de verificacion")
                } catch (_: Exception) {
                    "Error de verificacion"
                }
                return Result.success(
                    ActivationResult(
                        valid = false,
                        userId = "",
                        passType = "",
                        expiresAt = 0,
                        message = error
                    )
                )
            }

            val json = JSONObject(responseBody)
            Result.success(
                ActivationResult(
                    valid = json.optBoolean("valid", false),
                    userId = json.optString("userId", ""),
                    passType = json.optString("passType", ""),
                    expiresAt = json.optLong("expiresAt", 0),
                    message = json.optString("message", "")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Verification failed", e)
            Result.failure(e)
        }
    }
}
