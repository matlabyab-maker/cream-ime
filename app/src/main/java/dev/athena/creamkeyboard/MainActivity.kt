package dev.athena.creamkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            setBackgroundColor(0xFFF2F0E8.toInt())
        }
        val heading = TextView(this).apply {
            text = "کیبورد کرم"
            textSize = 25f
            setTextColor(0xFF263B4C.toInt())
            gravity = Gravity.CENTER
        }
        page.addView(heading, LinearLayout.LayoutParams(-1, dp(54)))
        val intro = TextView(this).apply {
            text = "برای شروع، کیبورد را در تنظیمات اندروید فعال و سپس انتخاب کن. برای آزمایش، در کادر پایین تایپ کن."
            textSize = 16f
            setTextColor(0xFF30343A.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(intro, LinearLayout.LayoutParams(-1, dp(84)))
        page.addView(button("۱) فعال‌کردن کیبورد") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        page.addView(button("۲) انتخاب کیبورد") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })
        val edit = EditText(this).apply {
            hint = "اینجا تایپ کن…"
            textSize = 18f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val editParams = LinearLayout.LayoutParams(-1, 0, 1f)
        editParams.setMargins(0, dp(14), 0, dp(8))
        page.addView(edit, editParams)
        val privacy = TextView(this).apply {
            text = "حریم خصوصی: این نمونه مجوز اینترنت ندارد و متن را به سرور نمی‌فرستد. در فیلدهای گذرواژه پیشنهاد و تاریخچه غیرفعال است."
            textSize = 13f
            setTextColor(0xFF53585C.toInt())
        }
        page.addView(privacy, LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(page)
    }

    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        setOnClickListener { click() }
    }
}
