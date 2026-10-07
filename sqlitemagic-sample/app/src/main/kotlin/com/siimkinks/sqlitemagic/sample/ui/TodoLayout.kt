package com.siimkinks.sqlitemagic.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.siimkinks.sqlitemagic.sample.R

@Composable
internal fun TodoLayout(
  title: String,
  subtitle: String,
  saving: Boolean,
  canAdd: Boolean,
  addLabel: String,
  onAdd: () -> Unit,
  onBack: (() -> Unit)?,
  content: @Composable () -> Unit
) = Scaffold(
  floatingActionButton = {
    if (canAdd) {
      ExtendedFloatingActionButton(onClick = onAdd) {
        Text(addLabel)
      }
    }
  }
) { padding ->
  Column(modifier = Modifier.padding(padding)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      onBack?.let { back ->
        IconButton(
          onClick = back,
          enabled = !saving
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigate_back)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    HorizontalDivider()
    content()
  }
}

@Composable
internal fun EmptyMessage(
  title: String,
  body: String
) = Column(
  modifier = Modifier
    .fillMaxSize()
    .padding(32.dp),
  verticalArrangement = Arrangement.Center,
  horizontalAlignment = Alignment.CenterHorizontally
) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleLarge
  )
  Text(
    text = body,
    modifier = Modifier.padding(top = 8.dp),
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
