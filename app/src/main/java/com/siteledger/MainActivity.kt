package com.siteledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import com.siteledger.data.AppDatabase
import com.siteledger.data.LedgerRepository
import com.siteledger.ui.SiteLedgerApp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        androidx.activity.enableEdgeToEdge()
        val repository = LedgerRepository(AppDatabase.get(this).ledgerDao())
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val colors = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (dark) darkColorScheme() else lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
                    secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
                    tertiary = androidx.compose.ui.graphics.Color(0xFF7D5260),
                    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
                    background = androidx.compose.ui.graphics.Color(0xFFF7F2FA)
                )
            }
            MaterialTheme(
                colorScheme = colors,
                shapes = Shapes(
                    small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(20.dp),
                    large = RoundedCornerShape(28.dp)
                )
            ) { SiteLedgerApp(repository) }
        }
    }
}

