package com.example.arglasses

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log

class CallManager(private val context: Context) {

    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @SuppressLint("MissingPermission")
    fun acceptCall() {
        try {
            Log.d("CALL_FLOW", "acceptCall() TRIGGERED")

            // Method 1: Headsethook (works like a Bluetooth headset button)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            val downEvent = android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK
            )
            val upEvent = android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK
            )

            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            Log.d("CALL_FLOW", "HEADSETHOOK dispatched")

            // Method 2: Telecom fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    telecomManager.acceptRingingCall()
                    Log.d("CALL_FLOW", "acceptRingingCall() also called")
                } catch (e: Exception) {
                    Log.e("CALL_FLOW", "Telecom fallback failed: ${e.message}")
                }
            }

            enableSpeaker()

        } catch (e: Exception) {
            Log.e("CALL_FLOW", "ERROR in acceptCall: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                disableSpeaker()
            } else {
                Log.e("GLASS_CALL", "Reject call not supported below API 28")
            }
        } catch (e: Exception) {
            Log.e("GLASS_CALL", "Error rejecting call", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun makeCall(phoneNumber: String) {
        try {
            val uri = Uri.parse("tel:$phoneNumber")
            val intent = Intent(Intent.ACTION_CALL, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            enableSpeaker()
        } catch (e: Exception) {
            Log.e("GLASS_CALL", "Error making call", e)
        }
    }

    fun enableSpeaker() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.e("GLASS_CALL", "Error enabling speaker", e)
        }
    }

    fun disableSpeaker() {
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e("GLASS_CALL", "Error disabling speaker", e)
        }
    }
}
