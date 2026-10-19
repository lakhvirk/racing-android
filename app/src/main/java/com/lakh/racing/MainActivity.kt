package com.lakh.racing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lakh.racing.ui.drive.DriveScreen
import com.lakh.racing.ui.pois.PoiScreen
import com.lakh.racing.ui.prepare.PrepareScreen
import com.lakh.racing.ui.races.RaceListScreen
import com.lakh.racing.ui.theme.RacingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RacingTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "races") {
        composable("races") {
            RaceListScreen(onOpenRace = { nav.navigate("race/$it") })
        }
        composable("race/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            PrepareScreen(
                raceId = id,
                onBack = { nav.popBackStack() },
                onStart = { nav.navigate("drive/$id") },
                onPois = { nav.navigate("pois/$id") },
            )
        }
        composable("drive/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            DriveScreen(raceId = id, onBack = { nav.popBackStack() }, onPois = { nav.navigate("pois/$id") })
        }
        composable("pois/{id}") { entry ->
            PoiScreen(raceId = entry.arguments?.getString("id")!!, onBack = { nav.popBackStack() })
        }
    }
}
