package com.yuecheng.ticket.ui.login

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.Api
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.RegisterRequiredException
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.ui.common.YcScaffold
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(nav: NavController) {
    var mode by remember { mutableIntStateOf(0) } // 0=密码 1=验证码
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var captchaBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var captchaOk by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var registerToken by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshCaptcha() {
        resultOf { Api.captchaImage() }.onSuccess {
            captchaBitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            captcha = ""; captchaOk = false
        }
    }
    LaunchedEffect(Unit) { refreshCaptcha() }
    LaunchedEffect(countdown) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    YcScaffold(title = "登录", onBack = { nav.popBackStack() }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == 0, onClick = { mode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("密码登录") }
                        SegmentedButton(
                            selected = mode == 1, onClick = { mode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("验证码登录") }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { if (it.length <= 11) mobile = it.filter { c -> c.isDigit() } },
                        label = { Text("手机号") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (mode == 0) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        // 图形验证码
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = captcha,
                                onValueChange = { if (it.length <= 4) captcha = it.uppercase() },
                                label = { Text("图形验证码") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier
                                    .size(width = 152.dp, height = 66.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .clickable { scope.launch { refreshCaptcha() } },
                                contentAlignment = Alignment.Center,
                            ) {
                                val bmp = captchaBitmap
                                if (bmp != null) {
                                    // 原生 92x38 拉伸放大,字符更清晰
                                    Image(bmp.asImageBitmap(), "验证码", contentScale = ContentScale.FillBounds)
                                } else {
                                    Text("点击刷新", fontSize = 12.sp)
                                }
                            }
                        }
                        LaunchedEffect(captcha) {
                            if (captcha.length == 4) {
                                captchaOk = Api.checkCaptcha(captcha)
                                if (!captchaOk) { msg = "图形验证码不正确"; refreshCaptcha() } else msg = ""
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = smsCode,
                                onValueChange = { if (it.length <= 6) smsCode = it.filter { c -> c.isDigit() } },
                                label = { Text("短信验证码") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                enabled = mobile.length == 11 && captchaOk && countdown == 0 && !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true; msg = ""
                                        resultOf { Repo.sendSmsCode(mobile, captcha) }
                                            .onSuccess { countdown = 60; msg = "验证码已发送,5分钟内有效" }
                                            .onFailure { msg = it.message ?: "发送失败"; refreshCaptcha() }
                                        busy = false
                                    }
                                },
                            ) { Text(if (countdown > 0) "${countdown}秒后重发" else "获取验证码") }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; msg = ""
                                resultOf {
                                    val user = if (mode == 0) Repo.loginByPassword(mobile, password)
                                    else Repo.loginBySms(mobile, smsCode)
                                    Session.setLogin(user)
                                    // 密码登录记住凭据,会话失效时静默重登;验证码登录无密码无法重登
                                    if (mode == 0) Session.setCredentials(mobile, password)
                                }.onSuccess { nav.popBackStack() }
                                    .onFailure { e ->
                                        if (e is RegisterRequiredException) {
                                            registerToken = e.token
                                            msg = ""
                                        } else {
                                            msg = e.message ?: "登录失败"
                                        }
                                    }
                                busy = false
                            }
                        },
                        enabled = !busy && mobile.length == 11 && (mode == 0 && password.length >= 6 || mode == 1 && smsCode.length == 6),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(if (busy) "登录中…" else "登录") }

                    if (msg.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "登录 / 注册即代表同意购票协议。首次使用请选择「验证码登录」,验证通过后设置密码即完成注册。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 未注册:设置密码完成注册(对应 H5 #/password 页,成功返回即登录)
    registerToken?.let { token ->
        SetPasswordDialog(
            onDismiss = { registerToken = null },
            onConfirm = { newPassword ->
                scope.launch {
                    busy = true; msg = ""
                    resultOf {
                        // resetPassword 只设置密码、不建立服务端会话(实测 NEEDLOGIN),
                        // 成功后必须用新密码真实登录一次,登录响应的 DATA 即用户信息
                        Repo.resetPassword(mobile, newPassword, token)
                        val user = Repo.loginByPassword(mobile, newPassword)
                        Session.setCredentials(mobile, newPassword)
                        user
                    }.onSuccess { user ->
                        Session.setLogin(user)
                        registerToken = null
                        nav.popBackStack()
                    }.onFailure { e ->
                        msg = e.message ?: "设置密码失败"
                        registerToken = null
                    }
                    busy = false
                }
            },
        )
    }
}

@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    // 与 H5 一致:8位以上,须含大小写字母和数字
    val pwdValid = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$").matches(pwd)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("完成注册") },
        text = {
            Column {
                Text(
                    "手机号验证已通过,设置登录密码即完成注册(也可用该密码在「密码登录」中登录)。",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("设置登录密码") },
                    supportingText = { Text("至少8位,须包含大写字母、小写字母和数字") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            OutlinedButton(enabled = pwdValid, onClick = { onConfirm(pwd) }) { Text("注册并登录") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
