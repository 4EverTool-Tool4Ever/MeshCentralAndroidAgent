package com.meshcentral.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Transparent activity whose only job is the first MediaProjection consent while the agent app is
 * not on screen (a wall tablet showing another app). Below Android 10 an app may start an activity
 * from the background, so the system dialog appears over whatever is displayed; once accepted, the
 * projection is kept by [ScreenCaptureService] and later sessions never show this again.
 */
class ProjectionConsentActivity : Activity() {
    private var sessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val id = sessionId
        if (id == null || !AgentController.isDesktopSessionAuthorized(id)) {
            finish()
            return
        }
        if (savedInstanceState != null) return // Dialog already requested before a recreation.
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) return
        val id = sessionId
        if (id != null && AgentController.isDesktopSessionAuthorized(id)) {
            if (resultCode == RESULT_OK && data != null) {
                ContextCompat.startForegroundService(this,
                    ScreenCaptureService.getStartIntent(this, resultCode, data, id))
            } else {
                AgentController.cancelDesktopSession(id)
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 7001
        private const val EXTRA_SESSION_ID = "SESSION_ID"

        // Background activity starts are blocked from Android 10.
        val supported: Boolean
            get() = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, ProjectionConsentActivity::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.startActivity(intent)
        }
    }
}
