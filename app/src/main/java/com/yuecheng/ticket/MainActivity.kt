package com.yuecheng.ticket

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yuecheng.ticket.data.ThemePrefs
import com.yuecheng.ticket.ui.change.ChangeScreen
import com.yuecheng.ticket.ui.home.HomeScreen
import com.yuecheng.ticket.ui.login.LoginScreen
import com.yuecheng.ticket.ui.order.OrderDetailScreen
import com.yuecheng.ticket.ui.order.OrderFillScreen
import com.yuecheng.ticket.ui.order.OrdersScreen
import com.yuecheng.ticket.ui.passenger.PassengersScreen
import com.yuecheng.ticket.ui.picker.CityPickerScreen
import com.yuecheng.ticket.ui.profile.ProfileScreen
import com.yuecheng.ticket.ui.shift.ShiftScreen
import com.yuecheng.ticket.ui.theme.YcTheme
import com.yuecheng.ticket.ui.theme.applyWindowBackground

class MainActivity : ComponentActivity() {
    /** 快捷方式等外部入口要跳转的目的地;消费后置空 */
    private val pendingNav = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNav.value = intent?.getStringExtra("nav")
        // 首帧前就设好窗口底色,冷启动与页面转场都不闪白
        applyWindowBackground(ThemePrefs.dark.value)
        enableEdgeToEdge()
        setContent {
            val dark = ThemePrefs.dark.value
            LaunchedEffect(dark) {
                applyWindowBackground(dark)
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            YcTheme(dark) { AppNav(pendingNav) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNav.value = intent.getStringExtra("nav")
    }
}

@Composable
fun AppNav(pendingNav: MutableState<String?>) {
    val nav = rememberNavController()
    // 桌面快捷方式:查票回首页,订单直达订单页
    LaunchedEffect(pendingNav.value) {
        when (pendingNav.value) {
            "orders" -> nav.navigate("orders") { launchSingleTop = true }
            "home" -> nav.popBackStack("home", false)
        }
        pendingNav.value = null
    }
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
