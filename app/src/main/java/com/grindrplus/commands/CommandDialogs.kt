package com.grindrplus.commands

import android.app.AlertDialog
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatTextView
import com.grindrplus.GrindrPlus
import com.grindrplus.ui.Utils.copyToClipboard

object CommandDialogs {
    fun showTextDialog(
        title: String,
        content: String,
        copyLabel: String = title,
        scrollable: Boolean = false,
        textSize: Float = 14f,
    ) {
        GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
            val textView = AppCompatTextView(activity).apply {
                text = content
                this.textSize = textSize
                setTextColor(Color.WHITE)
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 20, 0, 0)
                }
            }

            val dialogView = if (scrollable) {
                ScrollView(activity).apply {
                    setPadding(60, 40, 60, 40)
                    addView(textView)
                }
            } else {
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    addView(textView)
                }
            }

            AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Copy") { _, _ ->
                    copyToClipboard(copyLabel, content)
                }
                .create()
                .show()
        }
    }
}
