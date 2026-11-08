package com.lancefer.lancedrop.model

/**
 * Spécifications matérielles et logicielles authentiques de l'appareil Android.
 * Éradique toute valeur fictive ou hardcodée.
 */
data class DeviceInfo(
    val deviceName: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val deviceCodename: String,
    val board: String,
    val hardware: String,
    val androidVersion: String,
    val sdkInt: Int,
    val buildNumber: String
) {
    val fullDisplayName: String
        get() {
            val cleanManufacturer = manufacturer.replaceFirstChar { it.uppercase() }
            return if (model.startsWith(cleanManufacturer, ignoreCase = true)) {
                model
            } else {
                "$cleanManufacturer $model"
            }
        }
}
