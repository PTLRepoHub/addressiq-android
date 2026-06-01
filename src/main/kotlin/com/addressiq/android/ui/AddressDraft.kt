package com.addressiq.android.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * In-flight address being collected through the verify flow. Held in
 * `rememberSaveable` so config changes don't reset progress.
 */
@Parcelize
data class AddressDraft(
    val lat: Double? = null,
    val lon: Double? = null,
    val formattedAddress: String? = null,
    val propertyNumber: String? = null,
    val streetName: String? = null,
    val buildingColor: String? = null,
    val directions: String? = null,
) : Parcelable
