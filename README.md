# PhoneInspect

不接电脑、不用 USB，直接在 Android 手机上查看网页每个请求的**完整响应头** —— 包括 JS 层永远读不到的 `Set-Cookie`、重定向链和真实状态码。

## 为什么不用 Eruda / vConsole

那类工具 hook 的是页面里的 `XMLHttpRequest` 和 `fetch`，撞三条规范墙：

- 跨域请求只能读到 CORS 安全列表里的响应头
- `Set-Cookie` 是 forbidden response header，任何 JS 手段都读不到
- 导航请求（HTML 文档本身）、CSS/JS/图片、重定向链全在 hook 范围外

PhoneInspect 把拦截搬到 WebView 的原生层，用 `HttpURLConnection` 把请求重发一次。重发不经过页面的 fetch，**CORS 根本不适用**，所以第三方站点的响应头同样能看全。

## 三档模式

| 模式 | 行为 | 用在哪 |
|---|---|---|
| **拦截 GET**（默认） | 原生层重发 GET，拿到状态码、全部响应头、`Set-Cookie`、重定向链、响应体前 6KB，再回填给 WebView | 排查接口问题 |
| **仅记录** | 只记 URL 和请求头，不动真实流量 | 怀疑拦截本身引起异常时对照 |
| **关闭** | 完全等同普通浏览器 | 定位问题时做减法 |

底部面板分 **网络 / 控制台 / 信息** 三块。控制台自动注入钩子，捕获 `console.*`、未捕获异常和 Promise 拒绝。点任意请求可看分组详情，支持整份复制导出。

## 安装

下载仓库里的 `PhoneInspect.apk`，传到手机点击安装（需允许未知来源）；或

```bash
adb install -r PhoneInspect.apk
```

minSdk 26（Android 8+），无原生库，不分 32/64 位。

## 构建

不需要 Gradle，也不需要 Android Studio。工具链是 `aapt2 + javac + d8 + zipalign + apksigner`。

```bash
bash build.sh        # 产物在根目录 PhoneInspect.apk
```

首次运行会自动调用 `bootstrap.sh` 下载 build-tools 35 与 platform 35（约 120MB）到 `sdk/`。这一步是**从 Google 官方源下载 SDK 组件**，和安装 Android Studio 里勾选的是同一批文件。

依赖：JDK 11+、`curl`、`unzip`、`python3`（用于把 `classes.dex` 合进 APK，因为 Git Bash 没有 `zip`）。

## 原理

```
WebView 请求
   ↓
WebViewClient.shouldInterceptRequest()      ← 后台线程，可阻塞
   ↓
Replay.fetch()  用 HttpURLConnection 重发一次
   ↓ 逐跳处理 3xx，记录重定向链，Set-Cookie 写回 CookieManager
WebResourceResponse(mime, enc, status, reason, headers, data)
   ↓
WebView 拿到"看起来像正常响应"的内容并渲染
```

几个必须踩过才知道的坑，代码里都处理了：

- 重发时**丢掉** `Accept-Encoding`，交给 HttpURLConnection 自动协商并解压
- 重发时**丢掉** `If-None-Match` / `If-Modified-Since`，否则服务端返回 304 空包，页面直接白屏
- 回给 WebView 的头**必须删掉** `Content-Length` / `Content-Encoding` / `Transfer-Encoding`，因为 body 已被解压，长度和原头对不上会导致渲染失败
- `Set-Cookie` 不能靠响应头传给 WebView，要逐条写进 `CookieManager` 再 `flush()`

## 已知限制

- **只有 HTTP/1.1**。重发走 `HttpURLConnection`，Android 上它被锁死在 HTTP/1.1。浏览器直连可能是 HTTP/2，头部会全小写、没有 `Connection`、并发模型也不同（资源多的页面在拦截模式下会明显更慢）。HTTP/3 完全看不到。
- **拿不到 POST 请求体**。`WebResourceRequest` 不提供 body，所以非 GET 请求只记录 URL 和请求头、不重发。
- 每个 GET 多一次往返，请求多的页面会变慢。
- 记录只存在内存里，切后台被回收就没了，最多保留 400 条。

## 目录

```
app/
  AndroidManifest.xml
  res/                          自适应图标
  java/com/phoneinspect/
    MainActivity.java           界面、WebView、三档模式、JS 桥
    Replay.java                 重发与响应头抓取（核心）
    Net.java                    数据模型与内存存储
    PanelAdapter.java           请求列表 / 日志列表
    Detail.java                 请求详情弹窗
bootstrap.sh                    下载 SDK 组件
build.sh                        一条命令出 APK
```
