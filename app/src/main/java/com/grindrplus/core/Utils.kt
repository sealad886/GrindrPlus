package com.grindrplus.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.GrindrPlus.context
import com.grindrplus.GrindrPlus.httpClient
import com.grindrplus.GrindrPlus.isImportingSomething
import com.grindrplus.GrindrPlus.shouldTriggerAntiblock
import com.grindrplus.core.Constants.NEWLINE
import de.robv.android.xposed.XposedHelpers.callMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

object Utils {
    fun openChat(id: String) {
        val chatActivityInnerClass =
            GrindrPlus.loadClass("com.grindrapp.android.chat.presentation.ui.ChatActivityV2\$a")
        val chatArgsClass =
            GrindrPlus.loadClass("com.grindrapp.android.args.ChatArgs")
        val profileTypeClass =
            GrindrPlus.loadClass("com.grindrapp.android.ui.profileV2.model.ProfileType")
        val referrerTypeClass =
            GrindrPlus.loadClass("com.grindrapp.android.profile.domain.ReferrerType")
        val conversationMetadataClass =
            GrindrPlus.loadClass("com.grindrapp.android.chat.model.DirectConversationMetaData")

        val conversationMetadataInstance = conversationMetadataClass.constructors.first().newInstance(
            id,
            id.substringBefore(":"),
            id.substringAfter(":")
        )

        val profileType = profileTypeClass.getField("FAVORITES").get(null)
        val refererType = referrerTypeClass.getField("UNIFIED_CASCADE").get(null)

        val chatArgsInstance = chatArgsClass.constructors.first().newInstance(
            conversationMetadataInstance,
            "notification_chat_message", // str
            profileType,
            refererType,
            "0xDEADBEEF", // str2
            null,
            false,
            844
        )

        val method = chatActivityInnerClass.declaredMethods.find {
            it.parameterTypes.size == 2 && it.parameterTypes[1] == chatArgsClass
        }

        val intent = method?.invoke(
            null,
            context,
            chatArgsInstance
        ) as Intent?

        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun openProfile(id: String) {
        val referrerTypeClass =
            GrindrPlus.loadClass("com.grindrapp.android.profile.domain.ReferrerType")
        val referrerType = referrerTypeClass.getField("NOTIFICATION").get(null)
        val profilesActivityInnerClass =
            GrindrPlus.loadClass("com.grindrapp.android.ui.profileV2.ProfilesActivity\$a")

        Logger.i("ProfilesActivity inner class: $profilesActivityInnerClass")

        val method = profilesActivityInnerClass.declaredMethods.find {
            it.parameterTypes.size == 4 && it.parameterTypes[2] == referrerTypeClass
        }

        if (method == null) {
            Logger.e("Method not found in ProfilesActivity inner class.")
            return
        }

        val intent = method?.invoke(
            null,
            context,
            id,
            referrerType,
            referrerType
        ) as Intent?
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun calculateBMI(isMetric: Boolean, weight: Double, height: Double): Double {
        return if (isMetric) {
            weight / (height / 100).pow(2)
        } else {
            703 * weight / height.pow(2)
        }
    }

    fun w2n(isMetric: Boolean, weight: String): Double {
        return when {
            isMetric -> weight.substringBefore("kg").trim().toDouble()
            else -> weight.substringBefore("lbs").trim().toDouble()
        }
    }

    fun h2n(isMetric: Boolean, height: String): Double {
        return if (isMetric) {
            height.removeSuffix("cm").trim().toDouble()
        } else {
            val (feet, inches) = height.split("'").let {
                it[0].toDouble() to it[1].replace("\"", "").toDouble()
            }
            feet * 12 + inches
        }
    }

    fun safeGetField(obj: Any, fieldName: String): Any? {
        return try {
            obj::class.java.getDeclaredField(fieldName).apply {
                isAccessible = true
            }.get(obj)
        } catch (e: Exception) {
            null
        }
    }

    fun coordsToGeoHash(lat: Double, lon: Double, precision: Int = 12): String {
        return GrindrPlus.loadClass("ch.hsr.geohash.GeoHash")
            .getMethod("geoHashStringWithCharacterPrecision",
                Double::class.java, Double::class.java, Int::class.java)
            .invoke(null, lat, lon, precision) as String
    }

    @SuppressLint("SetTextI18n")
    fun showProgressDialog(
        context: Context,
        message: String,
        onCancel: () -> Unit,
        onRunInBackground: (updateProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) -> Unit,
        successMessage: String = "All blocks have been imported!",
        failureMessage: String = "Something went wrong. Please try again."
    ) {
        lateinit var dialog: AlertDialog

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }

        val textView = TextView(context).apply {
            text = "$message (0%)"
            textSize = 16f
            setPadding(20, 20, 20, 20)
        }

        val cancelButton = Button(context).apply {
            text = "Cancel"
            setOnClickListener {
                onCancel()
                dialog.dismiss()
            }
        }

        val backgroundButton = Button(context).apply {
            text = "Run in Background"
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(progressBar)
            addView(textView)
            addView(cancelButton)
            addView(backgroundButton)
        }

        dialog = AlertDialog.Builder(context)
            .setCancelable(false)
            .setView(container)
            .create()

        dialog.show()

        onRunInBackground({ progress ->
            progressBar.progress = progress
            textView.text = "$message ($progress%)"
        }) { success ->
            container.removeAllViews()

            val resultIcon = TextView(context).apply {
                text = if (success) "✅" else "❌"
                textSize = 40f
                setPadding(20, 20, 20, 20)
                gravity = android.view.Gravity.CENTER
            }

            val resultMessage = TextView(context).apply {
                text = if (success) successMessage else failureMessage
                textSize = 18f
                setPadding(20, 20, 20, 20)
                gravity = android.view.Gravity.CENTER
            }

            val closeButton = Button(context).apply {
                text = "Close"
                setOnClickListener {
                    dialog.dismiss()
                }
            }

            container.apply {
                addView(resultIcon)
                addView(resultMessage)
                addView(closeButton)
            }
        }
    }

    fun showWarningDialog(context: Context, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Warning")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Proceed") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .create()
            .show()
    }

    fun handleImports(activity: Activity) {
        val homeActivity = "com.grindrapp.android.ui.home.HomeActivity"

        if (activity.javaClass.name == homeActivity && !isImportingSomething) {
            val favoritesFile = context.getFileStreamPath("favorites_to_import.txt")
            val blocksFile = context.getFileStreamPath("blocks_to_import.txt")

            if (favoritesFile.exists() && blocksFile.exists()) {
                showWarningDialog(
                    context = activity,
                    message = "Favorites and Blocks import files detected. GrindrPlus will process the favorites list first. " +
                            "Blocks import will be done on the next app restart.",
                    onConfirm = {
                        launchImportWithThresholdCheck(
                            activity, favoritesFile, "favorites_import_threshold",
                            warnItemCount = 50, importType = ImportType.FAVORITES
                        )
                    },
                    onCancel = { Logger.i("Imports canceled by the user.") }
                )
            } else if (favoritesFile.exists()) {
                launchImportWithThresholdCheck(
                    activity, favoritesFile, "favorites_import_threshold",
                    warnItemCount = 50, importType = ImportType.FAVORITES
                )
            } else if (blocksFile.exists()) {
                launchImportWithThresholdCheck(
                    activity, blocksFile, "block_import_threshold",
                    warnItemCount = 100, importType = ImportType.BLOCKS
                )
            }
        }
    }

    private enum class ImportType { FAVORITES, BLOCKS }

    private fun launchImportWithThresholdCheck(
        activity: Activity,
        file: File,
        thresholdConfigKey: String,
        warnItemCount: Int,
        importType: ImportType
    ) {
        val threshold = (Config.get(thresholdConfigKey, "500") as String).toInt()
        val items = file.readLines()
        val typeName = importType.name.lowercase()

        if (items.size > warnItemCount && threshold < 1000) {
            showWarningDialog(
                context = activity,
                message = "High number of $typeName and low threshold detected. " +
                        "Continuing may result in your account being banned. Do you want to proceed?",
                onConfirm = { startBatchImport(activity, items, file, threshold, importType) },
                onCancel = {
                    isImportingSomething = false
                    Logger.i("${typeName.replaceFirstChar { it.uppercase() }} import canceled by the user.")
                }
            )
        } else {
            startBatchImport(activity, items, file, threshold, importType)
        }
    }

    private fun startBatchImport(
        activity: Activity,
        items: List<String>,
        file: File,
        threshold: Int,
        importType: ImportType
    ) {
        val typeName = importType.name.lowercase()
        val capitalizedName = typeName.replaceFirstChar { it.uppercase() }

        if (importType == ImportType.BLOCKS) {
            shouldTriggerAntiblock = false
        }

        try {
            showProgressDialog(
                context = activity,
                message = "Importing $typeName...",
                successMessage = "$capitalizedName import completed.",
                failureMessage = "$capitalizedName import failed.",
                onCancel = {
                    isImportingSomething = false
                    Logger.i("$capitalizedName import canceled by the user.")
                },
                onRunInBackground = { updateProgress, onComplete ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            items.forEachIndexed { index, id ->
                                when (importType) {
                                    ImportType.FAVORITES -> {
                                        val parts = id.split("|||")
                                        val profileId = parts.getOrNull(0) ?: ""
                                        val note = parts.getOrNull(1)?.replace(NEWLINE, "\n") ?: ""
                                        val phoneNumber = parts.getOrNull(2)?.replace(NEWLINE, "\n") ?: ""
                                        httpClient.favorite(profileId, silent = true, reflectInDb = false)
                                        if (note.isNotEmpty() || phoneNumber.isNotEmpty()) {
                                            httpClient.addProfileNote(profileId, note, phoneNumber, silent = true)
                                        }
                                    }
                                    ImportType.BLOCKS -> {
                                        httpClient.blockUser(id, silent = true, reflectInDb = false)
                                    }
                                }
                                file.writeText(items.drop(index + 1).joinToString("\n"))
                                val progress = ((index + 1) * 100) / items.size
                                updateProgress(progress)
                                Thread.sleep(threshold.toLong())
                            }

                            withContext(Dispatchers.Main) {
                                file.delete()
                                onComplete(true)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                val message = "An error occurred while importing $typeName: ${e.message ?: "Unknown error"}"
                                GrindrPlus.showToast(Toast.LENGTH_LONG, message)
                                Logger.apply {
                                    e(message)
                                    writeRaw(e.stackTraceToString())
                                }
                                onComplete(false)
                            }
                        } finally {
                            if (importType == ImportType.BLOCKS) {
                                shouldTriggerAntiblock = true
                            }
                            isImportingSomething = false
                        }
                    }
                }
            )
        } catch (e: Exception) {
            val message = "An error occurred while importing $typeName: ${e.message ?: "Unknown error"}"
            if (importType == ImportType.BLOCKS) {
                shouldTriggerAntiblock = true
            }
            GrindrPlus.showToast(Toast.LENGTH_LONG, message)
            Logger.apply {
                e(message)
                writeRaw(e.stackTraceToString())
            }
        }
    }
}
