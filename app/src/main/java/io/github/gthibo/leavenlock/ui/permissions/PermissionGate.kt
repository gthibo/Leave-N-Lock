package io.github.gthibo.leavenlock.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.ui.components.CardTone
import io.github.gthibo.leavenlock.ui.components.IconWell
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Gate that requests the runtime permissions LEAVE/LOCK needs, then shows [content].
 * Background location + activity recognition are requested contextually.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val state = rememberMultiplePermissionsState(permissions)

    if (state.allPermissionsGranted) {
        content()
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(Porcelain.colors.surface)
                .safeDrawingPadding()
                .padding(PorcelainSpace.screenMargin),
            verticalArrangement = Arrangement.Center,
        ) {
            PgCard(
                tone = CardTone.Hero,
                cut = 24.dp,
                eyebrow = stringResource(R.string.app_name),
                pixels = listOf("w.w", ".d.", "w.m"),
            ) {
                IconWell(R.drawable.ic_pg_lock, size = 48.dp, iconSize = 24.dp)
                Text(stringResource(R.string.perm_title), style = PorcelainText.heading1)
                Text(stringResource(R.string.perm_body), style = PorcelainText.body, color = mutedText)
            }
            PgButton(
                text = stringResource(R.string.perm_grant),
                onClick = { state.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth().padding(top = PorcelainSpace.s4),
            )
        }
    }
}
