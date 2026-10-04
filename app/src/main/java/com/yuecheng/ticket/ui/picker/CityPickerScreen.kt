package com.yuecheng.ticket.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.EndCity
import com.yuecheng.ticket.data.FlowState
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.StartCity
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.YcScaffold

@Composable
fun CityPickerScreen(nav: NavController, pickingStart: Boolean) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var startGroups by remember { mutableStateOf<List<Pair<String, List<StartCity>>>>(emptyList()) }
    var endGroups by remember { mutableStateOf<List<Pair<String, List<EndCity>>>>(emptyList()) }
    var keyword by remember { mutableStateOf("") }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(pickingStart, retryTick) {
        loading = true; error = ""
        resultOf {
            if (pickingStart) {
                startGroups = Repo.startPoints()
                // 缓存全量表,供首页交换城市使用
                FlowState.cachedStartPoints = startGroups.flatMap { it.second }
            }
            else endGroups = Repo.endPoints(FlowState.startCity?.id ?: "")
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    YcScaffold(title = if (pickingStart) "选择出发城市" else "选择到达地", onBack = { nav.popBackStack() }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text("输入城市名 / 拼音 / 简拼搜索") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                loading -> LoadingBox()
                error.isNotEmpty() -> ErrorBox(error) { retryTick++ }
                else -> {
                    if (pickingStart) {
                        val filtered = startGroups.map { (letter, list) ->
                            letter to list.filter {
                                keyword.isEmpty() || it.name.contains(keyword, true) ||
                                    it.pinyin.contains(keyword, true) || it.jianpin.contains(keyword, true)
                            }
                        }.filter { it.second.isNotEmpty() }
                        val hot = startGroups.flatMap { it.second }.filter { it.hot == 1 }
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (keyword.isEmpty() && hot.isNotEmpty()) {
                                item {
                                    SectionLabel("热门城市")
                                    FlowChips(hot.map { it.name }) { name ->
                                        startGroups.flatMap { it.second }.firstOrNull { it.name == name }?.let {
                                            FlowState.startCity = it
                                            nav.popBackStack()
                                        }
                                    }
                                }
                            }
                            filtered.forEach { (letter, list) ->
                                item(key = "L$letter") { LetterHeader(letter) }
                                // 接口不保证 id 唯一/非空,key 用字母+下标保证唯一
                                list.forEachIndexed { index, city ->
                                    item(key = "S-$letter-$index") {
                                        CityRow(city.name, city.pinyin) {
                                            FlowState.startCity = city
                                            nav.popBackStack()
                                        }
                                    }
                                }
                            }
                            if (filtered.isEmpty()) item { EmptyTip("没有匹配的城市") }
                        }
                    } else {
                        val filtered = endGroups.map { (letter, list) ->
                            letter to list.filter {
                                keyword.isEmpty() || it.name.contains(keyword, true) ||
                                    it.pinyin.contains(keyword, true) || it.jianpin.contains(keyword, true)
                            }
                        }.filter { it.second.isNotEmpty() }
                        LazyColumn(Modifier.fillMaxSize()) {
                            filtered.forEach { (letter, list) ->
                                item(key = "L$letter") { LetterHeader(letter) }
                                // 重名到达地会导致 key 冲突,同样用字母+下标
                                list.forEachIndexed { index, city ->
                                    item(key = "E-$letter-$index") {
                                        CityRow(city.name, city.pinyin) {
                                            FlowState.endCityName = city.name
                                            FlowState.endCityPinyin = city.pinyin
                                            nav.popBackStack()
                                        }
                                    }
                                }
                            }
                            if (filtered.isEmpty()) item { EmptyTip("没有匹配的到达地") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FlowChips(items: List<String>, onClick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        items.chunked(4).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { name ->
                    Box(
                        Modifier
                            .padding(end = 10.dp, bottom = 10.dp)
                            .clickable { onClick(name) },
                    ) {
                        Text(
                            name,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        letter,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun CityRow(name: String, pinyin: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.LocationOn, null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(name, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text(pinyin, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun EmptyTip(text: String) {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
