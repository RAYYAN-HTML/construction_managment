package com.siteledger.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseResetMigrationTest {
    private val helper by lazy {
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )
    }

    @Test
    fun versionTwoDatabaseResetsIntoEmptyLedgerSchema() {
        val databaseName = "ledger-migration-test"
        helper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO projects (id, reference, name, clientName, siteAddress, contractAmountMinor, budgetAmountMinor, startDate, expectedCompletionDate, status, createdAt) VALUES ('old-site', 'OLD', 'Old site', '', '', 0, 0, '2026-01-01', NULL, 'ACTIVE', 0)")
            execSQL("INSERT INTO workers (id, fullName, phone, trade, dailyWageMinor, active, joinedOn) VALUES ('old-worker', 'Old worker', NULL, '', 10000, 1, '2026-01-01')")
            close()
        }

        val db = helper.runMigrationsAndValidate(databaseName, 3, true, AppDatabase.MIGRATION_2_3)
        try {
            assertEquals(0, db.query("SELECT COUNT(*) FROM workers").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM attendance").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM counterparties").use { it.moveToFirst(); it.getInt(0) })
            assertFalse(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='projects'").use { it.moveToFirst() })
            assertFalse(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='materials'").use { it.moveToFirst() })
        } finally {
            db.close()
        }
    }
}
