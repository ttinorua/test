package com.financetracker.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.financetracker.app.data.backup.cloud.CloudException
import com.financetracker.app.data.backup.cloud.GoogleDriveBackup
import com.financetracker.app.data.backup.cloud.OneDriveBackup
import java.io.FileNotFoundException

/** Writes the weekly automatic backup (see [AutoBackupSettings]) to the phone, OneDrive or Google
 * Drive, replacing the previous one. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val destination = AutoBackupSettings.destination ?: return Result.success()
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
            when (destination) {
                BackupDestination.PHONE -> {
                    val uri = AutoBackupSettings.backupUri ?: return Result.success()
                    FullBackup.writeTo(applicationContext, uri, password)
                }
                BackupDestination.ONEDRIVE -> {
                    val refreshToken = AutoBackupSettings.oneDriveRefreshToken()
                        ?: throw CloudException("Sign in to OneDrive again: turn automatic backup off and on.")
                    val tokens = OneDriveBackup.refresh(refreshToken)
                    AutoBackupSettings.saveOneDriveRefreshToken(tokens.refreshToken)
                    OneDriveBackup.upload(tokens.accessToken, AutoBackupSettings.FILE_NAME, FullBackup.encode(applicationContext, password))
                }
                BackupDestination.GOOGLE_DRIVE -> {
                    val token = GoogleDriveBackup.accessToken(applicationContext)
                    AutoBackupSettings.googleDriveFileId = GoogleDriveBackup.upload(
                        token,
                        AutoBackupSettings.googleDriveFileId,
                        AutoBackupSettings.FILE_NAME,
                        FullBackup.encode(applicationContext, password)
                    )
                }
            }
            AutoBackupSettings.recordSuccess()
            Result.success()
        } catch (e: CloudException) {
            AutoBackupSettings.recordFailure(e.message ?: "Upload failed.")
            // 400/401 mean the sign-in is no longer valid — retrying won't help.
            if (e.status in 400..401 || runAttemptCount >= 3) Result.failure() else Result.retry()
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
