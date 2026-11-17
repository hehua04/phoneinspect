package com.phoneinspect;

import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 把 WebView 的请求在原生层重发一次：
 * 拿到状态码、完整响应头（含 Set-Cookie）、真实协议版本，
 * 再把内容和头一起喂回给 WebView。
 */
public class Replay {

    private static final int MAX_HOPS = 6;
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private static final int PREVIEW = 6000;
    private static final int SNIFF_BYTES = 8192;
    /** 流式交付时留给面板的开头字节数 */
    private static final int PREVIEW_BYTES = 8192;
    private static final byte[] EMPTY = new byte[0];

    private static final Map<String, String> BY_EXT = new HashMap<String, String>();

    private static final String[] HTML_TAGS = {
            "html", "head", "body", "div", "span", "p", "h1", "h2", "h3", "h4",
            "title", "meta", "script", "link", "style", "table", "ul", "ol", "li",
            "form", "input", "img", "a", "br", "nav", "section", "article", "pre", "label"
    };

    static {
        BY_EXT.put("html", "text/html");
        BY_EXT.put("htm", "text/html");
        BY_EXT.put("js", "application/javascript");
        BY_EXT.put("mjs", "application/javascript");
        BY_EXT.put("css", "text/css");
        BY_EXT.put("json", "application/json");
        BY_EXT.put("xml", "text/xml");
        BY_EXT.put("txt", "text/plain");
        BY_EXT.put("svg", "image/svg+xml");
        BY_EXT.put("png", "image/png");
        BY_EXT.put("jpg", "image/jpeg");
        BY_EXT.put("jpeg", "image/jpeg");
        BY_EXT.put("gif", "image/gif");
        BY_EXT.put("webp", "image/webp");
        BY_EXT.put("ico", "image/x-icon");
        BY_EXT.put("woff", "font/woff");
        BY_EXT.put("woff2", "font/woff2");
        BY_EXT.put("ttf", "font/ttf");
        BY_EXT.put("wasm", "application/wasm");
        BY_EXT.put("mp4", "video/mp4");
        BY_EXT.put("webm", "video/webm");
        BY_EXT.put("mp3", "audio/mpeg");
    }

    private static OkHttpClient CLIENT_H1;
    private static OkHttpClient CLIENT_H2;
    private static OkHttpClient CLIENT_H1_INSECURE;
    private static OkHttpClient CLIENT_H2_INSECURE;

    private static synchronized OkHttpClient client(int choice, boolean insecure) {
        if (choice == Net.PROTO_H1) {
            if (insecure) {
                if (CLIENT_H1_INSECURE == null) {
                    CLIENT_H1_INSECURE = build(Arrays.asList(Protocol.HTTP_1_1), true);
                }
                return CLIENT_H1_INSECURE;
            }
            if (CLIENT_H1 == null) CLIENT_H1 = build(Arrays.asList(Protocol.HTTP_1_1), false);
            return CLIENT_H1;
        }
        // OkHttp 3.x 的 protocols() 强制要求列表里有 http/1.1 或 h2_prior_knowledge，
        // 单独传 [HTTP_2] 会抛 IllegalArgumentException。
        // 所以「探测 H2」不能靠限制协议，只能在拿到响应后检查协商结果（见 fetch 里的 PROTO_H2 分支）。
        if (insecure) {
            if (CLIENT_H2_INSECURE == null) {
                CLIENT_H2_INSECURE = build(
                        Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2), true);
            }
            return CLIENT_H2_INSECURE;
        }
        if (CLIENT_H2 == null) {
            CLIENT_H2 = build(Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2), false);
        }
        return CLIENT_H2;
    }

    private static OkHttpClient build(List<Protocol> ps, boolean insecure) {
        // OkHttp 默认 maxRequestsPerHost = 5。WebView 原生是几十路并发，
        // 5 路 + 每次读完整个 body 才交给 WebView，等于把并行下载改成串行，
        // 同域图片多的页面会肉眼可见地变慢。
        Dispatcher d = new Dispatcher();
        d.setMaxRequests(64);
        d.setMaxRequestsPerHost(6);

        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .dispatcher(d)
                .protocols(ps)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        if (insecure) {
            // 只对用户在弹窗里点过「继续」的站点生效，且仅存在于本次进程内
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
                b.sslSocketFactory(ctx.getSocketFactory(), TRUST_ALL);
                b.hostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String h, SSLSession s) { return true; }
                });
            } catch (Throwable ignored) { }
        }
        return b.build();
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    /** host:port，证书例外按端口区分，避免把整台机器上所有服务都放开 */
    private static String hostKey(HttpUrl u) {
        return u.host() + ":" + u.port();
    }

    /** 判断异常链里是不是证书问题，用来决定要不要弹「继续访问」 */
    private static boolean isSslError(Throwable t) {
        Throwable c = t;
        for (int i = 0; i < 8 && c != null; i++) {
            String n = c.getClass().getName();
            if (n.contains("SSL") || n.contains("Cert") || n.contains("Trust")) return true;
            c = c.getCause();
        }
        return false;
    }

    public static String dump(Map<String, String> h) {
        if (h == null || h.isEmpty()) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : h.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    public static WebResourceResponse fetch(String url, Map<String, String> reqHeaders,
                                            boolean mainFrame, Net.Store store, int protoChoice,
                                            int navId, String pageUrl) {
        HttpUrl current = HttpUrl.parse(url);
        if (current == null) return null;

        Net.Record rec = new Net.Record();
        rec.method = "GET";
        rec.url = url;
        rec.mainFrame = mainFrame;
        rec.navId = navId;
        rec.pageUrl = pageUrl;
        rec.requestHeaders = dump(reqHeaders);
        long t0 = System.currentTimeMillis();
        StringBuilder chain = new StringBuilder();

        try {
            // 必须放在 try 里：协议配置非法时 client() 会抛异常，
            // 一旦漏到 shouldInterceptRequest 外面就是未捕获异常，整个进程会被杀掉。
            boolean insecure = store.insecureHosts.contains(hostKey(current));
            OkHttpClient client = client(protoChoice, insecure);

            for (int hop = 0; hop <= MAX_HOPS; hop++) {
                Request.Builder rb = new Request.Builder().url(current).get();
                if (reqHeaders != null) {
                    for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                        String k = e.getKey();
                        if (k == null || skip(k)) continue;
                        try {
                            rb.header(k, e.getValue());
                        } catch (Throwable ignored) { }
                    }
                }
                // WebResourceRequest 的请求头默认不含 Cookie，必须自己从 CookieManager 取；
                // 每一跳都重新取一次，跳转后域名/路径变了也能拿到正确的那份。
                String ck = cookieFor(current.toString());
                if (ck != null && ck.length() > 0) rb.header("Cookie", ck);

                Response resp = client.newCall(rb.build()).execute();
                boolean handedOff = false;   // 流式交付后，响应体归 WebView 负责关闭
                try {
                    int code = resp.code();
                    String ct = resp.header("Content-Type");
                    rec.status = code;
                    rec.protocol = protoName(resp.protocol());

                    // 「探测 H2」：协议列表里必须留着 h1，所以只能在协商结果上判断。
                    // 不是 h2 就记一笔交回 WebView，等效于告诉用户这站没开 h2。
                    if (protoChoice == Net.PROTO_H2 && resp.protocol() != Protocol.HTTP_2) {
                        rec.kind = Net.Record.KIND_SKIPPED_AFTER;
                        rec.note = "服务端没协商到 HTTP/2（实际 " + rec.protocol + "）";
                        rec.finalUrl = current.toString();
                        rec.cost = System.currentTimeMillis() - t0;
                        store.addRecord(rec);
                        return null;
                    }
                    rec.responseHeaders = dumpResp(resp.headers());

                    String loc = resp.header("Location");

                    // 子资源手动跟随，便于把链记全
                    if (!mainFrame && loc != null && code >= 300 && code < 400 && hop < MAX_HOPS) {
                        chain.append(code).append(' ').append(current.toString()).append('\n');
                        chain.append("   -> ").append(loc).append('\n');
                        pushCookies(current.toString(), resp.headers("Set-Cookie"));
                        HttpUrl next = current.resolve(loc);
                        if (next == null) break;
                        current = next;
                        continue;
                    }

                    // WebResourceResponse 的构造器直接禁止 300-399，抛 IllegalArgumentException。
                    // 所以主文档的 3xx 无法"透传"，只能记一笔真实状态再返回 null，
                    // 让 WebView 自己重新请求并跟随跳转 —— 文档 URL 由 Chromium 维护，
                    // 相对路径才不会解析错。
                    // 任何 3xx 都不能构造 WebResourceResponse（statusCode 不允许 300–399），
                    // 所以不看有没有 Location，一律记一笔再交回 WebView。
                    if (mainFrame && code >= 300 && code < 400) {
                        rec.kind = Net.Record.KIND_SKIPPED_AFTER;
                        rec.note = code + (loc != null
                                ? " → 交回 WebView 自行跳转"
                                : " → 交回 WebView 重新请求");
                        rec.finalUrl = current.toString();
                        rec.cost = System.currentTimeMillis() - t0;
                        store.addRecord(rec);
                        return null;
                    }

                    ResponseBody body = resp.body();
                    long declared = body == null ? -1 : body.contentLength();

                    // 流媒体 / 超大响应不再"读一截再丢弃"，改成边下边传：
                    // 服务端只会收到一次请求，内存也不会被几十兆占住。
                    if (isStream(ct) || declared > MAX_BODY) {
                        handedOff = true;
                        boolean stream = isStream(ct);
                        InputStream bin = body == null
                                ? new ByteArrayInputStream(EMPTY) : body.byteStream();
                        return handOff(bin, resp, rec, current, ct, store, t0,
                                stream ? "流式透传（内容不进面板）"
                                        : "边下边传（超过 " + (MAX_BODY / 1024 / 1024) + "MB）");
                    }

                    ByteArrayOutputStream partial = new ByteArrayOutputStream();
                    byte[] data = readCapped(body, partial);
                    if (data == null) {
                        // 长度未知（chunked）但实际超过上限：已读到的部分 + 剩余字节接上去继续传
                        handedOff = true;
                        InputStream joined = new SequenceInputStream(
                                new ByteArrayInputStream(partial.toByteArray()), body.byteStream());
                        return handOff(joined, resp, rec, current, ct, store, t0,
                                "边下边传（长度未知且超过 " + (MAX_BODY / 1024 / 1024) + "MB）");
                    }

                    rec.size = data.length;
                    rec.cost = System.currentTimeMillis() - t0;
                    rec.mime = mimeOf(ct, current.toString(), data);
                    rec.finalUrl = current.toString();
                    rec.bodyPreview = preview(data, ct, rec.mime);
                    rec.chain = chain.toString();
                    pushCookies(current.toString(), resp.headers("Set-Cookie"));

                    // 主文档的原始字节留一份，供「源码 · 原文」视图使用。
                    // 这是服务器真实输出，比 outerHTML 更可信（注释、缩进、模板都在）。
                    if (mainFrame && isText(rec.mime)) {
                        try {
                            String html = new String(data,
                                    java.nio.charset.Charset.forName(
                                            detectCharset(ct, data, rec.mime)));
                            if (html.length() > 512 * 1024) {
                                html = html.substring(0, 512 * 1024)
                                        + "\n\n<!-- 已截断，原始长度 " + html.length() + " 字符 -->";
                            }
                            store.setSource(current.toString(), html);
                        } catch (Throwable ignored) { }
                    }

                    // 先构造再落库：构造器可能对状态码/参数抛异常，
                    // 先 add 会导致同一条记录被 catch 分支再加一遍。
                    WebResourceResponse out = new WebResourceResponse(rec.mime,
                            detectCharset(ct, data, rec.mime),
                            code, reason(code), clean(resp.headers()),
                            new ByteArrayInputStream(data));
                    store.addRecord(rec);
                    return out;
                } finally {
                    if (!handedOff) resp.close();
                }
            }
            chain.append("超过 ").append(MAX_HOPS).append(" 跳重定向，已放弃\n");

        } catch (Throwable t) {
            if (isCancel(t)) {
                // 换页时我们主动取消的请求：既不算失败，也不必回退给 WebView
                // （那一页已经走了，再让它重发只会多打一次服务端）
                return null;
            }
            rec.kind = Net.Record.KIND_FAILED;
            rec.status = -1;
            rec.cost = System.currentTimeMillis() - t0;
            rec.chain = chain.toString();
            rec.responseHeaders = "请求失败：" + t;
            if (isSslError(t)) {
                // 这里只标记，不弹窗：返回 null 后 WebView 会自己重试，
                // 由 onReceivedSslError 统一弹一次，否则会出现两个对话框
                rec.note = "TLS 证书不受信任";
            }
            store.addRecord(rec);
        }
        return null;
    }

    private static String cookieFor(String url) {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            return cm.getCookie(url);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 这几类头必须丢掉：Host/Connection 由客户端自己生成；
     * Accept-Encoding 交给 OkHttp 自动协商并解压；
     * 条件请求头会导致返回 304 空包，页面会变白。
     */
    private static boolean skip(String k) {
        String l = k.toLowerCase();
        return l.equals("host") || l.equals("connection") || l.equals("content-length")
                || l.equals("content-type") || l.equals("accept-encoding")
                || l.equals("transfer-encoding") || l.equals("upgrade")
                || l.equals("proxy-connection") || l.equals("if-none-match")
                || l.equals("if-modified-since") || l.equals("if-range")
                || l.equals("cookie");
    }

    private static String dumpResp(Headers h) {
        if (h == null || h.size() == 0) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.size(); i++) {
            sb.append(h.name(i)).append(": ").append(h.value(i)).append('\n');
        }
        return sb.toString().trim();
    }

    private static byte[] readCapped(ResponseBody body, ByteArrayOutputStream sink) throws IOException {
        if (body == null) return new byte[0];
        InputStream in = body.byteStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (sink.size() + n > MAX_BODY) return null;
            sink.write(buf, 0, n);
        }
        return sink.toByteArray();
    }

    /**
     * 边下边传：把 OkHttp 的 body 直接交给 WebView，内存里只留开头几 KB 做预览。
     * 以前这两档是"读一截就丢弃、再让 WebView 重新请求"，服务端会收到两次。
     */
    private static WebResourceResponse handOff(InputStream data, Response resp, Net.Record rec,
                                               HttpUrl current, String ct, Net.Store store,
                                               long t0, String note) {
        rec.kind = Net.Record.KIND_INTERCEPTED;
        rec.note = note;
        rec.mime = mimeOf(ct, current.toString(), EMPTY);
        rec.finalUrl = current.toString();
        rec.cost = System.currentTimeMillis() - t0;
        rec.responseHeaders = rec.responseHeaders
                + "\n\n(" + note + "，内容由 WebView 边读边渲染，面板只保留开头一段)";
        WebResourceResponse out = new WebResourceResponse(rec.mime,
                detectCharset(ct, EMPTY, rec.mime), rec.status, reason(rec.status),
                clean(resp.headers()),
                new TeeStream(data, resp, rec, store, isText(rec.mime), ct));
        store.addRecord(rec);
        return out;
    }

    /** 抄一份开头给面板做预览，读完后补上真实大小并刷新列表 */
    private static class TeeStream extends FilterInputStream {
        private final Response resp;
        private final Net.Record rec;
        private final Net.Store store;
        private final String ct;
        private final boolean text;
        private final ByteArrayOutputStream head = new ByteArrayOutputStream();
        private long total;
        private boolean closed;

        TeeStream(InputStream in, Response resp, Net.Record rec, Net.Store store,
                  boolean text, String ct) {
            super(in);
            this.resp = resp;
            this.rec = rec;
            this.store = store;
            this.text = text;
            this.ct = ct;
        }

        public int read() throws IOException {
            int c = super.read();
            if (c >= 0) {
                total++;
                if (text && head.size() < PREVIEW_BYTES) head.write(c);
            }
            return c;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                total += n;
                if (text) {
                    int room = PREVIEW_BYTES - head.size();
                    if (room > 0) head.write(b, off, Math.min(n, room));
                }
            }
            return n;
        }

        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try { super.close(); } catch (Throwable ignored) { }
            try { resp.close(); } catch (Throwable ignored) { }
            try {
                rec.size = total;
                if (text && head.size() > 0) {
                    rec.bodyPreview = preview(head.toByteArray(), ct, rec.mime);
                }
                store.touch();
            } catch (Throwable ignored) { }
        }
    }

    /** 换页时叫停上一页还在跑的请求：别让它们继续占并发名额和流量 */
    public static void cancelAll() {
        try {
            if (CLIENT_H1 != null) CLIENT_H1.dispatcher().cancelAll();
            if (CLIENT_H2 != null) CLIENT_H2.dispatcher().cancelAll();
            if (CLIENT_H1_INSECURE != null) CLIENT_H1_INSECURE.dispatcher().cancelAll();
            if (CLIENT_H2_INSECURE != null) CLIENT_H2_INSECURE.dispatcher().cancelAll();
        } catch (Throwable ignored) { }
    }

    /** 是不是被我们自己取消的（换页），这种情况不算请求失败 */
    private static boolean isCancel(Throwable t) {
        Throwable c = t;
        for (int i = 0; i < 6 && c != null; i++) {
            String m = c.getMessage();
            if (m != null && m.contains("Canceled")) return true;
            c = c.getCause();
        }
        return false;
    }

    private static boolean isStream(String ct) {
        if (ct == null) return false;
        String m = ct.toLowerCase();
        return m.startsWith("video/") || m.startsWith("audio/")
                || m.contains("event-stream") || m.contains("x-mixed-replace");
    }

    /** 回给 WebView 的头：去掉已失效的压缩与长度信息，避免长度不符导致渲染失败 */
    private static Map<String, String> clean(Headers h) {
        Map<String, String> out = new HashMap<String, String>();
        if (h == null) return out;
        for (int i = 0; i < h.size(); i++) {
            String k = h.name(i);
            String l = k.toLowerCase();
            if (l.equals("content-length") || l.equals("content-encoding")
                    || l.equals("transfer-encoding") || l.equals("set-cookie")
                    || l.equals("connection") || l.equals("keep-alive")) continue;
            String prev = out.get(k);
            out.put(k, prev == null ? h.value(i) : prev + ", " + h.value(i));
        }
        return out;
    }

    private static void pushCookies(String url, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            for (int i = 0; i < setCookies.size(); i++) {
                cm.setCookie(url, setCookies.get(i));
            }
            cm.flush();
        } catch (Throwable ignored) { }
    }

    private static String protoName(Protocol p) {
        if (p == null) return "";
        if (p == Protocol.HTTP_2) return "HTTP/2";
        if (p == Protocol.HTTP_1_1) return "HTTP/1.1";
        if (p == Protocol.H2_PRIOR_KNOWLEDGE) return "HTTP/2 (h2c)";
        return p.toString().toUpperCase();
    }

    /**
     * 缺 Content-Type 时不能一律给 octet-stream，否则 HTML 会被当成二进制，
     * 页面静默白屏。先按扩展名，再嗅探内容。
     */
    private static String mimeOf(String ct, String url, byte[] data) {
        if (ct != null) {
            int i = ct.indexOf(';');
            String m = (i > 0 ? ct.substring(0, i) : ct).trim();
            if (m.length() > 0) return m;
        }
        String ext = extOf(url);
        if (ext != null) {
            String m = BY_EXT.get(ext);
            if (m != null) return m;
        }
        if (looksHtml(data)) return "text/html";
        return "application/octet-stream";
    }

    private static String extOf(String url) {
        try {
            String p = HttpUrl.parse(url).encodedPath();
            int dot = p.lastIndexOf('.');
            int slash = p.lastIndexOf('/');
            if (dot < 0 || dot < slash) return null;
            String e = p.substring(dot + 1).toLowerCase();
            return e.length() > 6 ? null : e;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksHtml(byte[] data) {
        try {
            int start = 0;
            if (data.length > 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                    && (data[2] & 0xFF) == 0xBF) start = 3;
            int n = Math.min(data.length - start, 512);
            if (n <= 0) return false;
            String s = new String(data, start, n, Charset.forName("ISO-8859-1"))
                    .trim().toLowerCase();

            if (s.startsWith("<!doctype") || s.startsWith("<!--")
                    || s.startsWith("<?xml")) return true;

            // 片段式文档不一定以 doctype/html 开头，前 512 字节里出现任意常见标签就认作 HTML
            for (int t = 0; t < HTML_TAGS.length; t++) {
                if (hasTag(s, HTML_TAGS[t])) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasTag(String s, String tag) {
        String needle = "<" + tag;
        int i = s.indexOf(needle);
        while (i >= 0) {
            int after = i + needle.length();
            if (after >= s.length()) return false;
            char c = s.charAt(after);
            if (c == '>' || c == '/' || c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                return true;
            }
            i = s.indexOf(needle, i + 1);
        }
        return false;
    }

    /**
     * 响应头没写 charset 时，先看 <meta charset>，再判断是不是合法 UTF-8，
     * 都不是就按 GBK 兜底（国内老站绝大多数是 GBK/GB2312）。
     */
    private static String detectCharset(String ct, byte[] data, String mime) {
        if (ct != null) {
            int i = ct.toLowerCase().indexOf("charset=");
            if (i >= 0) {
                String c = ct.substring(i + 8).trim();
                int j = c.indexOf(';');
                if (j > 0) c = c.substring(0, j);
                c = c.trim().replace("\"", "");
                if (c.length() > 0) return c;
            }
        }
        if (!isText(mime)) return "utf-8";
        String meta = sniffMetaCharset(data);
        if (meta != null) return meta;
        if (looksUtf8(data)) return "utf-8";
        return "GBK";
    }

    private static boolean isText(String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase();
        return m.startsWith("text/") || m.contains("json") || m.contains("xml")
                || m.contains("javascript") || m.contains("urlencoded");
    }

    private static String sniffMetaCharset(byte[] data) {
        try {
            int n = Math.min(data.length, 4096);
            String s = new String(data, 0, n, Charset.forName("ISO-8859-1")).toLowerCase();
            int i = s.indexOf("charset=");
            if (i < 0) return null;
            int start = i + 8;
            while (start < s.length() && (s.charAt(start) == '"' || s.charAt(start) == '\'')) start++;
            int end = start;
            while (end < s.length() && end - start < 24) {
                char c = s.charAt(end);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') end++;
                else break;
            }
            if (end == start) return null;
            String cs = s.substring(start, end);
            Charset.forName(cs);
            return cs;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksUtf8(byte[] data) {
        try {
            int n = Math.min(data.length, SNIFF_BYTES);
            String s = new String(data, 0, n, Charset.forName("UTF-8"));
            int bad = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\uFFFD') bad++;
            }
            // 截断在多字节字符中间也会产生个别坏字符，允许 0.5% 以内的噪声
            return s.length() == 0 || bad * 200 < s.length();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String preview(byte[] data, String ct, String mime) {
        if (!isText(mime)) return "(二进制内容，" + data.length + " 字节)";
        try {
            String s = new String(data, Charset.forName(detectCharset(ct, data, mime)));
            if (s.length() > PREVIEW) s = s.substring(0, PREVIEW) + "\n…（已截断）";
            return s;
        } catch (Throwable t) {
            return "(解码失败)";
        }
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 206: return "Partial Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 303: return "See Other";
            case 304: return "Not Modified";
            case 307: return "Temporary Redirect";
            case 308: return "Permanent Redirect";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "";
        }
    }
}
