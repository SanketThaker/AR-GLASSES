package com.example.arglasses

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallState(
    val status: String = "IDLE",
    val callerName: String? = null,
    val phoneNumber: String? = null
)

class CallReceiver : BroadcastReceiver() {

    companion object {
        private val _callStateFlow = MutableStateFlow(CallState())
        val callStateFlow: StateFlow<CallState> = _callStateFlow.asStateFlow()

        // Store last known number and name for fallback
        private var lastNumber: String? = null
        private var lastName: String? = null
    }

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            // Try to get the incoming number
            var number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            Log.d("GLASS_CALL", "═══════════════════════════════════════")
            Log.d("GLASS_CALL", "Phone state changed: $state")
            Log.d("GLASS_CALL", "Number from intent: $number")
            Log.d("GLASS_CALL", "Last known number: $lastNumber")
            Log.d("GLASS_CALL", "Last known name: $lastName")

            when (state) {

                TelephonyManager.EXTRA_STATE_RINGING -> {

                    // Update last number if we got a new one
                    if (!number.isNullOrBlank()) {
                        lastNumber = number
                        Log.d("GLASS_CALL", "✅ Updated lastNumber to: $number")
                    } else {
                        // Fallback to last known number
                        number = lastNumber
                        Log.d("GLASS_CALL", "⚠️ Using fallback number: $number")
                    }

                    // Try to get contact name
                    val name = getContactName(context, number)

                    if (name != null) {
                        lastName = name
                        Log.d("GLASS_CALL", "✅ Contact name found: $name")
                    } else {
                        Log.d("GLASS_CALL", "⚠️ No contact name found")
                    }

                    // Determine what to display
                    val displayName = when {
                        // If we have a contact name, use it
                        name != null -> name
                        // If we have a number but no name, show the number
                        number != null -> number
                        // Last resort: Unknown
                        else -> "Unknown"
                    }

                    Log.d("GLASS_CALL", "📱 RINGING - Display: $displayName, Number: $number")

                    _callStateFlow.value = CallState(
                        status = "RINGING",
                        callerName = displayName,
                        phoneNumber = number
                    )
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d("GLASS_CALL", "📞 OFFHOOK (call active)")

                    // Preserve the caller info from RINGING state
                    val currentState = _callStateFlow.value
                    _callStateFlow.value = currentState.copy(
                        status = "ON_CALL"
                    )

                    Log.d("GLASS_CALL", "ON_CALL - Name: ${currentState.callerName}, Number: ${currentState.phoneNumber}")
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d("GLASS_CALL", "📵 IDLE (call ended)")

                    _callStateFlow.value = CallState("IDLE", null, null)

                    // Clear last known info after call ends
                    lastNumber = null
                    lastName = null
                    Log.d("GLASS_CALL", "🧹 Cleared last known number and name")
                }
            }

            Log.d("GLASS_CALL", "Final state: ${_callStateFlow.value}")
            Log.d("GLASS_CALL", "═══════════════════════════════════════")
        }
    }

    @SuppressLint("Range")
    private fun getContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) {
            Log.d("GLASS_CALL_LOOKUP", "❌ Phone number is null or blank")
            return null
        }

        Log.d("GLASS_CALL_LOOKUP", "🔍 Looking up contact for: $phoneNumber")

        var contactName: String? = null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    )
                    Log.d("GLASS_CALL_LOOKUP", "✅ Contact found: $contactName")
                } else {
                    Log.d("GLASS_CALL_LOOKUP", "⚠️ No contact found for: $phoneNumber")
                }
            } ?: run {
                Log.d("GLASS_CALL_LOOKUP", "❌ Query returned null cursor")
            }
        } catch (e: SecurityException) {
            Log.e("GLASS_CALL_LOOKUP", "❌ SecurityException: Missing READ_CONTACTS permission")
        } catch (e: Exception) {
            Log.e("GLASS_CALL_LOOKUP", "❌ Error reading contacts: ${e.message}")
            e.printStackTrace()
        }

        return contactName
    }
}
