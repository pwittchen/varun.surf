package surf.varun.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import surf.varun.app.navigation.DeepLink
import surf.varun.app.navigation.DeepLinks
import surf.varun.app.ui.VarunApp
import surf.varun.core.navigation.TopLevel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** A link that arrived and hasn't been handed to the navigation yet. */
    private var pendingLink by mutableStateOf<DeepLink?>(null)
    private var startTab by mutableStateOf<TopLevel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) pendingLink = DeepLinks.parse(intent)
        lifecycleScope.launch { startTab = viewModel.startTab() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.onForeground() }
        }

        setContent {
            val tab = startTab
            if (tab != null) {
                VarunApp(
                    viewModel = viewModel,
                    startTab = tab,
                    pendingLink = pendingLink,
                    onLinkHandled = { pendingLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.parse(intent)?.let { pendingLink = it }
    }
}
