package com.phoneinspect;

import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 把 WebView 的请求在原生层重发一次：
 * 拿到状态码、完整响应头（含 Set-Cookie）、真实协议版本、重定向链，
 * 再把内容和头一起喂回给 WebView。
 */
public class Replay {

    private static final int MAX_HOPS = 6;
    private static final int MAX_BODY = 6 * 1024 * 1024;
    private static final int PREVIEW = 6000;

    private static volatile OkHttpClient CLIENT_AUTO;
    private static volatile OkHttpClient CLIENT_H1;
    private static volatile OkHttpClient CLIENT_H2;

    private static OkHttpClient client(int choice) {
        if (choice == Net.PROTO_H1) {
            if (CLIENT_H1 == null) CLIENT_H1 = build(Arrays.asList(Protocol.HTTP_1_1));
            return CLIENT_H1;
        }
        if (choice == Net.PROTO_H2) {
            if (CLIENT_H2 == null) CLIENT_H2 = build(Arrays.asList(Protocol.HTTP_2));
            return CLIENT_H2;
        }
        if (CLIENT_AUTO == null) {
            CLIENT_AUTO = build(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        }
        return CLIENT_AUTO;
    }

    private static OkHttpClient build(List<Protocol> ps) {
        return new OkHttpClient.Builder()
                .protocols(ps)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
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
                                            boolean mainFrame, Net.Store store, int protoChoice) {
        HttpUrl current = HttpUrl.parse(url);
        if (current == null) return null;

        Net.Record rec = new Net.Record();
        rec.method = "GET";
        rec.url = url;
        rec.mainFrame = mainFrame;
        rec.requestHeaders = dump(reqHeaders);
        long t0 = System.currentTimeMillis();

        OkHttpClient client = client(protoChoice);
        StringBuilder chain = new StringBuilder();
        String extraCookie = null;

        try {
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
                if (extraCookie != null && extraCookie.length() > 0) {
                    rb.header("Cookie", extraCookie);
                }

                Response resp = client.newCall(rb.build()).execute();
                try {
                    int code = resp.code();
                    rec.status = code;
                    rec.protocol = protoName(resp.protocol());
                    rec.mime = mimeOf(resp.header("Content-Type"));
                    rec.responseHeaders = dumpResp(resp.headers());

                    String loc = resp.header("Location");
                    if (loc != null && code >= 300 && code < 400 && hop < MAX_HOPS) {
                        chain.append(code).append(' ').append(current.toString()).append('\n');
                        chain.append("   -> ").append(loc).append('\n');
                        List<String> sc = resp.headers("Set-Cookie");
                        pushCookies(current.toString(), sc);
                        String pairs = cookiePairs(sc);
                        if (pairs != null) extraCookie = mergeCookies(extraCookie, pairs);
                        HttpUrl next = current.resolve(loc);
                        if (next == null) break;
                        current = next;
                        continue;
                    }

                    byte[] data = new byte[0];
                    if (resp.body() != null) {
                        data = resp.body().bytes();
                        if (data.length > MAX_BODY) {
                            byte[] cut = new byte[MAX_BODY];
                            System.arraycopy(data, 0, cut, 0, MAX_BODY);
                            data = cut;
                        }
                    }

                    rec.size = data.length;
                    rec.cost = System.currentTimeMillis() - t0;
                    rec.bodyPreview = preview(data, resp.header("Content-Type"));
                    rec.chain = chain.toString();
                    pushCookies(current.toString(), resp.headers("Set-Cookie"));
                    store.addRecord(rec);

                    return new WebResourceResponse(rec.mime,
                            charsetOf(resp.header("Content-Type")),
                            code, reason(code), clean(resp.headers()),
                            new ByteArrayInputStream(data));
                } finally {
                    resp.close();
                }
            }
            chain.append("超过 ").append(MAX_HOPS).append(" 跳重定向，已放弃\n");

        } catch (Throwable t) {
            rec.status = -1;
            rec.cost = System.currentTimeMillis() - t0;
            rec.chain = chain.toString();
            rec.responseHeaders = "请求失败：" + t;
            store.addRecord(rec);
        }
        return null;
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
                || l.equals("if-modified-since") || l.equals("if-range");
    }

    private static String dumpResp(Headers h) {
        if (h == null || h.size() == 0) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.size(); i++) {
            sb.append(h.name(i)).append(": ").append(h.value(i)).append('\n');
        }
        return sb.toString().trim();
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

    private static String cookiePairs(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < setCookies.size(); i++) {
            String s = setCookies.get(i);
            if (s == null) continue;
            int cut = s.indexOf(';');
            sb.append(cut > 0 ? s.substring(0, cut) : s).append("; ");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String mergeCookies(String old, String add) {
        if (old == null || old.length() == 0) return add;
        return old + add;
    }

    private static String protoName(Protocol p) {
        if (p == null) return "";
        if (p == Protocol.HTTP_2) return "HTTP/2";
        if (p == Protocol.HTTP_1_1) return "HTTP/1.1";
        if (p == Protocol.H2_PRIOR_KNOWLEDGE) return "HTTP/2 (h2c)";
        return p.toString().toUpperCase();
    }

    private static String preview(byte[] data, String ct) {
        String m = ct == null ? "" : ct.toLowerCase();
        boolean text = m.contains("text/") || m.contains("json") || m.contains("xml")
                || m.contains("javascript") || m.contains("urlencoded");
        if (!text) return "(二进制内容，" + data.length + " 字节)";
        try {
            String s = new String(data, Charset.forName(charsetOf(ct)));
            if (s.length() > PREVIEW) s = s.substring(0, PREVIEW) + "\n…（已截断）";
            return s;
        } catch (Throwable t) {
            return "(解码失败)";
        }
    }

    private static String charsetOf(String ct) {
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
        return "utf-8";
    }

    private static String mimeOf(String ct) {
        if (ct == null) return "application/octet-stream";
        int i = ct.indexOf(';');
        String m = (i > 0 ? ct.substring(0, i) : ct).trim();
        return m.length() == 0 ? "application/octet-stream" : m;
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
