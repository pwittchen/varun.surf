package surf.varun.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import surf.varun.core.navigation.Navigator
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.TopLevel
import surf.varun.core.navigation.root

/**
 * One back stack per bottom-bar tab (spec 5.1: tab stacks are preserved). What is
 * displayed is the start tab's stack with the current tab's stack on top, so back
 * from another tab's root lands on the start tab, and back from there leaves.
 */
@Stable
class AppNavState(
    val startTab: TopLevel,
    initialTab: TopLevel,
    private val stacks: Map<TopLevel, SnapshotStateList<Route>>,
) : Navigator {
    var currentTab by mutableStateOf(initialTab)
        private set

    val visibleStack: List<Route>
        get() {
            val start = stacks.getValue(startTab)
            return if (currentTab == startTab) start else start + stacks.getValue(currentTab)
        }

    fun stack(tab: TopLevel): List<Route> = stacks.getValue(tab)

    override fun navigate(route: Route) {
        val stack = stacks.getValue(currentTab)
        if (stack.lastOrNull() != route) stack.add(route)
    }

    override fun switchTo(tab: TopLevel, route: Route?) {
        val stack = stacks.getValue(tab)
        if (route != null) {
            stack.clear()
            stack.add(route)
        } else if (tab == currentTab && stack.size > 1) {
            // Re-selecting the current tab pops it to its root
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        currentTab = tab
    }

    override fun back() {
        val stack = stacks.getValue(currentTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != startTab -> currentTab = startTab
        }
    }

    fun handle(link: DeepLink) {
        if (link.replaceRoot && link.route != null) {
            switchTo(link.tab, link.route)
            return
        }
        switchTo(link.tab)
        link.route?.let(::navigate)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(Route.serializer())

        fun saver(): Saver<AppNavState, List<String>> = Saver(
            save = { state ->
                listOf(state.startTab.name, state.currentTab.name) +
                    TopLevel.entries.map { json.encodeToString(serializer, state.stacks.getValue(it).toList()) }
            },
            restore = { saved ->
                runCatching {
                    val stacks = TopLevel.entries.mapIndexed { index, tab ->
                        tab to mutableStateListOf<Route>().apply { addAll(json.decodeFromString(serializer, saved[index + 2])) }
                    }.toMap()
                    AppNavState(TopLevel.valueOf(saved[0]), TopLevel.valueOf(saved[1]), stacks)
                }.getOrNull()
            },
        )

        fun create(startTab: TopLevel) = AppNavState(
            startTab = startTab,
            initialTab = startTab,
            stacks = TopLevel.entries.associateWith { mutableStateListOf(it.root) },
        )
    }
}

@Composable
fun rememberAppNavState(startTab: TopLevel): AppNavState =
    rememberSaveable(saver = AppNavState.saver()) { AppNavState.create(startTab) }
