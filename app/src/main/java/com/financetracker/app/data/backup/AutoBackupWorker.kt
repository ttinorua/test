package com.financetracker.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.FileNotFoundException

/** Writes the weekly automatic backup (see [AutoBackupSettings]) over the file the user picked. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uri = AutoBackupSettings.backupUri ?: return Result.success()
        val password = when (val stored = AutoBackupSettings.password()) {
            AutoBackupSettings.Password.None -> null
            is AutoBackupSettings.Password.Set -> stored.chars
            AutoBackupSettings.Password.Unavailable -> {
                AutoBackupSettings.recordFailure(
                    "The backup password can't be read on this phone anymore. Turn automatic backup off and on again."
                )
                return Result.failure()
            }
        }
        return try {
            FullBackup.writeTo(applicationContext, uri, password)
            AutoBackupSettings.recordSuccess()
            Result.success()
        } catch (e: SecurityException) {
            AutoBackupSettings.recordFailure("No longer allowed to write the backup file. Choose the location again.")
            Result.failure()
        } catch (e: FileNotFoundException) {
            AutoBackupSettings.recordFailure("The backup file was moved or deleted. Choose the location again.")
            Result.failure()
        } catch (e: Exception) {
            AutoBackupSettings.recordFailure("Backup failed: ${e.message ?: e.javaClass.simpleName}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            password?.fill('\u0000')
        }
    }
}
