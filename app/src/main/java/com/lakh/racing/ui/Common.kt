package com.lakh.racing.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakh.racing.AppContainer
import com.lakh.racing.RacingApp
import com.lakh.racing.data.model.PoiType
import java.util.Locale

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as RacingApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    else -> "${bytes / 1000} KB"
}

fun compass(bearingDeg: Double): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((bearingDeg % 360) + 360 + 22.5) / 45).toInt() % 8]
}

val PoiType.icon: ImageVector
    get() = when (this) {
        PoiType.REPAIR -> Icons.Filled.Build
        PoiType.FUEL -> Icons.Filled.LocalGasStation
        PoiType.MEDICAL -> Icons.Filled.LocalHospital
        PoiType.SHOP -> Icons.Filled.ShoppingCart
        PoiType.OTHER -> Icons.Filled.Place
    }

val PoiType.color: Color
    get() = when (this) {
        PoiType.REPAIR -> Color(0xFFFF9800)
        PoiType.FUEL -> Color(0xFF2196F3)
        PoiType.MEDICAL -> Color(0xFFE53935)
        PoiType.SHOP -> Color(0xFF8E24AA)
        PoiType.OTHER -> Color(0xFF9E9E9E)
    }

val PoiType.label: String
    get() = when (this) {
        PoiType.REPAIR -> "Repair"
        PoiType.FUEL -> "Fuel"
        PoiType.MEDICAL -> "Medical"
        PoiType.SHOP -> "Shop"
        PoiType.OTHER -> "Other"
    }
