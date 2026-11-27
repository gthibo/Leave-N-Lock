package io.github.gthibo.leavenlock.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows each PIN digit as a dot. Deliberately not `PasswordVisualTransformation`: Compose marks
 * any field using that as a password for autofill (inputType text-password), and so does
 * `KeyboardType.NumberPassword` (content type Password, overriding ours). Google's autofill then
 * offered to save the PIN as the BlueLink password.
 */
object PinMask : VisualTransformation {
    const val DOT = '•'

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(DOT.toString().repeat(text.length)), OffsetMapping.Identity)
}

/** Autofill hint for the BlueLink PIN: unknown to autofill services, so they leave it alone. */
val BlueLinkPinContentType = ContentType("bluelinkPin")

/** A masked, digits-only field for the BlueLink PIN that autofill won't treat as a password. */
@Composable
fun PgPinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PgField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        visualTransformation = PinMask,
        // Number, not NumberPassword: same digit keypad, without the password flag.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
        contentType = BlueLinkPinContentType,
    )
}
