package com.worksoc.goaicoach.ui

internal fun formatBuildTime(rawBuildTime: String): String {
    return try {
        val parts = rawBuildTime.split(" ")
        if (parts.size == 2) {
            val dateParts = parts[0].split("-")
            if (dateParts.size == 3) {
                val yy = dateParts[0].takeLast(2)
                val mm = dateParts[1]
                val dd = dateParts[2]
                val time = parts[1].replace(":", "")
                "v$yy$mm$dd.$time"
            } else {
                "v$rawBuildTime"
            }
        } else {
            "v$rawBuildTime"
        }
    } catch (e: Exception) {
        "v$rawBuildTime"
    }
}
