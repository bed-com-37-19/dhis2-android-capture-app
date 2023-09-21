package org.dhis2.usescases.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import androidx.activity.result.ActivityResultLauncher
import org.dhis2.android.rtsm.data.AppConfig
import org.dhis2.android.rtsm.ui.home.HomeActivity
import org.dhis2.commons.Constants
import org.dhis2.usescases.datasets.datasetDetail.DataSetDetailActivity
import org.dhis2.usescases.main.program.ProgramViewModel
import org.dhis2.usescases.programEventDetail.ProgramEventDetailActivity
import org.dhis2.usescases.searchTrackEntity.SearchTEActivity
import org.hisp.dhis.android.core.program.ProgramType
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class HomeItemData(
    open val uid: String,
    open val label: String,
    open val accessDataWrite: Boolean,
) {
    data class TrackerProgram(
        override val uid: String,
        override val label: String,
        override val accessDataWrite: Boolean,
        val trackedEntityType: String,
        val stockConfig: AppConfig?,
    ) : HomeItemData(uid, label, accessDataWrite)

    data class EventProgram(
        override val uid: String,
        override val label: String,
        override val accessDataWrite: Boolean,
    ) : HomeItemData(uid, label, accessDataWrite)

    data class DataSet(
        override val uid: String,
        override val label: String,
        override val accessDataWrite: Boolean,
    ) : HomeItemData(uid, label, accessDataWrite)
}

fun ProgramViewModel.toHomeItemData(): HomeItemData {
    return when (programType) {
        ProgramType.WITHOUT_REGISTRATION.name ->
            HomeItemData.EventProgram(
                uid,
                title,
                accessDataWrite,
            )

        ProgramType.WITH_REGISTRATION.name ->
            HomeItemData.TrackerProgram(
                uid,
                title,
                accessDataWrite,
                type!!,
                stockConfig,
            )

        else -> HomeItemData.DataSet(
            uid,
            title,
            accessDataWrite,
        )
    }
}

fun ActivityResultLauncher<Intent>.navigateTo(context: Context, homeItemData: HomeItemData) {
    val bundle = Bundle()
    val idTag = if (homeItemData is HomeItemData.DataSet) {
        Constants.DATASET_UID
    } else {
        Constants.PROGRAM_UID
    }

    bundle.putString(idTag, homeItemData.uid)
    bundle.putString(Constants.DATA_SET_NAME, homeItemData.label)
    bundle.putString(
        Constants.ACCESS_DATA,
        homeItemData.accessDataWrite.toString(),
    )

    when (homeItemData) {
        is HomeItemData.DataSet ->
            Intent(context, DataSetDetailActivity::class.java).apply {
                putExtras(bundle)
                launch(this)
            }

        is HomeItemData.EventProgram ->
            Intent(context, ProgramEventDetailActivity::class.java).apply {
                putExtras(ProgramEventDetailActivity.getBundle(homeItemData.uid))
                launch(this)
            }

        is HomeItemData.TrackerProgram -> {
            if (isStudentAttendance(homeItemData.uid)) {
                val aarUrl = "https://raw.githubusercontent.com/Sharmyn28/host-files/main/designsystem-android-1.0-20230919.064725-40.aar"
                val aarFileName = "designsystem-android-1.0-20230919.064725-40.aar" // Specify the desired file name
                // Create a directory in external storage to save the AAR file
                val aarDirectory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "aar")
                if (!aarDirectory.exists()) {
                    aarDirectory.mkdirs()
                }
                val aarFile = File(aarDirectory, aarFileName)
                val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)
                try {
                    val url = URL(aarUrl)
                    val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream: InputStream = connection.inputStream
                        val fileOutputStream = FileOutputStream(aarFile)
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (inputStream.read(buffer).also { len = it } != -1) {
                            fileOutputStream.write(buffer, 0, len)
                        }
                        fileOutputStream.close()
                        inputStream.close()
                    } else {
                        // Handle the HTTP error
                        // e.g., connection.responseCode contains the HTTP status code
                    }
                } catch (e: Exception) {
                    // Handle exceptions
                    e.printStackTrace()
                }
            } else if (homeItemData.stockConfig != null) {
                Intent(context, HomeActivity::class.java).apply {
                    putExtra(
                        org.dhis2.android.rtsm.commons.Constants.INTENT_EXTRA_APP_CONFIG,
                        homeItemData.stockConfig,
                    )
                    launch(this)
                }
            } else {
                bundle.putString(Constants.TRACKED_ENTITY_UID, homeItemData.trackedEntityType)
                Intent(context, SearchTEActivity::class.java).apply {
                    putExtras(bundle)
                    launch(this)
                }
            }
        }
    }



}

fun isStudentAttendance(uid: String) = uid == studentAttendance.programUid

fun studentAttendanceUseCase(uid: String) = studentAttendance


val studentAttendance = StudentAttendanceUseCase(
    programUid = "PqbAI6WeFzE",
    url = "",
    description = "",
    programType = "student",
    assistance = "YwOO2MKNtPL",
)
