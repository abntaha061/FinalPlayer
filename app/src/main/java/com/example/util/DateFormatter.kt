// File: /app/src/main/java/com/example/util/DateFormatter.kt
package com.example.util

import android.content.Context
import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatter {
    fun formatDate(dateAddedSeconds: Long, context: Context): String {
        // Handle milliseconds or seconds conversion appropriately
        val dateMs = dateAddedSeconds * 1000L
        val date = Date(dateMs)
        
        val targetCalendar = Calendar.getInstance().apply { time = date }
        val nowCalendar = Calendar.getInstance()
        
        // 1. Same Day (Today)
        val isToday = targetCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
                targetCalendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR)
                
        if (isToday) {
            return context.getString(R.string.today)
        }
        
        // 2. Yesterday
        val yesterdayCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = targetCalendar.get(Calendar.YEAR) == yesterdayCalendar.get(Calendar.YEAR) &&
                targetCalendar.get(Calendar.DAY_OF_YEAR) == yesterdayCalendar.get(Calendar.DAY_OF_YEAR)
                
        if (isYesterday) {
            return context.getString(R.string.yesterday)
        }
        
        // 3. Last 7 Days
        val diffMs = nowCalendar.timeInMillis - targetCalendar.timeInMillis
        val diffDays = (diffMs / (1000L * 60 * 60 * 24)).toInt()
        
        if (diffDays in 2..7) {
            return String.format(Locale.getDefault(), context.getString(R.string.days_ago), diffDays)
        }
        
        // 4. Older -> actual date like "22 يونيو" or "Jun 22"
        val isAr = Locale.getDefault().language == "ar"
        val pattern = if (isAr) "d MMMM" else "MMM d"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(date)
    }
}
