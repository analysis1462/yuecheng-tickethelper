package com.yuecheng.ticket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yuecheng.ticket.ui.change.ChangeScreen
import com.yuecheng.ticket.ui.home.HomeScreen
import com.yuecheng.ticket.ui.login.LoginScreen
import com.yuecheng.ticket.ui.order.OrderFillScreen
import com.yuecheng.ticket.ui.order.OrderDetailScreen
import com.yuecheng.ticket.ui.order.OrdersScreen
import com.yuecheng.ticket.ui.passenger.PassengersScreen
import com.yuecheng.ticket.ui.picker.CityPickerScreen
import com.yuecheng.ticket.ui.profile.ProfileScreen
import com.yuecheng.ticket.ui.shift.ShiftScreen
import com.yuecheng.ticket.ui.theme.YcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = com.yuecheng.ticket.data.ThemePrefs.dark.value
            androidx.compose.runtime.LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                    else androidx.activity.SystemBarStyle.light(transparent, transparent),
                )
            }
            YcTheme(dark) { AppNav() }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "home",
        // 平滑切换:前进从右滑入,返回向右滑出,带淡入淡出
        enterTransition = {
            slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))
        },
        exitTransition = {
            slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut(tween(320))
        },
        popEnterTransition = {
            slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(320))
        },
        popExitTransition = {
            slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320))
        },
    ) {
        composable("home") { HomeScreen(nav) }
        composable("startPick") { CityPickerScreen(nav, pickingStart = true) }
        composable("endPick") { CityPickerScreen(nav, pickingStart = false) }
        composable("shifts") { ShiftScreen(nav) }
        composable("orderFill") { OrderFillScreen(nav) }
        composable("login") { LoginScreen(nav) }
        composable("passengers/{choose}") { entry ->
            PassengersScreen(nav, chooseMode = entry.arguments?.getString("choose") == "1")
        }
        composable("orders") { OrdersScreen(nav) }
        composable("profile") { ProfileScreen(nav) }
        composable("orderDetail/{orderId}") { entry ->
            OrderDetailScreen(nav, orderId = entry.arguments?.getString("orderId").orEmpty())
        }
        composable("change/{orderId}") { entry ->
            ChangeScreen(nav, orderId = entry.arguments?.getString("orderId").orEmpty())
        }
    }
}
