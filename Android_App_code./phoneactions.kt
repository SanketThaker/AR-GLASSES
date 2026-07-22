package com.example.arglasses

import android.content.Context
import android.content.Intent
import android.net.Uri

class PhoneActionsAndroid(private val context: Context) {
    fun openDialer(number: String) {
        val i = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        context.startActivity(i)
    }

    fun openSms(number: String, text: String = "") {
        val i = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", text)
        }
        context.startActivity(i)
    }
}
