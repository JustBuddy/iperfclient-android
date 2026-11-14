package wtf.buddyworks.iperfclient.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wtf.buddyworks.iperfclient.R
import wtf.buddyworks.iperfclient.data.Server
import wtf.buddyworks.iperfclient.ui.PaneConfig
import wtf.buddyworks.iperfclient.ui.components.CardMargin
import wtf.buddyworks.iperfclient.ui.components.CollapsingPane
import wtf.buddyworks.iperfclient.ui.components.GroupCard
import wtf.buddyworks.iperfclient.ui.components.InsetDivider
import wtf.buddyworks.iperfclient.ui.components.PillButton
import wtf.buddyworks.iperfclient.ui.components.SectionHeader
import wtf.buddyworks.iperfclient.ui.components.ToolbarIcon
import wtf.buddyworks.iperfclient.ui.components.groupItem
import wtf.buddyworks.iperfclient.ui.theme.OneUi

@Composable
fun ServersPane(
    servers: List<Server>,
    selectedId: String?,
    onSelect: (Server) -> Unit,
    onSave: (Server) -> Unit,
    onDelete: (Server) -> Unit,
    onRestorePublic: () -> Unit,
    config: PaneConfig,
    showHelp: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var editing by remember { mutableStateOf<Server?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    CollapsingPane(
        title = stringResource(R.string.nav_servers),
        subtitle = stringResource(R.string.servers_subtitle),
        expandable = config.expandableTitle,
        listState = listState,
        topInset = config.topInset,
        bottomPadding = config.bottomPadding,
        modifier = modifier,
        actions = { ToolbarIcon(Icons.Rounded.Add, stringResource(R.string.action_add_server), { adding = true }) },
    ) {
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
        itemsIndexed(servers, key = { _, s -> s.id }) { index, server ->
            ServerRow(
                server = server,
                selected = server.id == selectedId,
                isFirst = index == 0,
                isLast = index == servers.lastIndex,
                onSelect = { onSelect(server) },
                onEdit = { editing = server },
                modifier = Modifier.padding(horizontal = CardMargin),
            )
        }
        item(key = "actions") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                PillButton(stringResource(R.string.action_add_server), { adding = true }, icon = Icons.Rounded.Add)
                if (Server.Public.any { p -> servers.none { it.id == p.id } }) {
                    PillButton(stringResource(R.string.action_restore_public), onRestorePublic, filled = false)
                }
            }
        }
        if (showHelp) {
            item(key = "help") { OwnServerHelp(Modifier.padding(horizontal = CardMargin)) }
        }
    }
    if (adding || editing != null) {
        ServerEditor(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { onSave(it); adding = false; editing = null },
            onDelete = editing?.let { e -> { onDelete(e); editing = null } },
        )
    }
}

@Composable
private fun ServerRow(
    server: Server,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneUi.colors
    Column(modifier.groupItem(isFirst, isLast, colors.card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .heightIn(min = 68.dp)
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accent), modifier = Modifier.padding(horizontal = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = OneUi.type.itemTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(server.address, server.note).joinToString(" · "),
                    style = OneUi.type.itemSubtitle,
                    color = if (selected) colors.accent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ToolbarIcon(Icons.Outlined.Edit, stringResource(R.string.action_edit), onEdit, tint = colors.textSecondary)
        }
        if (!isLast) InsetDivider(start = 64.dp)
    }
}

/** How to start a server on a computer, for testing a home network end to end. */
@Composable
fun OwnServerHelp(modifier: Modifier = Modifier) {
    val colors = OneUi.colors
    SectionHeader(stringResource(R.string.help_title))
    GroupCard(modifier, contentPadding = 20.dp) {
        Text(stringResource(R.string.help_text), style = OneUi.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.highlight)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("iperf3 -s", style = OneUi.type.body.copy(fontFamily = FontFamily.Monospace), color = colors.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.help_text_2), style = OneUi.type.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ServerEditor(
    initial: Server?,
    onDismiss: () -> Unit,
    onSave: (Server) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = OneUi.colors
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var host by rememberSaveable { mutableStateOf(initial?.host ?: "") }
    var ports by rememberSaveable {
        mutableStateOf(initial?.let { if (it.portEnd > it.port) "${it.port}-${it.portEnd}" else it.port.toString() } ?: "5201")
    }
    val parsedPorts = parsePorts(ports)
    val hostOk = host.trim().let { it.isNotEmpty() && !it.contains(' ') && !it.contains('/') }
    val valid = hostOk && parsedPorts != null
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        focusedLabelColor = colors.accent,
        cursorColor = colors.accent,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.action_add_server else R.string.edit_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    host, { host = it.trim() },
                    label = { Text(stringResource(R.string.field_host)) },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    ports, { ports = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text(stringResource(R.string.field_port)) },
                    supportingText = { Text(stringResource(R.string.field_port_hint)) },
                    isError = parsedPorts == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(host.ifBlank { stringResource(R.string.field_name_hint) }) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_delete_server), style = OneUi.type.button, color = colors.danger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val (start, end) = parsedPorts ?: return@TextButton
                    onSave(
                        Server(
                            id = initial?.id ?: "",
                            name = name.trim().ifEmpty { host.trim() },
                            host = host.trim(),
                            port = start,
                            portEnd = end,
                            note = initial?.note,
                        ),
                    )
                },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save), color = if (valid) colors.accent else colors.textSecondary) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.accent) } },
        containerColor = colors.card,
        shape = OneUi.cardShape,
    )
}

private fun parsePorts(text: String): Pair<Int, Int>? {
    val parts = text.split('-').map { it.trim() }
    val start = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val end = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else start
    if (parts.size > 2 || start !in 1..65535 || end !in start..65535 || end - start > 50) return null
    return start to end
}
