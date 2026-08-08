package ch.weissheimer.poly.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.weissheimer.poly.ui.recents.RecentsScreen
import ch.weissheimer.poly.ui.viewer.ViewerScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val RECENTS = "recents"
    const val VIEWER = "viewer/{uri}"

    fun viewer(uri: Uri): String = "viewer/${Uri.encode(uri.toString())}"
}

@Composable
fun PolyNavHost(
    incomingDocument: StateFlow<Uri?>,
    onIncomingConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        incomingDocument.collect { uri ->
            if (uri != null) {
                navController.navigate(Routes.viewer(uri))
                onIncomingConsumed()
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.RECENTS) {
        composable(Routes.RECENTS) {
            RecentsScreen(
                onOpenDocument = { uri -> navController.navigate(Routes.viewer(uri)) },
            )
        }
        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri").orEmpty()
            ViewerScreen(
                uri = Uri.parse(uriString),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
