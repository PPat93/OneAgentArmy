package com.parrotworks.oneagentarmy.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.ui.components.ParrotLogoBadge

@Composable
fun AppLockScreen(onUnlockClick: () -> Unit) {
    // Prompt right away so the common case (open the app, authenticate) needs no extra tap;
    // the button stays as the way to retry after a cancel/failure.
    LaunchedEffect(Unit) { onUnlockClick() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ParrotLogoBadge(size = 96.dp, innerSize = 79.dp)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.app_lock_unlock_button), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
