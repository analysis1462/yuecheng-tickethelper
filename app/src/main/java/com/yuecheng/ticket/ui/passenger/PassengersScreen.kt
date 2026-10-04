package com.yuecheng.ticket.ui.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.yuecheng.ticket.data.CardType
import com.yuecheng.ticket.data.FlowState
import com.yuecheng.ticket.data.IdCard
import com.yuecheng.ticket.data.NeedLoginException
import com.yuecheng.ticket.data.Passenger
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.LoginPrompt
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.maskId
import com.google.gson.JsonParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengersScreen(nav: NavController, chooseMode: Boolean) {
    var list by remember { mutableStateOf<List<Passenger>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CardType>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Passenger?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Passenger?>(null) }
    var needLogin by remember { mutableStateOf(false) }
    // 选中的证件号集合(可观察:勾选/行点击立即刷新界面)
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 导入/导出的结果提示
    var transferMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 最多可购人数:优先班次,其次套餐详情,默认 5(与 H5 口径一致)
    val maxSell = (FlowState.shift?.maxSellNum ?: FlowState.suit?.shiftInfo?.maxSellNum)
        ?.toIntOrNull()?.takeIf { it > 0 } ?: 5

    fun reload() {
        scope.launch {
            loading = true; error = ""; needLogin = false
            resultOf {
                Repo.passengers(FlowState.shift?.stationId ?: "", Session.customerId)
            }.onSuccess {
                list = it.first; cards = it.second
                // 恢复已选状态
                selectedIds = FlowState.selectedPassengers.mapNotNull { sel -> sel.idcardNo }.toSet()
                loading = false
            }.onFailure {
                loading = false
                if (it is NeedLoginException) {
                    if (Session.isLoggedIn) Session.logout()
                    needLogin = true
                } else {
                    error = it.message ?: "加载失败"
                }
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    // 乘客数据导出(SAF 写文件,不申请存储权限)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = org.json.JSONObject().apply {
                    put("exported", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date()))
                    put("passengers", org.json.JSONArray().apply {
                        list.forEach { p ->
                            put(org.json.JSONObject()
                                .put("name", p.name ?: "")
                                .put("idcardType", p.idcardType ?: "1")
                                .put("idcardNo", p.idcardNo ?: "")
                                .put("mobile", p.mobile ?: ""))
                        }
                    })
                }.toString()
                runCatching {
                    nav.context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法写入文件")
                }.onSuccess { transferMsg = "已导出 ${list.size} 位乘车人" }
                    .onFailure { transferMsg = "导出失败:${it.message}" }
            }
        }
    }

    // 乘客数据导入:与账号内现有乘车人按证件号去重,逐个调用新增接口
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val bytes = nav.context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取文件")
                    // 误选超大文件时整读会占内存:超过 1MB 直接判为选错文件
                    if (bytes.size > 1_048_576) throw IllegalStateException("文件超过 1MB,请确认选择了正确的备份文件")
                    val text = bytes.toString(Charsets.UTF_8)
                    val root = JsonParser.parseString(text)
                    val arr = if (root.isJsonArray) root.asJsonArray
                    else root.asJsonObject.get("passengers")?.asJsonArray
                        ?: throw IllegalStateException("文件格式不正确")
                    arr.mapNotNull { e ->
                        val o = e.asJsonObject
                        Triple(
                            o.get("name")?.asString ?: "",
                            o.get("idcardNo")?.asString ?: "",
                            (o.get("idcardType")?.asString ?: "1") to (o.get("mobile")?.asString ?: ""),
                        )
                    }
                }.map { entries ->
                    val existing = list.mapNotNull { it.idcardNo }.toMutableSet()
                    var ok = 0; var skip = 0; var fail = 0
                    entries.forEach { (name, idNo, typeMobile) ->
                        when {
                            idNo.isEmpty() || name.isEmpty() || idNo in existing -> skip++
                            else -> resultOf {
                                Repo.addPassenger(
                                    Session.customerId, name, typeMobile.first, idNo, typeMobile.second,
                                )
                            }.onSuccess {
                                existing += idNo; ok++
                            }.onFailure { fail++ }
                        }
                    }
                    reload()
                    transferMsg = "导入完成:新增 $ok,跳过 $skip" + (if (fail > 0) ",失败 $fail" else "")
                }.onFailure { transferMsg = "导入失败:${it.message}" }
            }
        }
    }

    transferMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(nav.context, msg, Toast.LENGTH_LONG).show()
            transferMsg = null
        }
    }

    /** 勾选/取消:更新可观察集合并同步到 FlowState(自动补默认票种,票价计算依赖它) */
    fun toggleSelect(person: Passenger) {
        val id = person.idcardNo ?: return
        if (id !in selectedIds && selectedIds.size >= maxSell) {
            Toast.makeText(nav.context, "最多选择 $maxSell 人", Toast.LENGTH_SHORT).show()
            return
        }
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        selectedIds = next
        val defaultTck = FlowState.shift?.tckTypeList?.firstOrNull()
            ?: FlowState.suit?.shiftInfo?.tckTypeList?.firstOrNull()
        FlowState.selectedPassengers = list.filter { it.idcardNo != null && it.idcardNo in next }
            .map { p ->
                if (p.tckType == null) p.tckType = defaultTck
                p
            }
            .toMutableList()
    }

    YcScaffold(
        title = if (chooseMode) "选择乘车人" else "乘车人管理",
        onBack = { nav.popBackStack() },
        actions = {
            // 管理模式下提供乘客数据备份:导出 JSON / 从 JSON 导入
            if (!chooseMode && list.isNotEmpty()) {
                IconButton(onClick = { exportLauncher.launch("yuecheng-passengers.json") }) {
                    Icon(Icons.Default.FileUpload, "导出乘车人")
                }
                IconButton(onClick = {
                    if (Session.isLoggedIn) importLauncher.launch(arrayOf("application/json", "text/plain"))
                    else android.widget.Toast.makeText(nav.context, "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.FileDownload, "导入乘车人")
                }
            }
        },
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
            when {
                loading -> LoadingBox()
                needLogin -> LoginPrompt { nav.navigate("login") }
                error.isNotEmpty() -> ErrorBox(error) { reload() }
                list.isEmpty() -> {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有常用乘车人", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showAdd = true }) { Text("添加乘车人") }
                    }
                }
                else -> Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, "添加乘车人")
                        }
                    },
                ) { inner ->
                    LazyColumn(
                        Modifier.fillMaxSize().padding(inner),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list) { person ->
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (chooseMode) {
                                        Checkbox(
                                            checked = person.idcardNo != null && person.idcardNo in selectedIds,
                                            onCheckedChange = { toggleSelect(person) },
                                        )
                                    }
                                    Column(Modifier.weight(1f).clickable(enabled = chooseMode) {
                                        toggleSelect(person)
                                    }) {
                                        Text(person.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(
                                            "${cardLabel(cards, person.idcardType)} ${maskId(person.idcardNo ?: "")}",
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        // 身份证:推算生日与性别展示(仅显示,下单以证件号为准)
                                        if (person.idcardType == "1") {
                                            val derived = listOfNotNull(
                                                IdCard.gender(person.idcardNo),
                                                IdCard.birth(person.idcardNo),
                                            ).joinToString(" · ")
                                            if (derived.isNotEmpty()) {
                                                Text(derived, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        person.mobile?.takeIf { it.isNotEmpty() }?.let {
                                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { editing = person }) {
                                        Icon(Icons.Default.Edit, "编辑", modifier = Modifier.width(20.dp))
                                    }
                                    IconButton(onClick = { deleting = person }) {
                                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.width(20.dp))
                                    }
                                }
                            }
                        }
                        if (chooseMode) {
                            item {
                                Button(
                                    onClick = { nav.popBackStack() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) { Text("确定(${selectedIds.size})") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 新增/编辑弹窗
    val showEditor = showAdd || editing != null
    if (showEditor) {
        val target = editing
        EditPassengerDialog(
            cards = cards,
            initial = target,
            onDismiss = { showAdd = false; editing = null },
            onSave = { name, idType, idNo, mobileNo ->
                scope.launch {
                    resultOf {
                        if (target == null) {
                            Repo.addPassenger(Session.customerId, name, idType, idNo, mobileNo)
                        } else {
                            Repo.editPassenger(Session.customerId, target.id ?: "", name, idType, idNo, mobileNo)
                        }
                    }.onSuccess {
                        showAdd = false; editing = null; reload()
                    }.onFailure { err ->
                        error = err.message ?: "保存失败"; showAdd = false; editing = null
                    }
                }
            },
        )
    }

    deleting?.let { person ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除乘车人") },
            text = { Text("确定删除 ${person.name} 吗?") },
            confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            resultOf { Repo.delPassenger(Session.customerId, person.id ?: "") }
                                .onSuccess { deleting = null; reload() }
                                .onFailure { err -> error = err.message ?: "删除失败"; deleting = null }
                        }
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EditPassengerDialog(
    cards: List<CardType>,
    initial: Passenger?,
    onDismiss: () -> Unit,
    onSave: (name: String, idType: String, idNo: String, mobile: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var idType by remember { mutableStateOf(initial?.idcardType ?: "1") }
    var idNo by remember { mutableStateOf(initial?.idcardNo ?: "") }
    var mobile by remember { mutableStateOf(initial?.mobile ?: "") }
    // 身份证做校验位/出生日期校验(含 X),其他证件类型只校验非空与最小长度
    val idError = if (idType == "1" && idNo.isNotEmpty()) IdCard.validate(idNo) else null
    val idNoValid = if (idType == "1") idNo.isNotEmpty() && idError == null else idNo.length >= 5
    val valid = name.isNotBlank() && idNoValid && mobile.length == 11

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加乘车人" else "编辑乘车人") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = idNo,
                    onValueChange = { idNo = it.uppercase().filter { c -> !c.isWhitespace() } },
                    label = { Text("证件号码") },
                    singleLine = true,
                    isError = idError != null,
                    supportingText = {
                        when {
                            idError != null -> Text(idError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            idType == "1" && idNo.length >= 15 -> {
                                val info = listOfNotNull(IdCard.gender(idNo), IdCard.birth(idNo)).joinToString(" · ")
                                if (info.isNotEmpty()) Text("校验通过:$info", fontSize = 12.sp)
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("证件类型", fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    cards.forEach { c ->
                        TextButton(onClick = { idType = c.value ?: "1" }) {
                            Text(
                                c.label ?: "身份证",
                                color = if (idType == c.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (idType == c.value) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(11) }, label = { Text("手机号") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), idType, idNo.trim(), mobile) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun cardLabel(cards: List<CardType>, value: String?): String =
    cards.firstOrNull { it.value == value }?.label ?: "身份证"
