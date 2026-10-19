package com.siteledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import com.siteledger.data.AppDatabase
import com.siteledger.data.LedgerRepository
import com.siteledger.ui.SiteLedgerApp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val repository = LedgerRepository(AppDatabase.get(this).ledgerDao())
        setContent {
            val colors = lightColorScheme(
                primary = Color(0xFF4A6CF7),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE3EEFD),
                onPrimaryContainer = Color(0xFF2E5CA6),
                secondary = Color(0xFF22C55E),
                tertiary = Color(0xFFF59E0B),
                background = Color.White,
                onBackground = Color(0xFF1A1D29),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF1A1D29),
                surfaceVariant = Color(0xFFF1F2F6),
                onSurfaceVariant = Color(0xFF9397A3),
                outline = Color(0xFFE8EAF0),
                error = Color(0xFFBA1A1A)
            )
            MaterialTheme(
                colorScheme = colors,
                shapes = Shapes(
                    small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(22.dp),
                    large = RoundedCornerShape(30.dp)
                )
            ) { SiteLedgerApp(repository) }
        }
    }
}

