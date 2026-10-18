package com.siteledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.siteledger.data.AppDatabase
import com.siteledger.data.LedgerRepository
import com.siteledger.ui.SiteLedgerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val repository = LedgerRepository(AppDatabase.get(this).ledgerDao())
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF17324D), secondary = androidx.compose.ui.graphics.Color(0xFFE8812A))) { SiteLedgerApp(repository) } }
    }
}

