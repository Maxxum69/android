package mega.privacy.android.app.extensions

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * Enable edge to edge and consume insets for the activity used XML layout.
 *
 */
fun ComponentActivity.enableEdgeToEdgeAndConsumeInsets() {
    // we need condition to check if the device running Android 15 when we target sdk to 35
    // because it will enable edge to edge by default
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
            bottomMargin = insets.bottom
            rightMargin = insets.right
            topMargin = insets.top
        }

        WindowInsetsCompat.CONSUMED
    }
}