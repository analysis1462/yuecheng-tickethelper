# 悦程购票 (YueChengTicket)

第三方原生 Android 客户端,调用「悦程汽车票」(新途云) H5 同源接口,实现汽车票查询、购票、支付、改签、退票全流程。界面使用 Kotlin + Jetpack Compose (Material 3) 原生实现,支持日间/夜间双主题。

> ⚠️ **仅供个人学习与自用**,请勿商用或分发。接口来自公开 H5 页面,官方随时可能变更或加固,本仓库不保证长期可用。购票 / 支付行为均通过官方网关完成,账号即你本人手机号。

## 功能

| 模块 | 说明 |
|---|---|
| 首页 | 出发/到达城市选择(首字母分组 + 热门 + 拼音搜索)、一键互换、预售期内日历选日期、查询历史记录(点击直达 / 一键清除)、**收藏线路**(一键收藏,点击直达查询,长按移除) |
| 车次列表 | 时间/价格排序,时段/车站/公司/有票/高速筛选,无班次空状态引导 |
| 登录 | 密码登录 + 图形验证码 + 短信验证码;未注册号码验证后设置密码即完成注册;会话失效自动静默重登;登录凭据经 Android Keystore 加密存储 |
| 乘车人 | 增删改查,身份证号格式校验,乘车人 JSON 导入/导出(备份、换机),选择乘车人自动带出票种 |
| 下单 | 票种选择、乘意险、费用明细(与官方 H5 计算一致) |
| 支付 | 内嵌 WebView 打开官方 payUrl,拦截 `alipays://` `weixin://` `intent://` 唤起支付宝/微信;支付完成后自动轮询订单状态并刷新 |
| 订单 | 全部/待支付/待出行,电子客票凭证版式(条码 + 二维码 + 取票密码 + 14 项票面信息),可点击放大亮码 |
| 改签退票 | 可改班次查询 + 确认改签;退票手续费预查 + 按票退票(以官方 isAllowChange / isAllowRefund 开关为准) |
| 发车提醒 | 每张票可多选提前量(如 30 分钟 / 1 小时 / 2 小时)到点发本地通知(带检票口、座位、车牌等票面要素,触发时拉取最新订单);可选在系统时钟 App 同步创建真实闹钟由系统响铃;购票成功票有常驻倒计时通知 |
| 其他 | 夜间模式(带主题切换动画)、页面平滑切换动画、桌面长按快捷方式(查票 / 我的订单,Android 7.1+)、崩溃日志本地留存、内置测试面板(首页副标题「退票」长按 5 秒进入) |

## 构建

### 方式一:GitHub Actions 云端打包(无需安装任何东西)

1. 把本仓库推到你的 GitHub:
   ```bash
   git remote add origin https://github.com/<你的用户名>/yuecheng.git
   git push -u origin main
   ```
2. 打开仓库 Actions 页,等 `Build APK` 跑完,在 Artifacts 里下载 `yuecheng-apk.zip`,解压即得 APK(debug + debug 签名的 release)。

### 方式二:Android Studio 本地打包

1. Android Studio 打开本目录,等待 Gradle Sync(首次自动下载依赖)。
2. `Build → Build App Bundle(s) / APK(s) → Build APK(s)`。
3. 产物在 `app/build/outputs/apk/debug/app-debug.apk`。

> 仓库未提交 gradle wrapper,CI 侧由 `gradle/actions/setup-gradle` 固定 Gradle 8.9;本地需 Android Studio 或已安装的 Gradle 8.7+。

### 运行单元测试

```bash
gradle :app:testDebugUnitTest
```

单元测试为纯 JVM(`app/src/test/`,无需设备/模拟器),覆盖证件号校验、响应封装解析、状态码映射、shiftId 序列化、价格与日期工具、提醒/通知文案等无 Android 依赖的纯逻辑;CI 在每次构建前自动执行。

## 技术要点(还原自官方 H5 前端源码)

- **会话**:登录态是 JSESSIONID cookie,App 用持久化 CookieJar 保存;图形验证码与短信验证码绑定同一会话;服务端会话有效期较短,App 保存登录凭据(Keystore 加密),任何接口遇 `NEEDLOGIN` 自动重登并重试。
- **参数编码**:登录手机号/密码、新增乘车人各字段为标准 Base64;下单 `shiftId` 是 JSON 复合串 `{stationId,sendDate,sendTime,shiftNum,portName}`;`passengerList` 是 JSON 数组字符串。
- **价格单位**:`/book/shifts` 返回 `price` 为元;`/book/suit` 与订单接口的价格字段为分(显示时 ÷100);票价 = `(票种价 - 活动价 + 极速服务费)/100`。
- **响应封装**:多数接口 `STATUS=="SUCCESS"` 判成功、`NEEDLOGIN` 判登录失效;下单/支付/改签用 `CODE=="0000"` 判成功,`0004` 表示有未完成订单。
- **订单状态**:`0/2 待支付,1/3 正在出票,4 购票成功,5 关闭,6/7 出票失败`;子票 `0` 为购票成功。
- **支付**:官方网关返回 `payUrl`,非微信环境同样可用(前端显式支持 `openId=null` 路径)。
- **ttsId**:H5 用 URL 参数 `?ttsId=` 区分租户,留空走网关默认,如需指定改 `data/Models.kt` 的 `Config.ttsId`。

## 工程结构

```
app/src/main/java/com/yuecheng/ticket/
├── YcApp.kt / MainActivity.kt        # 应用入口与导航(平滑切换动画、桌面快捷方式)
├── data/
│   ├── Api.kt                        # OkHttp + 持久化 CookieJar + 响应封装
│   ├── Models.kt                     # 数据模型 + 解析 + Repo(全部接口 + 自动重登)
│   ├── Session.kt                    # 登录态 / 流程状态 / 夜间模式 / 搜索历史
│   ├── SecureStore.kt                # 登录凭据加密存储(Keystore,低版本降级)
│   ├── Result.kt                     # 挂起安全的 runCatching(协程取消不被吞)
│   ├── Reminders.kt                  # 发车提醒计划(持久化、调度、通知渲染)
│   ├── CrashLog.kt                   # 全局崩溃捕获与本地日志
│   └── IdCard.kt                     # 身份证号校验
└── ui/
    ├── home / picker / shift         # 首页(收藏线路、历史记录)、城市选择、车次列表
    ├── login / passenger / profile   # 登录、乘车人、个人中心
    ├── order                         # 下单、订单列表、客票详情(含发车提醒配置弹窗)
    ├── change / pay                  # 改签、支付 WebView(轮询支付结果)
    ├── debug                         # 内置测试面板(长按首页副标题「退票」5 秒进入)
    └── theme / common                # 主题(日/夜)与通用组件
scripts/gen_launcher_icons.ps1        # 由矢量前景图生成各密度启动图标(PowerShell)
app/src/test/java/...                 # 纯 JVM 单元测试(证件号/响应封装/工具函数/提醒文案)
```

## 免责声明

本项目**仅供个人学习、研究与自用**,与官方无任何隶属关系;仅为人工操作的购票辅助工具,不提供也不支持任何抢票、占票、倒卖加价等行为,购票与支付均通过官方网关完成。严禁将其用于任何违法违规用途,由此产生的一切后果由使用者自行承担。

支付协议:主接口全程 HTTPS;支付网关服务端返回 `http://` 地址,但经实测支持 HTTPS,App 统一升级为 https 加载(`network_security_config.xml` 中的明文许可仅作为老网关页内嵌 http 子资源的兜底)。
