package com.stinkyweasel.spacewise.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stinkyweasel.spacewise.data.repository.StorageStatsRepository
import com.stinkyweasel.spacewise.ui.screens.*
import com.stinkyweasel.spacewise.viewmodel.*
import java.net.URLDecoder
import java.net.URLEncoder

object SpaceWiseDestinations {
    const val DASHBOARD = "dashboard"
    const val PERMISSIONS = "permissions"
    const val BREAKDOWN = "breakdown"
    const val APP_LIST = "app_list"
    const val CATEGORY_DETAIL = "category_detail/{categoryName}"
    
    fun createCategoryDetailRoute(categoryName: String): String {
        val encodedName = URLEncoder.encode(categoryName, "UTF-8")
        return "category_detail/$encodedName"
    }
}

@Composable
fun SpaceWiseNavGraph(
    navController: NavHostController = rememberNavController(),
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    
    // Create the repository and viewmodel factory once
    val repository = remember { StorageStatsRepository(context.applicationContext) }
    val factory = remember { SpaceWiseViewModelFactory(repository) }

    NavHost(
        navController = navController,
        startDestination = SpaceWiseDestinations.DASHBOARD
    ) {
        composable(SpaceWiseDestinations.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = dashboardViewModel,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
                onNavigateToPermissions = { navController.navigate(SpaceWiseDestinations.PERMISSIONS) },
                onNavigateToBreakdown = { navController.navigate(SpaceWiseDestinations.BREAKDOWN) },
                onNavigateToAppList = { navController.navigate(SpaceWiseDestinations.APP_LIST) },
                onNavigateToCategoryDetail = { categoryName ->
                    navController.navigate(SpaceWiseDestinations.createCategoryDetailRoute(categoryName))
                }
            )
        }

        composable(SpaceWiseDestinations.PERMISSIONS) {
            PermissionsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(SpaceWiseDestinations.BREAKDOWN) {
            val breakdownViewModel: BreakdownViewModel = viewModel(factory = factory)
            BreakdownScreen(
                viewModel = breakdownViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAppList = { navController.navigate(SpaceWiseDestinations.APP_LIST) },
                onNavigateToCategoryDetail = { categoryName ->
                    navController.navigate(SpaceWiseDestinations.createCategoryDetailRoute(categoryName))
                }
            )
        }

        composable(SpaceWiseDestinations.APP_LIST) {
            val appListViewModel: AppListViewModel = viewModel(factory = factory)
            AppListScreen(
                viewModel = appListViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPermissions = { navController.navigate(SpaceWiseDestinations.PERMISSIONS) }
            )
        }

        composable(
            route = SpaceWiseDestinations.CATEGORY_DETAIL,
            arguments = listOf(
                navArgument("categoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
            val categoryName = URLDecoder.decode(encodedCategoryName, "UTF-8")
            
            val categoryDetailViewModel: CategoryDetailViewModel = viewModel(factory = factory)
            CategoryDetailScreen(
                categoryName = categoryName,
                viewModel = categoryDetailViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
