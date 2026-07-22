package com.example.arglasses

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class GlassNotificationListener : NotificationListenerService() {

    companion object {
        var onNotificationReceived: ((String) -> Unit)? = null
        var lastNavTime = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = (extras.getString("android.title") ?: "").trim()
        val text = (extras.getCharSequence("android.text")?.toString() ?: "").trim()
        val packageName = sbn.packageName ?: return

        if (packageName.contains("phone", ignoreCase = true) ||
            packageName.contains("call", ignoreCase = true) ||
            packageName.contains("telecom", ignoreCase = true) ||
            packageName.contains("dialer", ignoreCase = true)) {
            Log.d("NOTIF_DEBUG", "Package: $packageName | Title: '$title' | Text: '$text'")
        }

        if (title.isBlank() && text.isBlank()) return

        // =========================================
        // MAPMYINDIA / MAPPLS NAVIGATION
        // =========================================
        if (packageName.contains("mappls", ignoreCase = true) ||
            packageName.contains("mapmyindia", ignoreCase = true) ||
            packageName.contains("com.mmi.maps", ignoreCase = true)) {

            val combinedLower = "$title $text".lowercase()

            // ARRIVAL
            if (combinedLower.contains("arrived") || combinedLower.contains("destination") ||
                combinedLower.contains("you have reached")) {
                onNotificationReceived?.invoke("NAV:END")
                return
            }

            // ── DIRECTION ────────────────────────────────────────────
            val direction = when {
                combinedLower.contains("u-turn") || combinedLower.contains("uturn")            -> "UTurn"
                combinedLower.contains("slight right") || combinedLower.contains("keep right") -> "UR"
                combinedLower.contains("slight left")  || combinedLower.contains("keep left")  -> "UL"
                combinedLower.contains("turn right")                                           -> "R"
                combinedLower.contains("turn left")                                            -> "L"
                combinedLower.contains("continue right")                                       -> "UR"
                combinedLower.contains("continue left")                                        -> "UL"
                combinedLower.contains("straight") || combinedLower.contains("continue") ||
                        combinedLower.contains("head")                                         -> "U"
                else                                                                           -> "U"
            }

            // ── STREET NAME ──────────────────────────────────────────
            var safeStreet = title
                .replace(Regex("^\\d+\\.?\\d*\\s?(?:m|km)\\s*[·•]?\\s*"), "")
                .trim()

            val dirPhrases = listOf(
                "(?i)continue\\s+(left|right)\\s+onto\\s+",
                "(?i)continue\\s+(left|right)\\s+on\\s+",
                "(?i)continue\\s+(left|right)\\s+",
                "(?i)continue\\s+onto\\s+",
                "(?i)continue\\s+on\\s+",
                "(?i)continue\\s+",
                "(?i)turn\\s+right\\s+onto\\s+",
                "(?i)turn\\s+left\\s+onto\\s+",
                "(?i)turn\\s+right\\s+on\\s+",
                "(?i)turn\\s+left\\s+on\\s+",
                "(?i)turn\\s+right\\s+",
                "(?i)turn\\s+left\\s+",
                "(?i)slight\\s+right\\s+onto\\s+",
                "(?i)slight\\s+left\\s+onto\\s+",
                "(?i)slight\\s+right\\s+",
                "(?i)slight\\s+left\\s+",
                "(?i)keep\\s+right\\s+",
                "(?i)keep\\s+left\\s+",
                "(?i)u-?turn\\s+onto\\s+",
                "(?i)u-?turn\\s+",
                "(?i)head\\s+\\w+\\s+onto\\s+",
                "(?i)head\\s+\\w+\\s+on\\s+",
                "(?i)head\\s+"
            )
            for (phrase in dirPhrases) {
                safeStreet = safeStreet.replace(Regex(phrase), "")
            }
            safeStreet = safeStreet.replace(",", "").trim()

            if (safeStreet.isBlank() && text.isNotBlank()) {
                safeStreet = text
                    .replace(Regex("\\d+\\.?\\d*\\s?(?:m|km).*"), "")
                    .replace(Regex("\\d+\\s?min.*"), "")
                    .trim()
            }

            // ── DISTANCE ─────────────────────────────────────────────
            val immDistMatch = Regex("(\\d+\\.?\\d*\\s?(?:m|km))\\b").find(title)
            val immDist = immDistMatch?.value?.replace(" ", "") ?: ""

            val etaMatch     = Regex("(\\d{1,2}:\\d{2}\\s?(?:am|pm))", RegexOption.IGNORE_CASE).find(text)
            val timeMatch    = Regex("(\\d+)\\s?min").find(text)
            val remDistMatch = Regex("(\\d+\\.?\\d*\\s?(?:m|km))\\b").find(text)

            val remDistStr = remDistMatch?.value?.replace(" ", "") ?: ""
            val timeStr    = timeMatch?.value?.replace(" ", "") ?: ""

            val safeDistance = when {
                immDist.isNotBlank()    -> immDist
                remDistStr.isNotBlank() -> remDistStr
                timeStr.isNotBlank()    -> timeStr
                else                    -> ""
            }

            // ── ETA ───────────────────────────────────────────────────
            // "12:51 pm" -> "12:51pm", keep "am"/"pm" fully intact
            val etaStr = etaMatch?.value
                ?.replace(" ", "")   // remove space: "12:51 pm" -> "12:51pm"
                ?.replace("AM", "am")
                ?.replace("PM", "pm")
                ?.lowercase()        // normalise any mixed case
                ?: ""
            // Result: "12:51pm" — fits on 128px display

            // ── NEXT TURN ─────────────────────────────────────────────
            val thenDistMatch = Regex(
                "(?:then|after).{0,20}?(\\d+\\.?\\d*\\s?(?:m|km))\\b", RegexOption.IGNORE_CASE
            ).find(combinedLower)

            val thenDir = when {
                combinedLower.contains("then turn left")     -> "L"
                combinedLower.contains("then turn right")    -> "R"
                combinedLower.contains("then keep left")     -> "UL"
                combinedLower.contains("then keep right")    -> "UR"
                combinedLower.contains("then slight left")   -> "UL"
                combinedLower.contains("then slight right")  -> "UR"
                combinedLower.contains("after turn left")    -> "L"
                combinedLower.contains("after turn right")   -> "R"
                combinedLower.contains("after keep left")    -> "UL"
                combinedLower.contains("after keep right")   -> "UR"
                combinedLower.contains("after slight left")  -> "UL"
                combinedLower.contains("after slight right") -> "UR"
                else                                         -> ""
            }

            // If no explicit "then/after X m turn Y" phrase found,
            // fall back to the largest distance value in the text field.
            // e.g. "603 m • 2 min • 12:51 pm ETA" -> "603m"
            // This gives the rider the remaining route distance after
            // the current turn, which is the best available info.
            val thenDist = if (thenDistMatch != null) {
                thenDistMatch.groups[1]?.value?.replace(" ", "") ?: ""
            } else {
                val allMatches = Regex(
                    "(\\d+\\.?\\d*)\\s*(m|km)\\b", RegexOption.IGNORE_CASE
                ).findAll(text)
                val largest = allMatches.maxByOrNull { m ->
                    val v = m.groups[1]?.value?.toFloatOrNull() ?: 0f
                    val u = m.groups[2]?.value?.lowercase() ?: "m"
                    if (u == "km") v * 1000f else v
                }
                largest?.value?.replace(" ", "") ?: ""
            }

            // ── DEBOUNCE ──────────────────────────────────────────────
            val now = System.currentTimeMillis()
            if (now - lastNavTime < 1200) return
            lastNavTime = now

            // ── FILTER JUNK ───────────────────────────────────────────
            if (direction == "U" && safeDistance.isBlank() && safeStreet.isBlank()) return

            // ── SEND ──────────────────────────────────────────────────
            val formattedMessage = "NAV:$safeStreet|$safeDistance|$direction|$thenDist|$thenDir|$etaStr"

            Log.d("MMI_NAV_DEBUG", "════════════════════════════════")
            Log.d("MMI_NAV_DEBUG", "TITLE: $title")
            Log.d("MMI_NAV_DEBUG", "TEXT:  $text")
            Log.d("MMI_NAV_DEBUG", "SENDING TO ARDUINO: $formattedMessage")
            Log.d("MMI_NAV_DEBUG", "════════════════════════════════")

            onNotificationReceived?.invoke(formattedMessage)
            return
        }

        // =========================================
        // BLOCK PHONE APP NOTIFICATIONS
        // =========================================
        if (packageName.contains("phone", ignoreCase = true) ||
            packageName.contains("call", ignoreCase = true) ||
            packageName.contains("telecom", ignoreCase = true) ||
            packageName.contains("dialer", ignoreCase = true)) {
            return
        }

        // =========================================
        // NORMAL NOTIFICATIONS
        // =========================================
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) { packageName }

        val formattedMessage = if (title.isNotBlank()) {
            "$appName: $title:\n$text"
        } else {
            "$appName:\n$text"
        }.take(400)

        Log.d("GLASS_NOTIFY", formattedMessage)
        onNotificationReceived?.invoke(formattedMessage)
    }
}
