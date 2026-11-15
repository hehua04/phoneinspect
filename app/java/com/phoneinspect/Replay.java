package com.phoneinspect;

import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 WebView 的请求在原生层重放一次：
 * 拿到状态码、完整响应头（含 Set-Cookie）、重定向链，再把内容和头一起喂回给 WebView。
 */
public class Replay {

    private static final int MAX_HOPS = 6;
    private static final int MAX_BODY = 6 * 1024 * 1024;
    private static final int PREVIEW = 6000;

    public static String dump(Map<String, String> h) {
        if (h == null || h.isEmpty()) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : h.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    public static String dumpResponse(Map<String, List<String>> h) {
        if (h == null) return "(空)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : h.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            sb.append(k).append(": ");
            List<String> v = e.getValue();
            if (v != null) {
                for (int i = 0; i < v.size(); i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(v.get(i));
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static WebResourceResponse fetch(String url, Map<String, String> reqHeaders,
                                            boolean mainFrame, Net.Store store) {
        Net.Record rec = new Net.Record();
        rec.method = "GET";
        rec.url = url;
        rec.mainFrame = mainFrame;
        rec.requestHeaders = dump(reqHeaders);
        long t0 = System.currentTimeMillis();

        StringBuilder chain = new StringBuilder();
        String current = url;
        String extraCookie = null;
        HttpURLConnection conn = null;

        try {
            for (int hop = 0; hop <= MAX_HOPS; hop++) {
                conn = open(current, reqHeaders, extraCookie);

                int code = conn.getResponseCode();
                rec.status = code;
                rec.protocol = protoOf(conn.getHeaderField(0));
                Map<String, List<String>> hf = conn.getHeaderFields();
                rec.responseHeaders = dumpResponse(hf);
                rec.mime = mimeOf(conn.getContentType());

                String loc = conn.getHeaderField("Location");
                if (loc != null && code >= 300 && code < 400 && hop < MAX_HOPS) {
                    chain.append(code).append(' ').append(current).append('\n');
                    chain.append("   -> ").append(loc).append('\n');
                    pushCookies(current, hf);
                    String pairs = cookiePairs(hf);
                    if (pairs != null) extraCookie = mergeCookies(extraCookie, pairs);
                    conn.disconnect();
                    current = resolve(current, loc);
                    continue;
                }

                byte[] data = read(conn, code);
                rec.size = data.length;
                rec.cost = System.currentTimeMillis() - t0;
                rec.bodyPreview = preview(data, conn.getContentType());
                rec.chain = chain.toString();
                pushCookies(current, hf);
                store.addRecord(rec);

                return new WebResourceResponse(rec.mime, charsetOf(conn.getContentType()),
                        code, reason(code), clean(hf), new ByteArrayInputStream(data));
            }

            chain.append("超过 ").append(MAX_HOPS).append(" 跳重定向，已放弃\n");

        } catch (Throwable t) {
            rec.status = -1;
            rec.cost = System.currentTimeMillis() - t0;
            rec.chain = chain.toString();
            rec.responseHeaders = "请求失败：" + t;
            store.addRecord(rec);
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static HttpURLConnection open(String url, Map<String, String> reqHeaders,
                                          String extraCookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);

        if (reqHeaders != null) {
            for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                String k = e.getKey();
                if (k == null || skip(k)) continue;
                try {
                    if (e.getValue() != null) c.setRequestProperty(k, e.getValue());
                } catch (Throwable ignored) { }
            }
        }
        if (extraCookie != null && extraCookie.length() > 0) {
            c.setRequestProperty("Cookie", extraCookie);
        }
        return c;
    }

    /**
     * 这几类头必须丢掉：Host/Connection 由客户端自己生成；
     * Accept-Encoding 交给 HttpURLConnection 自动协商并解压；
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

    private static byte[] read(HttpURLConnection c, int code) throws Exception {
        InputStream is = null;
        try {
            is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
            if (is == null) return new byte[0];
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) {
                int allow = Math.min(n, MAX_BODY - bo.size());
                if (allow > 0) bo.write(buf, 0, allow);
                if (bo.size() >= MAX_BODY) break;
            }
            return bo.toByteArray();
        } finally {
            if (is != null) {
                try { is.close(); } catch (Throwable ignored) { }
            }
        }
    }

    /** 回给 WebView 的头：去掉已经失效的压缩与长度信息，避免长度不符导致渲染失败 */
    private static Map<String, String> clean(Map<String, List<String>> hf) {
        Map<String, String> out = new HashMap<String, String>();
        if (hf == null) return out;
        for (Map.Entry<String, List<String>> e : hf.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            String l = k.toLowerCase();
            if (l.equals("content-length") || l.equals("content-encoding")
                    || l.equals("transfer-encoding") || l.equals("set-cookie")
                    || l.equals("connection") || l.equals("keep-alive")) continue;
            List<String> v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < v.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(v.get(i));
            }
            out.put(k, sb.toString());
        }
        return out;
    }

    private static void pushCookies(String url, Map<String, List<String>> hf) {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            for (Map.Entry<String, List<String>> e : hf.entrySet()) {
                String k = e.getKey();
                if (k == null || !k.equalsIgnoreCase("set-cookie")) continue;
                List<String> v = e.getValue();
                if (v == null) continue;
                for (int i = 0; i < v.size(); i++) cm.setCookie(url, v.get(i));
            }
            cm.flush();
        } catch (Throwable ignored) { }
    }

    private static String cookiePairs(Map<String, List<String>> hf) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : hf.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.equalsIgnoreCase("set-cookie")) continue;
            List<String> v = e.getValue();
            if (v == null) continue;
            for (int i = 0; i < v.size(); i++) {
                String s = v.get(i);
                if (s == null) continue;
                int cut = s.indexOf(';');
                sb.append(cut > 0 ? s.substring(0, cut) : s).append("; ");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String mergeCookies(String old, String add) {
        if (old == null || old.length() == 0) return add;
        return old + add;
    }

    private static String resolve(String base, String loc) {
        try {
            return new URL(new URL(base), loc).toString();
        } catch (Throwable t) {
            return loc;
        }
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

    /** 从状态行取出真实的协议版本，例如 "HTTP/1.1 200 OK" -> "HTTP/1.1" */
    private static String protoOf(String statusLine) {
        if (statusLine == null) return "HTTP/1.1";
        int sp = statusLine.indexOf(' ');
        String p = sp > 0 ? statusLine.substring(0, sp) : statusLine;
        return p.startsWith("HTTP/") ? p : "HTTP/1.1";
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
