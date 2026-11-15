package com.phoneinspect;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

public class MainActivity extends Activity {

    private static final int C_BG = 0xFF0F1115;
    private static final int C_SURF = 0xFF191C22;
    private static final int C_LINE = 0xFF2A2F38;
    private static final int C_TEXT = 0xFFE6E8EB;
    private static final int C_DIM = 0xFF8B929E;
    private static final int C_ACC = 0xFF4C8DFF;

    private static final String JS =
            "(function(){if(window.__PI__)return;window.__PI__=1;"
            + "var send=function(l,a){try{var o=[];for(var i=0;i<a.length;i++){var v=a[i];"
            + "try{o.push(v!==null&&typeof v==='object'?JSON.stringify(v):String(v));}catch(e){o.push(String(v));}}"
            + "PI.log(l,o.join(' '));}catch(e){}};"
            + "var m=['log','info','warn','error','debug'];"
            + "for(var i=0;i<m.length;i++){(function(k){var f=console[k];"
            + "console[k]=function(){send(k,arguments);if(f)f.apply(console,arguments);};})(m[i]);}"
            + "window.addEventListener('error',function(e){"
            + "send('error',['未捕获: '+e.message+' @ '+(e.filename||'')+':'+(e.lineno||0)]);});"
            + "window.addEventListener('unhandledrejection',function(e){"
            + "send('error',['Promise: '+e.reason]);});"
            + "})();";

    private static final String HOME =
            "<html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<style>body{margin:0;background:#0F1115;color:#E6E8EB;padding:26px 22px;"
            + "font-family:-apple-system,'Noto Sans SC',sans-serif}"
            + "h1{font-size:20px;margin:0 0 6px;font-weight:600}"
            + ".sub{color:#8B929E;font-size:13px;line-height:1.7;margin:0 0 20px}"
            + ".card{background:#191C22;border:1px solid #2A2F38;border-radius:12px;"
            + "padding:14px 16px;margin-bottom:10px}"
            + ".t{color:#4C8DFF;font-size:13px;font-weight:600;margin-bottom:5px}"
            + ".d{color:#8B929E;font-size:12px;line-height:1.7}"
            + "b{color:#3FD07A;font-weight:600}</style></head><body>"
            + "<h1>PhoneInspect</h1>"
            + "<p class=sub>在地址栏输入网址，下方面板会实时列出每个请求的状态码、耗时与完整响应头。</p>"
            + "<div class=card><div class=t>拦截 GET</div><div class=d>原生层重发 GET 请求，可以看到 "
            + "<b>状态码、全部响应头、Set-Cookie、重定向链</b>。排查接口问题用这个。</div></div>"
            + "<div class=card><div class=t>仅记录</div><div class=d>只记录 URL 和请求头，不改动真实流量。"
            + "页面表现异常时切到它对照。</div></div>"
            + "<div class=card><div class=t>关闭</div><div class=d>完全等同普通浏览器，"
            + "用于确认问题是否由拦截本身引起。</div></div>"
            + "<div class=card><div class=t>控制台</div><div class=d>自动注入钩子，捕获 console、"
            + "未捕获异常与 Promise 拒绝。</div></div>"
            + "</body></html>";

    private WebView web;
    private EditText urlBar;
    private LinearLayout panel;
    private ListView list;
    private TextView tabNet, tabLog, tabInfo;
    private TextView[] modeChip;
    private TextView[] protoChip;
    private PanelAdapter adapter;

    private final Net.Store store = Net.Store.get();
    private int mode = Net.MODE_INTERCEPT;
    private int proto = Net.PROTO_AUTO;
    private int tab = PanelAdapter.TAB_NET;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);
        getWindow().setBackgroundDrawable(new ColorDrawable(C_BG));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        setContentView(root);

        root.addView(buildBar(), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        root.addView(buildModeRow(), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        web = new WebView(this);
        setupWeb();
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        panel = buildPanel();
        int ph = (int) (getResources().getDisplayMetrics().heightPixels * 0.40f);
        root.addView(panel, lp(LinearLayout.LayoutParams.MATCH_PARENT, ph));

        adapter = new PanelAdapter(this);
        list.setAdapter(adapter);

        store.netListener = new Runnable() {
            public void run() { refreshCounts(); }
        };
        store.logListener = new Runnable() {
            public void run() { refreshCounts(); }
        };

        CookieManager.getInstance().setAcceptCookie(true);
        setMode(Net.MODE_INTERCEPT, false);
        setProto(Net.PROTO_AUTO, false);
        setTab(PanelAdapter.TAB_NET);
    }

    private View buildBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_SURF);
        bar.setPadding(dp(4), dp(5), dp(6), dp(5));

        bar.addView(iconBtn("‹", new Runnable() {
            public void run() { if (web.canGoBack()) web.goBack(); }
        }));
        bar.addView(iconBtn("›", new Runnable() {
            public void run() { if (web.canGoForward()) web.goForward(); }
        }));
        bar.addView(iconBtn("⟳", new Runnable() {
            public void run() { web.reload(); }
        }));

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        urlBar.setTextColor(C_TEXT);
        urlBar.setHintTextColor(0xFF5A6270);
        urlBar.setHint("输入要调试的网址");
        urlBar.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setBackground(round(0xFF12141A, 8));
        urlBar.setPadding(dp(12), 0, dp(12), 0);
        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                go(urlBar.getText().toString());
                return true;
            }
        });

        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        ulp.leftMargin = dp(3);
        ulp.rightMargin = dp(5);
        bar.addView(urlBar, ulp);

        TextView g = iconBtn("GO", new Runnable() {
            public void run() { go(urlBar.getText().toString()); }
        });
        g.setTextColor(C_ACC);
        g.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        bar.addView(g);
        return bar;
    }

    private View buildModeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(C_SURF);
        row.setPadding(dp(8), 0, dp(4), dp(6));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);

        modeChip = new TextView[3];
        String[] names = {"拦截 GET", "仅记录", "关闭"};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView c = chip(names[i]);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { setMode(m, true); }
            });
            chips.addView(c, chipLp());
            modeChip[i] = c;
        }

        View sep = new View(this);
        sep.setBackgroundColor(C_LINE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(1), dp(16));
        slp.leftMargin = dp(2);
        slp.rightMargin = dp(9);
        chips.addView(sep, slp);

        protoChip = new TextView[3];
        String[] pn = {"自动", "H1", "H2"};
        for (int i = 0; i < 3; i++) {
            final int p = i;
            TextView c = chip(pn[i]);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { setProto(p, true); }
            });
            chips.addView(c, chipLp());
            protoChip[i] = c;
        }

        hs.addView(chips);
        row.addView(hs, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView tg = new TextView(this);
        tg.setText("面板");
        tg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tg.setTextColor(C_DIM);
        tg.setPadding(dp(10), dp(5), dp(10), dp(5));
        tg.setClickable(true);
        tg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                panel.setVisibility(panel.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            }
        });
        row.addView(tg);
        return row;
    }

    private TextView chip(String label) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(11), dp(5), dp(11), dp(5));
        c.setClickable(true);
        return c;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(6);
        return p;
    }

    private LinearLayout buildPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(C_SURF);

        View line = new View(this);
        line.setBackgroundColor(C_LINE);
        p.addView(line, lp(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(6), 0, dp(6), 0);

        tabNet = tabBtn(PanelAdapter.TAB_NET);
        tabLog = tabBtn(PanelAdapter.TAB_LOG);
        tabInfo = tabBtn(PanelAdapter.TAB_INFO);
        head.addView(tabNet);
        head.addView(tabLog);
        head.addView(tabInfo);
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView clr = smallBtn("清空", new Runnable() {
            public void run() {
                store.clearRecords();
                store.clearLogs();
                refreshCounts();
                Toast.makeText(MainActivity.this, "已清空", Toast.LENGTH_SHORT).show();
            }
        });
        TextView exp = smallBtn("导出", new Runnable() {
            public void run() { exportAll(); }
        });
        head.addView(clr);
        head.addView(exp);

        p.addView(head, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        list = new ListView(this);
        list.setBackgroundColor(C_BG);
        list.setDivider(new ColorDrawable(C_LINE));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(0x22FFFFFF));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                if (tab != PanelAdapter.TAB_NET) return;
                Net.Record r;
                synchronized (store.records) {
                    if (pos >= store.records.size()) return;
                    r = store.records.get(pos);
                }
                Detail.show(MainActivity.this, r);
            }
        });
        p.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return p;
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(C_BG);
        web.addJavascriptInterface(new Bridge(), "PI");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView v, int p) {
                if (p > 30) inject();
            }
        });
        web.loadDataWithBaseURL(null, HOME, "text/html", "utf-8", null);
    }

    private class Client extends WebViewClient {
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return false;
        }

        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            return handle(r);
        }

        public void onPageStarted(WebView v, String u, Bitmap f) {
            urlBar.setText(u);
            inject();
        }

        public void onPageFinished(WebView v, String u) {
            urlBar.setText(u);
            inject();
        }
    }

    private WebResourceResponse handle(WebResourceRequest r) {
        if (mode == Net.MODE_OFF) return null;

        String url = r.getUrl().toString();
        if (url.startsWith("data:") || url.startsWith("blob:")
                || url.startsWith("about:") || url.startsWith("javascript:")) {
            return null;
        }

        String method = r.getMethod();
        Map<String, String> h = r.getRequestHeaders();

        if (mode == Net.MODE_RECORD || !"GET".equalsIgnoreCase(method)) {
            Net.Record rec = new Net.Record();
            rec.method = method;
            rec.url = url;
            rec.mainFrame = r.isForMainFrame();
            rec.requestHeaders = Replay.dump(h);
            rec.responseHeaders = "(未拦截，无响应信息)";
            store.addRecord(rec);
            return null;
        }

        return Replay.fetch(url, h, r.isForMainFrame(), store, proto);
    }

    public class Bridge {
        @JavascriptInterface
        public void log(String level, String msg) {
            Net.LogItem l = new Net.LogItem();
            l.level = level == null || level.length() == 0 ? "log" : level;
            l.msg = msg == null ? "" : msg;
            store.addLog(l);
        }
    }

    private void inject() {
        if (web == null) return;
        web.evaluateJavascript(JS, null);
    }

    private void go(String input) {
        if (input == null) return;
        String u = input.trim();
        if (u.length() == 0) return;
        String low = u.toLowerCase();
        if (!low.startsWith("http://") && !low.startsWith("https://")
                && !low.startsWith("about:") && !low.startsWith("file:")) {
            u = "https://" + u;
        }
        web.loadUrl(u);
    }

    private void setMode(int m, boolean toast) {
        mode = m;
        for (int i = 0; i < 3; i++) {
            boolean on = (i == m);
            modeChip[i].setTextColor(on ? 0xFF0F1115 : C_DIM);
            modeChip[i].setBackground(round(on ? C_ACC : 0xFF232830, 6));
        }
        if (toast) {
            String[] hint = {
                    "拦截 GET：可看状态码与完整响应头",
                    "仅记录：只看请求，不改动真实流量",
                    "关闭：等同普通浏览器"
            };
            Toast.makeText(this, hint[m], Toast.LENGTH_SHORT).show();
        }
    }

    private void setProto(int p, boolean toast) {
        proto = p;
        for (int i = 0; i < 3; i++) {
            boolean on = (i == p);
            protoChip[i].setTextColor(on ? 0xFF0F1115 : C_DIM);
            protoChip[i].setBackground(round(on ? 0xFF3FD07A : 0xFF232830, 6));
        }
        if (toast) {
            String[] hint = {
                    "自动协商：h2 优先，服务端不支持则回落 h1",
                    "强制 HTTP/1.1",
                    "只接受 HTTP/2，服务端不支持会直接失败"
            };
            Toast.makeText(this, hint[p], Toast.LENGTH_SHORT).show();
            reloadIfLoaded();
        }
    }

    /** 切协议后必须重新加载才有意义，已加载的页面不会自动换协议 */
    private void reloadIfLoaded() {
        if (mode != Net.MODE_INTERCEPT || web == null) return;
        String u = web.getUrl();
        if (u == null || u.startsWith("data:") || u.startsWith("about:")) return;
        web.reload();
    }

    private void setTab(int t) {
        tab = t;
        TextView[] tabs = {tabNet, tabLog, tabInfo};
        for (int i = 0; i < 3; i++) {
            boolean on = (i == t);
            tabs[i].setTextColor(on ? C_ACC : C_DIM);
            tabs[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabs[i].setBackground(round(on ? 0xFF232830 : 0x00000000, 6));
        }
        if (t == PanelAdapter.TAB_INFO) adapter.setInfo(infoText());
        adapter.setTab(t);
        refreshCounts();
    }

    private void refreshCounts() {
        tabNet.setText("网络 " + store.recordCount());
        tabLog.setText("控制台 " + store.logCount());
        if (tab == PanelAdapter.TAB_INFO) adapter.setInfo(infoText());
        adapter.notifyDataSetChanged();
    }

    private String infoText() {
        StringBuilder sb = new StringBuilder();
        sb.append("设备  ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append('\n');
        sb.append("系统  Android ").append(android.os.Build.VERSION.RELEASE)
                .append("  (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("屏幕  ").append(getResources().getDisplayMetrics().widthPixels)
                .append(" × ").append(getResources().getDisplayMetrics().heightPixels).append('\n');
        sb.append("模式  ").append(mode == Net.MODE_INTERCEPT ? "拦截 GET"
                : mode == Net.MODE_RECORD ? "仅记录" : "关闭").append('\n');
        sb.append("协议  ").append(proto == Net.PROTO_H1 ? "仅 HTTP/1.1"
                : proto == Net.PROTO_H2 ? "仅 HTTP/2" : "自动（h2 优先）").append('\n');
        sb.append("记录  ").append(store.recordCount()).append(" 条请求 / ")
                .append(store.logCount()).append(" 条日志\n\n");
        sb.append("说明\n");
        sb.append("· 拦截模式下 GET 请求由原生层重发一次，因此能看到状态码、完整响应头、")
                .append("Set-Cookie 和重定向链。\n");
        sb.append("· POST / PUT 不重发（拿不到请求体），只记录 URL 与请求头。\n");
        sb.append("· 页面出现异常时，切到「仅记录」或「关闭」做对比。\n");
        sb.append("· 协议可随时切换；「仅 HTTP/2」在服务端不支持时会直接失败，正好用来探测有没有开 h2。\n");
        sb.append("· 点击列表任意请求查看详情，详情内文字可长按选中。\n");
        return sb.toString();
    }

    private void exportAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("# PhoneInspect 导出\n\n");
        synchronized (store.records) {
            for (int i = store.records.size() - 1; i >= 0; i--) {
                Net.Record r = store.records.get(i);
                sb.append(r.method).append(' ').append(r.url).append('\n');
                sb.append("  status=").append(r.status)
                        .append("  cost=").append(r.cost).append("ms")
                        .append("  size=").append(r.size).append("B\n");
                if (r.chain != null && r.chain.length() > 0) {
                    sb.append("  重定向链:\n");
                    String[] cl = r.chain.split("\n");
                    for (int k = 0; k < cl.length; k++) sb.append("    ").append(cl[k]).append('\n');
                }
                if (r.responseHeaders != null && r.responseHeaders.length() > 0) {
                    sb.append("  响应头:\n");
                    String[] hl = r.responseHeaders.split("\n");
                    for (int k = 0; k < hl.length; k++) sb.append("    ").append(hl[k]).append('\n');
                }
                sb.append('\n');
            }
        }
        if (sb.length() < 60) {
            Toast.makeText(this, "还没有记录", Toast.LENGTH_SHORT).show();
            return;
        }
        Detail.copy(this, sb.toString());
    }

    private TextView tabBtn(final int t) {
        TextView tv = new TextView(this);
        tv.setText(t == PanelAdapter.TAB_NET ? "网络 0"
                : t == PanelAdapter.TAB_LOG ? "控制台 0" : "信息");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(9), dp(6), dp(9), dp(6));
        tv.setClickable(true);
        tv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setTab(t); }
        });
        return tv;
    }

    private TextView smallBtn(String s, final Runnable r) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTextColor(C_DIM);
        tv.setPadding(dp(9), dp(5), dp(9), dp(5));
        tv.setClickable(true);
        tv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        return tv;
    }

    private TextView iconBtn(String s, final Runnable r) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        t.setTextColor(C_DIM);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(36));
        t.setHeight(dp(36));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int v) {
        return PanelAdapter.dp(this, v);
    }

    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    protected void onDestroy() {
        try {
            if (web != null) {
                web.removeJavascriptInterface("PI");
                web.destroy();
            }
        } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
