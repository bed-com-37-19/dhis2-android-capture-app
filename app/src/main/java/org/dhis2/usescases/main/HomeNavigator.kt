package org.dhis2.usescases.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import dalvik.system.DexClassLoader
import org.dhis2.R
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
                val attendance = studentAttendanceUseCase(homeItemData.uid)

                // Define the directory where you want to extract the DEX file
                val dexOutputDir = context.getDir("dex", Context.MODE_PRIVATE).absolutePath

// Get the input stream of the AAR file from the raw resources
                val aarInputStream: InputStream = context.resources.openRawResource(R.raw.mylibrary_debug)
                val aarFile = File(dexOutputDir, "mylibrary_debug.aar") // Adjust the file name as needed

// Copy the AAR file from resources to the internal storage
                val fos = FileOutputStream(aarFile)
                val buffer = ByteArray(1024)
                var length: Int
                while (aarInputStream.read(buffer).also { length = it } > 0) {
                    fos.write(buffer, 0, length)
                }
                fos.close()
                aarInputStream.close()
                val classLoader = context.classLoader

// Create a DexClassLoader instance
                val dexClassLoader = DexClassLoader(
                    aarFile.absolutePath, // Path to the AAR file in internal storage
                    dexOutputDir, // Directory to store extracted DEX files
                    null, // Library search path (set to null if not needed)
                    classLoader // Parent class loader
                )

                val loadedClass = dexClassLoader.loadClass("org.dhis2.mylibrary.MainActivityPOC")

                // Replace "org.dhis2.mylibrary.MainActivityPOC" with the correct class name

                // Now you can use the loadedClass to access methods and instantiate objects.
                // For example:
                val instance = loadedClass.newInstance()

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
