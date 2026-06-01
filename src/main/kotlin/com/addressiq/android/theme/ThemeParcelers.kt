package com.addressiq.android.theme

import android.os.Parcel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parceler

// Parcelers that let `AddressIQThemeOverrides` ride through the
// `AddressIQVerifyContract` Activity-result boundary (which serializes its
// Input into an Intent). All override tokens are nullable, so the parcelers
// handle null directly. Color + radius tokens — the tokens partners actually
// override — round-trip losslessly.

internal object ColorParceler : Parceler<Color?> {
    override fun create(parcel: Parcel): Color? =
        if (parcel.readInt() == 1) Color(parcel.readLong().toULong()) else null

    override fun Color?.write(parcel: Parcel, flags: Int) {
        if (this == null) {
            parcel.writeInt(0)
        } else {
            parcel.writeInt(1)
            parcel.writeLong(value.toLong())
        }
    }
}

internal object DpParceler : Parceler<Dp?> {
    override fun create(parcel: Parcel): Dp? =
        if (parcel.readInt() == 1) parcel.readFloat().dp else null

    override fun Dp?.write(parcel: Parcel, flags: Int) {
        if (this == null) {
            parcel.writeInt(0)
        } else {
            parcel.writeInt(1)
            parcel.writeFloat(value)
        }
    }
}

// FontFamily cannot be reconstructed generically from a Parcel, so font-family
// overrides are NOT carried across the Activity-result boundary — they degrade
// to null (the merged theme then falls back to its default font). Partners
// needing a custom font should use the Compose `AddressIQVerifyView` API
// directly (no parcelling involved there).
internal object FontFamilyParceler : Parceler<FontFamily?> {
    override fun create(parcel: Parcel): FontFamily? = null
    override fun FontFamily?.write(parcel: Parcel, flags: Int) {}
}
