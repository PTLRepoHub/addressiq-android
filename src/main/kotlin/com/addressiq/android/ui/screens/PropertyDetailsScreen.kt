package com.addressiq.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.AddressDraft
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.ScreenScaffold

private data class ColorOption(val label: String, val color: Color, val needsBorder: Boolean = false)

private val COLOR_OPTIONS = listOf(
    ColorOption("White", Color(0xFFF5F5F5), needsBorder = true),
    ColorOption("Brown", Color(0xFF8B4513)),
    ColorOption("Blue", Color(0xFF2563EB)),
    ColorOption("Red", Color(0xFFDC2626)),
    ColorOption("Grey", Color(0xFF6B7280)),
    ColorOption("Yellow", Color(0xFFEAB308), needsBorder = true),
    ColorOption("Green", Color(0xFF16A34A)),
    ColorOption("Cream", Color(0xFFFFFDD0), needsBorder = true),
)

@Composable
fun PropertyDetailsScreen(
    initial: AddressDraft,
    onNext: (AddressDraft) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalAddressIQTheme.current

    var propertyNumber by remember { mutableStateOf(initial.propertyNumber.orEmpty()) }
    var streetName by remember { mutableStateOf(initial.streetName.orEmpty()) }
    var buildingColor by remember { mutableStateOf(initial.buildingColor.orEmpty()) }
    var directions by remember { mutableStateOf(initial.directions.orEmpty()) }

    val canContinue = propertyNumber.isNotBlank() && streetName.isNotBlank() && buildingColor.isNotBlank()

    ScreenScaffold(
        title = "Property Details",
        subtitle = "Help us identify your building",
        onBack = onBack,
        onClose = onCancel,
        footer = {
            AddressIQButton(
                text = "Continue",
                onClick = {
                    onNext(
                        initial.copy(
                            propertyNumber = propertyNumber.trim(),
                            streetName = streetName.trim(),
                            buildingColor = buildingColor,
                            directions = directions.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = canContinue,
            )
        },
    ) {
        LabeledField("Property / House Number", propertyNumber, onChange = { propertyNumber = it }, placeholder = "e.g. 12, Block A")
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField("Street Name", streetName, onChange = { streetName = it }, placeholder = "e.g. Broad Street")
        Spacer(modifier = Modifier.height(20.dp))

        Text("Building Color", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Two-row grid (4 colors per row)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                COLOR_OPTIONS.take(4).forEach { option ->
                    ColorChip(option, selected = buildingColor == option.label) { buildingColor = option.label }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                COLOR_OPTIONS.drop(4).forEach { option ->
                    ColorChip(option, selected = buildingColor == option.label) { buildingColor = option.label }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        LabeledField(
            label = "Landmark / Directions (optional)",
            value = directions,
            onChange = { directions = it },
            placeholder = "e.g. Opposite yellow church",
            multiline = true,
        )
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    multiline: Boolean = false,
) {
    val theme = LocalAddressIQTheme.current
    Column {
        Text(label, color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = theme.inputPlaceholder) },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (multiline) Modifier.height(80.dp) else Modifier),
            singleLine = !multiline,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.inputText,
                unfocusedTextColor = theme.inputText,
                focusedBorderColor = theme.borderFocused,
                unfocusedBorderColor = theme.inputBorder,
                cursorColor = theme.primary,
            ),
        )
    }
}

@Composable
private fun ColorChip(option: ColorOption, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalAddressIQTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) theme.primary else theme.border,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (option.needsBorder) {
            Row(
                modifier = Modifier
                    .size(28.dp)
                    .background(option.color, CircleShape)
                    .border(1.dp, theme.inputBorder, CircleShape),
            ) {}
        } else {
            Row(modifier = Modifier.size(28.dp).background(option.color, CircleShape)) {}
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            option.label,
            color = if (selected) theme.primary else theme.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
