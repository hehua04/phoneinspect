package com.phoneinspect;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Comparator;
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
            + "try{var mc=document.createElement('meta');mc.name='color-scheme';mc.content='light';"
            + "(document.head||document.documentElement).appendChild(mc);"
            + "document.documentElement.style.colorScheme='light';}catch(e){}"
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
            + "window.__pi_fmt=function(r){if(r===undefined)return 'undefined';"
            + "try{return typeof r==='string'?r:JSON.stringify(r);}catch(e){return String(r);}};"
            + "var ov=null,lastEl=null,sx=0,sy=0,moved=false;"
            + "function ovl(){if(!ov){ov=document.createElement('div');"
            + "ov.style.cssText='position:fixed;left:0;top:0;width:0;height:0;pointer-events:none;"
            + "z-index:2147483647;border:2px solid #4C8DFF;background:rgba(76,141,255,0.18);"
            + "display:none;box-sizing:border-box';document.documentElement.appendChild(ov);}return ov;}"
            + "function draw(){if(!lastEl)return;var o=ovl();var r=lastEl.getBoundingClientRect();"
            + "o.style.display='block';o.style.left=r.left+'px';o.style.top=r.top+'px';"
            + "o.style.width=r.width+'px';o.style.height=r.height+'px';}"
            + "function clean(s,n){return (s||'').replace(/\\s+/g,' ').trim().slice(0,n);}"
            + "function cls(el){var c=el.className;"
            + "return typeof c==='string'?(c.trim().split(/\\s+/)[0]||''):'';}"
            + "function path(el){var p=[],n=el,i=0;while(n&&n.nodeType===1&&i<5){"
            + "var s=n.tagName.toLowerCase();"
            + "if(n.id){p.unshift(s+'#'+n.id);break;}"
            + "var c=cls(n);if(c)s+='.'+c;"
            + "var par=n.parentNode;"
            + "if(par){var idx=1,sib=par.firstElementChild;while(sib&&sib!==n){"
            + "if(sib.tagName===n.tagName)idx++;sib=sib.nextElementSibling;}"
            + "if(idx>1)s+=':nth-of-type('+idx+')';}"
            + "p.unshift(s);n=n.parentNode;i++;}return p.join(' > ');}"
            + "function pick(x,y){var el=document.elementFromPoint(x,y);if(!el)return;"
            + "lastEl=el;draw();var r=el.getBoundingClientRect();var cs=getComputedStyle(el);"
            + "var o={tag:el.tagName.toLowerCase(),id:el.id||'',"
            + "cls:(typeof el.className==='string'?el.className:''),"
            + "path:path(el),text:clean(el.textContent,120),"
            + "rect:Math.round(r.left)+','+Math.round(r.top)+','+Math.round(r.width)+'x'+Math.round(r.height),"
            + "display:cs.display,position:cs.position,color:cs.color,bg:cs.backgroundColor,"
            + "fontSize:cs.fontSize,fontWeight:cs.fontWeight,margin:cs.margin,padding:cs.padding,"
            + "border:cs.borderTopWidth+' '+cs.borderTopStyle+' '+cs.borderTopColor,"
            + "zIndex:cs.zIndex,overflow:cs.overflow,opacity:cs.opacity};"
            + "PI.element(JSON.stringify(o));}"
            + "document.addEventListener('touchstart',function(e){if(!window.__PI_PICK)return;"
            + "try{var ae=document.activeElement;if(ae&&ae.blur)ae.blur();}catch(x){}"
            + "var t=e.touches[0];sx=t.clientX;sy=t.clientY;moved=false;},true);"
            + "document.addEventListener('touchmove',function(e){if(!window.__PI_PICK)return;"
            + "var t=e.touches[0];if(Math.abs(t.clientX-sx)>10||Math.abs(t.clientY-sy)>10)moved=true;},true);"
            + "document.addEventListener('touchend',function(e){if(!window.__PI_PICK)return;"
            + "if(moved)return;var t=e.changedTouches[0];e.preventDefault();e.stopPropagation();"
            + "pick(t.clientX,t.clientY);},true);"
            + "document.addEventListener('click',function(e){if(!window.__PI_PICK)return;"
            + "e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();},true);"
            + "window.addEventListener('scroll',function(){draw();},true);"
            + "window.addEventListener('resize',function(){draw();},true);"
            + "window.__PI_PICK=false;"
            + "window.__PI_SETPICK=function(on){window.__PI_PICK=!!on;"
            + "if(on){try{var ae=document.activeElement;if(ae&&ae.blur)ae.blur();}catch(x){}}"
            + "if(!on&&ov)ov.style.display='none';};"
            + "window.__PI_DOM=function(){var s='';"
            + "try{s=document.documentElement.outerHTML||'';}catch(e){s='';}"
            + "var total=s.length;var MAX=800000;"
            + "if(s.length>MAX){s=s.substring(0,MAX)+'\\n\\n<!-- 已截断，实际 '+total+' 字符 -->';}"
            + "PI.domReset();var SZ=60000;"
            + "for(var i=0;i<s.length;i+=SZ){PI.domChunk(s.substr(i,SZ));}"
            + "PI.domDone(location.href);};"
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
            + "未捕获异常与 Promise 拒绝；底部输入框可直接在页面里执行 JS。</div></div>"
            + "<div class=card><div class=t>元素</div><div class=d>点「开始拾取」后点页面元素，"
            + "看标签、选择器路径、尺寸和关键样式，高亮框跟随滚动。</div></div>"
            + "<div class=card><div class=t>源码</div><div class=d>「原文」是服务器返回的原始字节，"
            + "「DOM」是 JS 执行后的序列化结果，两者对照能看出页面被改了什么。</div></div>"
            + "</body></html>";

    private WebView web;
    private EditText urlBar;
    private LinearLayout panel;

    private TextView tabNet, tabLog, tabElem, tabSrc, tabInfo;
    private TextView btnClear, btnExport, btnPick;
    private TextView elemText, srcText, chipRaw, chipDom, infoBody;
    private LinearLayout logView, elementView, sourceView;
    private LinearLayout netView;
    private View infoView;
    private ListView netList, logList;
    private EditText evalInput;
    private PanelAdapter netAdapter, logAdapter;

    private TextView[] modeChip;
    private TextView[] protoChip;

    private final StringBuilder domBuf = new StringBuilder();

    private final Net.Store store = Net.Store.get();
    private int mode = Net.MODE_INTERCEPT;
    private int proto = Net.PROTO_AUTO;
    private int tab = PanelAdapter.TAB_NET;
    private boolean pickOn = false;
    private boolean srcDom = false;
    private boolean imeOpen = false;
    private boolean panelOn = true;
    private View rootView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        installCrashHandler();
        // 自己就是调试工具，开着让 chrome://inspect 也能接进来做对照
        WebView.setWebContentsDebuggingEnabled(true);

        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);
        getWindow().setBackgroundDrawable(new ColorDrawable(C_BG));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        setContentView(root);
        rootView = root;

        root.addView(buildBar(), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        root.addView(buildModeRow(), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        web = new WebView(this);
        setupWeb();
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        panel = buildPanel();
        root.addView(panel, lp(LinearLayout.LayoutParams.MATCH_PARENT, panelHeight()));
        setupImeWatcher(root);

        store.netListener = new Runnable() {
            public void run() { scheduleRefresh(); }
        };
        store.logListener = new Runnable() {
            public void run() { scheduleRefresh(); }
        };

        CookieManager.getInstance().setAcceptCookie(true);
        setMode(Net.MODE_INTERCEPT, false);
        setProto(Net.PROTO_AUTO, false);
        setTab(PanelAdapter.TAB_NET);
        reportLastCrash();
    }

    // ---- 崩溃兜底 ----

    /** 这个 App 本身就是调试工具，出问题不该还得找电脑看 logcat */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        final Context ctx = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    saveCrash(ctx, t, e);
                } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private static void saveCrash(Context ctx, Thread t, Throwable e) {
        FileOutputStream fos = null;
        try {
            File dir = ctx.getExternalFilesDir("crash");
            if (dir == null) return;
            if (!dir.exists() && !dir.mkdirs()) return;

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("time   : " + new java.util.Date());
            pw.println("thread : " + t.getName());
            pw.println();
            e.printStackTrace(pw);
            pw.flush();

            File f = new File(dir, "crash-" + System.currentTimeMillis() + ".txt");
            fos = new FileOutputStream(f);
            fos.write(sw.toString().getBytes("UTF-8"));

            File[] all = dir.listFiles();
            if (all != null && all.length > 5) {
                Arrays.sort(all, byTime(true));
                for (int i = 0; i < all.length - 5; i++) all[i].delete();
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (fos != null) fos.close(); } catch (Throwable ignored) { }
        }
    }

    private void reportLastCrash() {
        File dir = getExternalFilesDir("crash");
        if (dir == null) return;
        File[] all = dir.listFiles();
        if (all == null || all.length == 0) return;

        // 只播报没播报过的：播报过的文件名以 .seen 结尾。
        // 原来按 mtime 取最新再改名，可改名本身会把 mtime 刷新成"最新"，
        // 于是同一份崩溃每次启动都被重新播报一遍（现场留下了 .seen.seen.seen 这样的文件名）。
        File newest = null;
        for (int i = 0; i < all.length; i++) {
            File f = all[i];
            if (f.getName().endsWith(".seen")) continue;
            if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
        }
        if (newest == null) return;

        String text = readSmall(newest, 6000);
        if (text.length() == 0) return;

        // 改名，避免每次启动都重复播报同一次崩溃
        newest.renameTo(new File(dir, newest.getName() + ".seen"));

        Net.LogItem l = new Net.LogItem();
        l.level = "error";
        l.msg = "上次运行崩溃了（" + newest.getName() + "）\n" + text;
        store.addLog(l);
        Toast.makeText(this, "上次运行崩溃过，堆栈已放进「控制台」", Toast.LENGTH_LONG).show();
    }

    private static Comparator<File> byTime(final boolean asc) {
        return new Comparator<File>() {
            public int compare(File a, File b) {
                long x = a.lastModified();
                long y = b.lastModified();
                return asc ? (x < y ? -1 : (x == y ? 0 : 1)) : (x > y ? -1 : (x == y ? 0 : 1));
            }
        };
    }

    private static String readSmall(File f, int max) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            int len = (int) Math.min(max, f.length());
            byte[] buf = new byte[len];
            int n = in.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, "UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
        }
    }

    private int panelHeight() {
        return (int) (getResources().getDisplayMetrics().heightPixels * 0.40f);
    }

    /**
     * 节流而不是防抖：原来的 removeCallbacks + postDelayed 会被连续流量无限推后，
     * 页面一直在发请求时列表反而不刷新，看起来像卡死。现在最多 200ms 刷一次。
     */
    private void scheduleRefresh() {
        if (refreshPending) return;
        refreshPending = true;
        store.main.postDelayed(refreshTask, 200);
    }

    private boolean refreshPending = false;

    private final Runnable refreshTask = new Runnable() {
        public void run() {
            refreshPending = false;
            refreshCounts();
        }
    };

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
        urlBar.setPadding(dp(12), 0, dp(34), 0);
        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                go(urlBar.getText().toString());
                return true;
            }
        });
        // 键盘已经开着时（比如刚在控制台打过 JS）再点地址栏，imeOpen 不会变化，
        // 收不到 onGlobalLayout 的跳变，所以要在这里单独通知一次
        urlBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (has && imeOpen) applyPanel();
            }
        });

        final TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        clear.setTextColor(C_DIM);
        clear.setGravity(Gravity.CENTER);
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { urlBar.setText(""); }
        });
        urlBar.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        FrameLayout urlWrap = new FrameLayout(this);
        urlWrap.addView(urlBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                dp(26), dp(26), Gravity.END | Gravity.CENTER_VERTICAL);
        clp.rightMargin = dp(5);
        urlWrap.addView(clear, clp);

        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        ulp.leftMargin = dp(3);
        ulp.rightMargin = dp(4);
        bar.addView(urlWrap, ulp);

        TextView home = iconBtn("主页", new Runnable() {
            public void run() { goHome(); }
        });
        home.setTextColor(C_DIM);
        home.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        bar.addView(home);

        TextView g = iconBtn("GO", new Runnable() {
            public void run() { go(urlBar.getText().toString()); }
        });
        g.setTextColor(C_ACC);
        g.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        bar.addView(g);
        return bar;
    }

    private void goHome() {
        urlBar.setText("");
        String u = web.getUrl();
        if (u == null || u.startsWith("about:") || u.startsWith("data:")) {
            return; // 已经在主页，不重复压栈
        }
        int idx = web.copyBackForwardList().getCurrentIndex();
        if (idx > 0) {
            // 历史第一条永远是启动时载入的主页，直接跳回去而不是新开一条，
            // 否则每点一次「主页」就多一条记录，返回键要按很多次才能退出。
            web.goBackOrForward(-idx);
        } else {
            web.loadDataWithBaseURL(null, HOME, "text/html", "utf-8", null);
        }
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
        tg.setPadding(dp(8), dp(5), dp(6), dp(5));
        tg.setClickable(true);
        tg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                panelOn = !panelOn;
                applyPanel();
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
        // 6 个胶囊 + 面板要在一行里放得下，左右各 8dp 是实测不截断的临界值
        c.setPadding(dp(8), dp(5), dp(8), dp(5));
        c.setClickable(true);
        return c;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(5);
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
        head.setPadding(dp(6), 0, dp(4), 0);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);

        tabNet = tabBtn(PanelAdapter.TAB_NET);
        tabLog = tabBtn(PanelAdapter.TAB_LOG);
        tabElem = tabBtn(PanelAdapter.TAB_ELEM);
        tabSrc = tabBtn(PanelAdapter.TAB_SRC);
        tabInfo = tabBtn(PanelAdapter.TAB_INFO);
        tabs.addView(tabNet);
        tabs.addView(tabLog);
        tabs.addView(tabElem);
        tabs.addView(tabSrc);
        tabs.addView(tabInfo);
        hs.addView(tabs);
        head.addView(hs, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        btnClear = smallBtn("清空", new Runnable() {
            public void run() {
                store.clearRecords();
                store.clearLogs();
                refreshCounts();
                Toast.makeText(MainActivity.this, "已清空", Toast.LENGTH_SHORT).show();
            }
        });
        btnExport = smallBtn("导出", new Runnable() {
            public void run() { exportAll(); }
        });
        head.addView(btnClear);
        head.addView(btnExport);

        p.addView(head, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        FrameLayout content = new FrameLayout(this);
        p.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        netList = newList();
        netAdapter = new PanelAdapter(this);
        netAdapter.setTab(PanelAdapter.TAB_NET);
        netList.setAdapter(netAdapter);
        netList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                // 按行自带 id 反查，不用 position 直接索引 store.records：
                // 加载中新记录头插会使整表下移，按 position 取会打开邻居
                Net.Record r = netAdapter.recordFor(v, pos);
                if (r != null) Detail.show(MainActivity.this, r);
            }
        });
        // 网络页套一层：过滤条 + 列表。列表用 weight 吃掉剩下的高度
        netView = new LinearLayout(this);
        netView.setOrientation(LinearLayout.VERTICAL);
        netView.setBackgroundColor(C_BG);
        netView.addView(buildFilterBar(false), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        netView.addView(netList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(netView, full());

        logView = new LinearLayout(this);
        logView.setOrientation(LinearLayout.VERTICAL);
        logView.setBackgroundColor(C_BG);
        logList = newList();
        logAdapter = new PanelAdapter(this);
        logAdapter.setTab(PanelAdapter.TAB_LOG);
        logList.setAdapter(logAdapter);
        logView.addView(buildFilterBar(true), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        logView.addView(logList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        logView.addView(buildEvalRow(), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        content.addView(logView, full());

        elementView = buildElementView();
        content.addView(elementView, full());

        sourceView = buildSourceView();
        content.addView(sourceView, full());

        infoView = buildInfoView();
        content.addView(infoView, full());

        return p;
    }

    private ListView newList() {
        ListView l = new ListView(this);
        l.setBackgroundColor(C_BG);
        l.setDivider(new ColorDrawable(C_LINE));
        l.setDividerHeight(1);
        l.setSelector(new ColorDrawable(0x22FFFFFF));
        return l;
    }

    private FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    /** 目前选中的过滤分类（0 全部 / 1 失败 / 2 主文档） */
    private int netFilterKind = PanelAdapter.FILTER_ALL;
    private int logFilterKind = PanelAdapter.FILTER_ALL;

    /**
     * 过滤条：关键字 + 分类胶囊。
     * 只影响展示，数据一条都不动；tab 上会显示 "12/80" 表示过滤掉多少。
     */
    private LinearLayout buildFilterBar(final boolean forLog) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_SURF);
        bar.setPadding(dp(8), dp(2), dp(8), dp(2));

        final EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        q.setTextColor(C_TEXT);
        q.setHintTextColor(0xFF5A6270);
        q.setHint(forLog ? "过滤日志内容" : "过滤 URL / 方法 / 说明");
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setBackground(round(0xFF12141A, 8));
        q.setPadding(dp(8), 0, dp(8), 0);
        bar.addView(q, new LinearLayout.LayoutParams(0, dp(26), 1f));

        // 有内容时才出现，和地址栏的 ✕ 一个套路；不然清关键字得长按全选再删
        final TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        clear.setTextColor(0xFF7A828E);
        clear.setPadding(dp(7), 0, dp(7), 0);
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { q.setText(""); }
        });
        bar.addView(clear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        String[] names = forLog
                ? new String[]{"全部", "警告+", "错误"}
                : new String[]{"全部", "只看失败", "主文档"};
        final TextView[] chips = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            chips[i] = chip(names[i]);
            chips[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            chips[i].setPadding(dp(9), dp(3), dp(9), dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(26));
            lp.leftMargin = dp(6);
            bar.addView(chips[i], lp);
            chips[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (int k = 0; k < chips.length; k++) {
                        paintFilterChip(chips[k], k == idx);
                    }
                    applyFilter(forLog, q.getText().toString(), idx);
                }
            });
        }
        for (int i = 0; i < chips.length; i++) paintFilterChip(chips[i], i == 0);

        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) {
                clear.setVisibility(e.length() == 0 ? View.GONE : View.VISIBLE);
                applyFilter(forLog, e.toString(), forLog ? logFilterKind : netFilterKind);
            }
        });
        return bar;
    }

    private void paintFilterChip(TextView c, boolean on) {
        c.setTextColor(on ? 0xFF0F1115 : C_DIM);
        c.setBackground(round(on ? C_ACC : 0xFF232830, 6));
    }

    private void applyFilter(boolean forLog, String q, int kind) {
        if (forLog) {
            logFilterKind = kind;
            if (logAdapter != null) logAdapter.setLogFilter(q, kind);
        } else {
            netFilterKind = kind;
            if (netAdapter != null) netAdapter.setFilter(q, kind);
        }
        refreshCounts();
    }

    private View buildEvalRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundColor(C_SURF);
        r.setPadding(dp(8), dp(4), dp(6), dp(4));

        evalInput = new EditText(this);
        evalInput.setSingleLine(true);
        evalInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        evalInput.setTextColor(C_TEXT);
        evalInput.setHintTextColor(0xFF5A6270);
        evalInput.setHint("在页面里执行 JS，如 document.title");
        evalInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        evalInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        evalInput.setBackground(round(0xFF12141A, 8));
        evalInput.setPadding(dp(10), 0, dp(10), 0);
        evalInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                runEval();
                return true;
            }
        });
        r.addView(evalInput, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView run = smallBtn("运行", new Runnable() {
            public void run() { runEval(); }
        });
        run.setTextColor(C_ACC);
        r.addView(run);
        return r;
    }

    private LinearLayout buildElementView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(C_BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_SURF);
        bar.setPadding(dp(10), 0, dp(8), 0);

        btnPick = smallBtn("开始拾取", new Runnable() {
            public void run() { togglePick(); }
        });
        btnPick.setBackground(round(0xFF232830, 6));
        btnPick.setTextColor(C_TEXT);
        btnPick.setPadding(dp(10), dp(5), dp(10), dp(5));
        bar.addView(btnPick);

        TextView hint = new TextView(this);
        hint.setText("点页面元素即可选中，可正常滚动");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        hint.setTextColor(C_DIM);
        hint.setSingleLine(true);
        hint.setPadding(dp(10), 0, 0, 0);
        bar.addView(hint, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        v.addView(bar, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        ScrollView sc = new ScrollView(this);
        elemText = monoText();
        sc.addView(elemText);
        v.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }

    private LinearLayout buildSourceView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(C_BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_SURF);
        bar.setPadding(dp(10), 0, dp(6), 0);

        chipRaw = chip("原文");
        chipRaw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { setSrcMode(false); }
        });
        chipDom = chip("DOM");
        chipDom.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { setSrcMode(true); }
        });
        bar.addView(chipRaw, chipLp());
        bar.addView(chipDom, chipLp());
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        bar.addView(smallBtn("刷新", new Runnable() {
            public void run() {
                if (srcDom) captureDom();
                else {
                    renderSource();
                    Toast.makeText(MainActivity.this, "原文来自最近一次拦截的主文档",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }));
        bar.addView(smallBtn("复制", new Runnable() {
            public void run() {
                Detail.copy(MainActivity.this, srcText.getText().toString());
            }
        }));
        v.addView(bar, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        ScrollView sc = new ScrollView(this);
        srcText = monoText();
        sc.addView(srcText);
        v.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }

    private ScrollView buildInfoView() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(C_BG);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        infoBody = new TextView(this);
        infoBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        infoBody.setTextColor(0xFFB4BAC4);
        infoBody.setPadding(dp(14), dp(14), dp(14), dp(14));
        infoBody.setLineSpacing(dp(4), 1f);
        infoBody.setTextIsSelectable(true);
        box.addView(infoBody);

        box.addView(buildCacheRow());
        sc.addView(box);
        return sc;
    }

    /**
     * 浏览器自己的缓存和拦截层的记录是两套东西：
     * 301 默认被永久缓存，服务端改了以后浏览器仍然照着老 Location 跳，
     * 这种情况下网络面板里能看到正确的 301（那是重放拿到的），页面却还是跑到旧地址。
     */
    private LinearLayout buildCacheRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(14), 0, dp(14), dp(18));

        TextView hint = new TextView(this);
        hint.setText("浏览器缓存\n"
                + "上面的记录来自拦截层重放，绕过了缓存；页面实际走的是 WebView 自己的缓存。\n"
                + "301 默认会被永久缓存——服务端改了跳转，浏览器仍可能按老地址跳。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        hint.setTextColor(0xFF7C8593);
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding(0, dp(6), 0, dp(10));
        r.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView c1 = bigBtn("清 HTTP 缓存");
        c1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { confirmClear(false); }
        });
        row.addView(c1, new LinearLayout.LayoutParams(0, dp(38), 1f));

        TextView c2 = bigBtn("清缓存 + Cookie");
        c2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { confirmClear(true); }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp2.leftMargin = dp(8);
        row.addView(c2, lp2);

        r.addView(row);
        return r;
    }

    private TextView bigBtn(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(C_TEXT);
        t.setBackground(round(0xFF232830, 8));
        t.setClickable(true);
        return t;
    }

    private void confirmClear(final boolean withCookies) {
        String msg = withCookies
                ? "会清掉 HTTP 缓存、Cookie、localStorage 和 IndexedDB。\n\n"
                + "代价：所有站点的登录态会丢失。"
                : "只清 HTTP 缓存（含 301 跳转记录），登录态保留。";
        new AlertDialog.Builder(this)
                .setTitle("清缓存")
                .setMessage(msg)
                .setPositiveButton("清", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { doClear(withCookies); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doClear(boolean withCookies) {
        StringBuilder done = new StringBuilder();
        try {
            if (web != null) {
                web.clearCache(true);
                done.append("HTTP 缓存");
            }
        } catch (Throwable ignored) { }
        try {
            WebStorage.getInstance().deleteAllData();
            done.append(done.length() > 0 ? "、" : "").append("DOM Storage");
        } catch (Throwable ignored) { }
        if (withCookies) {
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.removeAllCookies(null);
                cm.flush();
                done.append("、Cookie");
            } catch (Throwable ignored) { }
        }
        Toast.makeText(this, "已清除：" + done + "，正在重新加载", Toast.LENGTH_SHORT).show();
        // 让用户马上看到效果：清完按一次刷新
        if (web != null) {
            String u = web.getUrl();
            if (u != null && !u.startsWith("data:") && !u.startsWith("about:")) web.reload();
        }
    }

    private TextView monoText() {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(C_TEXT);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setTextIsSelectable(true);
        t.setLineSpacing(dp(3), 1f);
        return t;
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
        // 底色必须是白的：WebView 的底色会从透明 body 的页面透出来，
        // 而默认 UA 样式里文字是黑的，深色底会让极简页面变成黑底黑字。
        web.setBackgroundColor(0xFFFFFFFF);
        // 深色渲染要三管齐下才关得掉，不同 WebView 版本认的 API 不一样：
        // 1) WebView.setForceDarkAllowed —— 框架新 API
        // 2) WebSettings.setForceDark —— 已废弃但部分 WebView 只认这个
        // 3) 注入 <meta name="color-scheme" content="light">（见 JS 开头）
        if (Build.VERSION.SDK_INT >= 29) {
            web.setForceDarkAllowed(false);
            try {
                s.setForceDark(WebSettings.FORCE_DARK_OFF);
            } catch (Throwable ignored) { }
        }
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
            resetPick();
            // 换了文档，上一页拾取到的元素信息一定失效。
            // 不清掉的话，在新页面上点「元素」会看到上一站的标签和样式（实测过）
            store.setElement("");
            if (tab == PanelAdapter.TAB_ELEM) renderElement();
            setUrlText(u);
            inject();
        }

        public void onPageFinished(WebView v, String u) {
            setUrlText(u);
            inject();
        }

        public void onReceivedSslError(WebView v, SslErrorHandler handler, SslError error) {
            promptWebSsl(handler, error);
        }
    }

    /** 主页是内嵌的 data 页面，显示 about:blank 没有意义，直接清空让提示语露出来 */
    private void setUrlText(String u) {
        if (u == null) return;
        if (u.startsWith("about:") || u.startsWith("data:")) {
            urlBar.setText("");
            return;
        }
        urlBar.setText(u);
    }

    private WebResourceResponse handle(WebResourceRequest r) {
        // 这个方法是 WebView 在后台线程回调的，任何未捕获异常都会直接杀掉进程，
        // 所以整段兜底：出问题就记一条然后交回 WebView，绝不让 App 闪退。
        try {
            if (mode == Net.MODE_OFF) return null;

            String url = r.getUrl() == null ? "" : r.getUrl().toString();
            if (url.length() == 0
                    || url.startsWith("data:") || url.startsWith("blob:")
                    || url.startsWith("about:") || url.startsWith("javascript:")) {
                return null;
            }

            String method = r.getMethod() == null ? "GET" : r.getMethod();
            Map<String, String> h = r.getRequestHeaders();

            // 主文档请求 = 一次新导航的分界点。用它而不是 onPageStarted：
            // onPageStarted 比 shouldInterceptRequest 晚，主文档那条记录会被算进上一段
            int nav;
            String page;
            if (r.isForMainFrame()) {
                // 新页开始：上一页还在跑的请求没必要再跑完
                Replay.cancelAll();
                nav = store.startNav(url);
                page = url;
            } else {
                nav = store.currentNav;
                page = store.currentPageUrl;
            }

            if (mode == Net.MODE_RECORD || !"GET".equalsIgnoreCase(method)) {
                Net.Record rec = new Net.Record();
                rec.kind = Net.Record.KIND_SKIPPED_BEFORE;
                rec.navId = nav;
                rec.pageUrl = page;
                rec.method = method;
                rec.url = url;
                rec.mainFrame = r.isForMainFrame();
                rec.note = mode == Net.MODE_RECORD ? "仅记录模式，未重放" : "非 GET，未重放";
                rec.requestHeaders = Replay.dump(h);
                rec.responseHeaders = "(未拦截，无响应信息)";
                store.addRecord(rec);
                return null;
            }

            return Replay.fetch(url, h, r.isForMainFrame(), store, proto, nav, page);

        } catch (Throwable t) {
            try {
                Net.Record rec = new Net.Record();
                rec.kind = Net.Record.KIND_FAILED;
                rec.navId = store.currentNav;
                rec.pageUrl = store.currentPageUrl;
                rec.method = "?";
                rec.url = String.valueOf(r.getUrl());
                rec.note = "拦截层异常";
                rec.responseHeaders = "拦截层异常，已回退到 WebView 原生请求：\n" + t;
                store.addRecord(rec);
            } catch (Throwable ignored) { }
            return null;
        }
    }

    public class Bridge {
        @JavascriptInterface
        public void log(String level, String msg) {
            Net.LogItem l = new Net.LogItem();
            l.navId = store.currentNav;
            l.pageUrl = store.currentPageUrl;
            l.level = level == null || level.length() == 0 ? "log" : level;
            l.msg = msg == null ? "" : msg;
            store.addLog(l);
        }

        @JavascriptInterface
        public void element(String json) {
            final String text = formatElement(json);
            store.setElement(text);
            store.main.post(new Runnable() {
                public void run() {
                    if (tab == PanelAdapter.TAB_ELEM) elemText.setText(text);
                }
            });
        }

        @JavascriptInterface
        public void domReset() {
            synchronized (domBuf) { domBuf.setLength(0); }
        }

        @JavascriptInterface
        public void domChunk(String s) {
            if (s == null) return;
            synchronized (domBuf) {
                if (domBuf.length() < 900000) domBuf.append(s);
            }
        }

        @JavascriptInterface
        public void domDone(String href) {
            final String s;
            synchronized (domBuf) { s = domBuf.toString(); }
            // 带上抓取时的地址，源码页才能判断这份 DOM 是不是当前页面的
            store.setDom(href, s);
            store.main.post(new Runnable() {
                public void run() {
                    if (tab == PanelAdapter.TAB_SRC && srcDom) srcText.setText(s);
                }
            });
        }
    }

    private void inject() {
        if (web == null) return;
        web.evaluateJavascript(JS, null);
    }

    private String lastGoUrl = "";
    private long lastGoAt = 0;

    private void go(String input) {
        if (input == null) return;
        String u = input.trim();
        if (u.length() == 0) return;
        String low = u.toLowerCase();
        if (!low.startsWith("http://") && !low.startsWith("https://")
                && !low.startsWith("about:") && !low.startsWith("file:")) {
            u = "https://" + u;
        }
        // 软键盘的回车在部分输入法上会触发两次 onEditorAction（编辑器动作 + 硬件 ENTER 键），
        // 表现为一次导航发两遍请求。同一地址短时间只放行一次。
        long now = System.currentTimeMillis();
        if (u.equals(lastGoUrl) && now - lastGoAt < 600) return;
        lastGoUrl = u;
        lastGoAt = now;
        hideKeyboard();
        web.loadUrl(u);
    }

    /** 导航后把焦点从地址栏拿走，否则地址栏握着焦点，页面里的输入框拿不到输入法 */
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && urlBar != null) {
                imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
            }
            if (urlBar != null) urlBar.clearFocus();
        } catch (Throwable ignored) { }
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
                    "探测 HTTP/2：没协商到 h2 就标记失败并交回 WebView"
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
        if (t != PanelAdapter.TAB_ELEM && pickOn) togglePick();
        tab = t;

        TextView[] tabs = {tabNet, tabLog, tabElem, tabSrc, tabInfo};
        int[] ids = {PanelAdapter.TAB_NET, PanelAdapter.TAB_LOG, PanelAdapter.TAB_ELEM,
                PanelAdapter.TAB_SRC, PanelAdapter.TAB_INFO};
        for (int i = 0; i < tabs.length; i++) {
            boolean on = (ids[i] == t);
            tabs[i].setTextColor(on ? C_ACC : C_DIM);
            tabs[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabs[i].setBackground(round(on ? 0xFF232830 : 0x00000000, 6));
        }

        netView.setVisibility(t == PanelAdapter.TAB_NET ? View.VISIBLE : View.GONE);
        logView.setVisibility(t == PanelAdapter.TAB_LOG ? View.VISIBLE : View.GONE);
        elementView.setVisibility(t == PanelAdapter.TAB_ELEM ? View.VISIBLE : View.GONE);
        sourceView.setVisibility(t == PanelAdapter.TAB_SRC ? View.VISIBLE : View.GONE);
        infoView.setVisibility(t == PanelAdapter.TAB_INFO ? View.VISIBLE : View.GONE);

        btnClear.setVisibility(t == PanelAdapter.TAB_NET || t == PanelAdapter.TAB_LOG
                ? View.VISIBLE : View.GONE);
        btnExport.setVisibility(t == PanelAdapter.TAB_NET ? View.VISIBLE : View.GONE);

        if (t == PanelAdapter.TAB_ELEM) renderElement();
        if (t == PanelAdapter.TAB_SRC) setSrcMode(srcDom);
        if (t == PanelAdapter.TAB_INFO) infoBody.setText(infoText());

        refreshCounts();
    }

    private void refreshCounts() {
        if (netAdapter != null) netAdapter.notifyDataSetChanged();
        if (logAdapter != null) logAdapter.notifyDataSetChanged();

        // 过滤生效时显示 "12/80"，避免误以为"只有 12 条请求"
        int total = store.recordCount();
        int vis = netAdapter == null ? total : netAdapter.visibleCount();
        tabNet.setText(vis == total ? "网络 " + total : "网络 " + vis + "/" + total);

        int lTotal = store.logCount();
        int lVis = logAdapter == null ? lTotal : logAdapter.visibleCount();
        tabLog.setText(lVis == lTotal ? "控制台 " + lTotal : "控制台 " + lVis + "/" + lTotal);
    }

    // ---- 证书例外 ----

    private boolean sslDialogShowing = false;

    private String hostKeyOf(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            String host = u.getHost();
            if (host == null) return "";
            int port = u.getPort();
            if (port < 0) port = "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
            return host + ":" + port;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 证书错误的唯一入口。重放层失败后返回 null，WebView 会自己重试，
     * 于是走到这里 —— 只在这条路径弹窗，避免同一请求弹两次。
     */
    private void promptWebSsl(final SslErrorHandler handler, SslError error) {
        if (handler == null) return;
        final String key = hostKeyOf(error == null ? "" : error.getUrl());

        // 本会话已信任过就直接放行，否则每个子资源都会弹一次
        if (key.length() > 0 && store.insecureHosts.contains(key)) {
            handler.proceed();
            return;
        }
        if (isFinishing() || sslDialogShowing) {
            handler.cancel();
            return;
        }
        sslDialogShowing = true;
        final String url = error == null ? "" : error.getUrl();

        new AlertDialog.Builder(this)
                .setTitle("证书不受信任")
                .setMessage(url + "\n\n" + describeSsl(error)
                        + "\n\n继续访问会关闭该站点（含重放层）的证书校验，"
                        + "流量可能被窃听或篡改，仅建议在调试自有服务时使用。\n\n"
                        + "该例外只在本次运行内有效，重启 App 后会重新询问。")
                .setPositiveButton("继续访问（不安全）", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        sslDialogShowing = false;
                        if (key.length() > 0) store.insecureHosts.add(key);
                        // 直接放行这次请求；后续子资源由重放层接手，那边已经信任该 host，
                        // 所以不需要 reload（cancel + reload 会把已中止的导航退回上一页）
                        handler.proceed();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        sslDialogShowing = false;
                        handler.cancel();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface d) {
                        sslDialogShowing = false;
                        handler.cancel();
                    }
                })
                .show();
    }

    private String describeSsl(SslError e) {
        if (e == null) return "证书校验失败";
        switch (e.getPrimaryError()) {
            case SslError.SSL_EXPIRED: return "证书已过期";
            case SslError.SSL_IDMISMATCH: return "证书域名与访问地址不匹配";
            case SslError.SSL_UNTRUSTED: return "证书颁发机构不受信任（多为自签名）";
            case SslError.SSL_NOTYETVALID: return "证书尚未生效";
            case SslError.SSL_DATE_INVALID: return "证书日期无效";
            case SslError.SSL_INVALID: return "证书格式无效";
            default: return "证书校验失败";
        }
    }

    // ---- 元素拾取 ----

    private void togglePick() {
        pickOn = !pickOn;
        applyPick();
    }

    private void applyPick() {
        if (web != null) {
            if (pickOn) hideWebKeyboard();
            web.evaluateJavascript("window.__PI_SETPICK&&window.__PI_SETPICK("
                    + (pickOn ? "true" : "false") + ");", null);
        }
        updatePickBtn();
    }

    /** 拾取模式不该弹键盘：除了 JS 侧 blur，原生也主动收一次 */
    private void hideWebKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && web != null) {
                imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
            }
        } catch (Throwable ignored) { }
    }

    private void updatePickBtn() {
        if (btnPick == null) return;
        btnPick.setText(pickOn ? "停止拾取" : "开始拾取");
        btnPick.setTextColor(pickOn ? 0xFF0F1115 : C_TEXT);
        btnPick.setBackground(round(pickOn ? C_ACC : 0xFF232830, 6));
    }

    /** 页面换文档后注入的脚本被重置，拾取状态要跟着复位，否则按钮显示与实际不符 */
    private void resetPick() {
        if (!pickOn) return;
        pickOn = false;
        updatePickBtn();
    }

    private void renderElement() {
        if (elemText == null) return;
        String s = store.elementInfo;
        elemText.setText(s == null || s.length() == 0
                ? "点「开始拾取」，再点页面上的元素。\n\n"
                + "· 拾取模式下页面不会响应点击，但可以正常滚动\n"
                + "· 点「停止拾取」恢复页面的正常交互\n"
                + "· 跨域 iframe 里的元素选不进去（同源可以）\n"
                + "· 高亮框会跟随滚动和缩放重算位置"
                : s);
    }

    // ---- 源码 ----

    private void setSrcMode(boolean dom) {
        srcDom = dom;
        if (chipRaw != null) {
            chipRaw.setTextColor(dom ? C_DIM : 0xFF0F1115);
            chipRaw.setBackground(round(dom ? 0x00000000 : C_ACC, 6));
            chipDom.setTextColor(dom ? 0xFF0F1115 : C_DIM);
            chipDom.setBackground(round(dom ? C_ACC : 0x00000000, 6));
        }
        if (dom) {
            boolean empty = store.domHtml == null || store.domHtml.length() == 0;
            // 上一站抓的 DOM 不能再冒充当前页：换站后自动重抓一次
            if (empty || isForeign(store.domUrl)) {
                captureDom();
                return;
            }
        }
        renderSource();
    }

    private void renderSource() {
        if (srcText == null) return;
        if (srcDom) {
            String s = store.domHtml;
            if (s == null || s.length() == 0) {
                srcText.setText("正在抓取当前 DOM…\n\n"
                        + "DOM 是 JS 执行后的结果，不含 shadow DOM，input 里的当前输入值"
                        + "也不会出现在 outerHTML 中。");
            } else {
                srcText.setText(foreignNote(store.domUrl) + s);
            }
        } else {
            String s = store.sourceHtml;
            if (s == null || s.length() == 0) {
                srcText.setText("还没有拿到原文。\n\n"
                        + "原文是拦截层从服务器响应里拿到的原始字节，"
                        + "需要处于「拦截 GET」模式并刷新一次页面。\n\n"
                        + "它比 DOM 快照更可信：注释、原始缩进、未被 JS 渲染的模板都在。");
            } else {
                srcText.setText(foreignNote(store.sourceUrl) + s);
            }
        }
    }

    /** 这份数据抓取时的地址是否已经不属于当前页面（按 host 判断） */
    private boolean isForeign(String fromUrl) {
        String a = hostOf(fromUrl);
        String b = hostOf(web == null ? "" : web.getUrl());
        return a.length() > 0 && b.length() > 0 && !a.equals(b);
    }

    /** 不删数据，只在前面加一行来源说明——删了就没法回看上一站的内容了 */
    private String foreignNote(String fromUrl) {
        if (!isForeign(fromUrl)) return "";
        String a = hostOf(fromUrl);
        String b = hostOf(web == null ? "" : web.getUrl());
        return "注意：以下内容来自 " + a + "，不属于当前页面 " + b + "\n"
                + "（页面已跳转，或当前处于「仅记录」模式，没有为新页面重新抓取）\n\n";
    }

    private static String hostOf(String u) {
        if (u == null) return "";
        int i = u.indexOf("://");
        if (i < 0) return "";
        String s = u.substring(i + 3);
        int e = s.length();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '/' || c == '?' || c == '#') { e = k; break; }
        }
        s = s.substring(0, e);
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && s.indexOf(']') < colon) s = s.substring(0, colon);
        return s.toLowerCase();
    }

    private void captureDom() {
        domBuf.setLength(0);
        // 换站后重抓时，先退回"正在抓取"占位：
        // 继续显示上一站的 DOM 会让人以为当前页就长这样（抓取要几十毫秒到几百毫秒）
        if (isForeign(store.domUrl)) store.setDom(store.domUrl, "");
        renderSource();
        if (web != null) {
            web.evaluateJavascript("window.__PI_DOM&&window.__PI_DOM();", null);
        }
    }

    // ---- 执行 JS ----

    private void runEval() {
        if (evalInput == null || web == null) return;
        final String code = evalInput.getText().toString().trim();
        if (code.length() == 0) return;
        evalInput.setText("");

        String lit;
        try {
            lit = JSONObject.quote(code);
        } catch (Throwable t) {
            lit = "\"\"";
        }
        String js = "(function(){try{var r=eval(" + lit + ");"
                + "return window.__pi_fmt?window.__pi_fmt(r):String(r);}"
                + "catch(e){return 'ERROR: '+(e&&e.message?e.message:e);}})()";

        web.evaluateJavascript(js, new ValueCallback<String>() {
            public void onReceiveValue(String v) {
                Net.LogItem l = new Net.LogItem();
                l.level = "eval";
                l.msg = code + "\n→ " + unescape(v);
                store.addLog(l);
            }
        });
    }

    private static String unescape(String v) {
        if (v == null) return "null";
        try {
            return String.valueOf(new JSONTokener(v).nextValue());
        } catch (Throwable t) {
            return v;
        }
    }

    private String formatElement(String json) {
        try {
            JSONObject o = new JSONObject(json);
            StringBuilder sb = new StringBuilder();
            sb.append('<').append(o.optString("tag")).append('>');
            String id = o.optString("id");
            if (id.length() > 0) sb.append("  #").append(id);
            String cls = o.optString("cls");
            if (cls.length() > 0) {
                sb.append("\nclass  ").append(cls);
            }
            sb.append("\n\n路径  ").append(o.optString("path"));
            sb.append("\n位置  ").append(o.optString("rect")).append("   (x, y, 宽x高)");
            String tx = o.optString("text");
            if (tx.length() > 0) sb.append("\n文本  ").append(tx);

            sb.append("\n\n── 样式 ──\n");
            kv(sb, "display", o.optString("display"));
            kv(sb, "position", o.optString("position"));
            kv(sb, "color", o.optString("color"));
            kv(sb, "background", o.optString("bg"));
            kv(sb, "font-size", o.optString("fontSize"));
            kv(sb, "font-weight", o.optString("fontWeight"));
            kv(sb, "margin", o.optString("margin"));
            kv(sb, "padding", o.optString("padding"));
            kv(sb, "border", o.optString("border"));
            kv(sb, "z-index", o.optString("zIndex"));
            kv(sb, "overflow", o.optString("overflow"));
            kv(sb, "opacity", o.optString("opacity"));
            return sb.toString();
        } catch (Throwable t) {
            return "解析失败：" + t + "\n\n" + json;
        }
    }

    private void kv(StringBuilder sb, String k, String v) {
        sb.append("  ").append(k);
        for (int i = k.length(); i < 12; i++) sb.append(' ');
        sb.append(v).append('\n');
    }

    private String infoText() {
        StringBuilder sb = new StringBuilder();
        sb.append("版本  PhoneInspect ").append(versionText()).append('\n');
        sb.append("设备  ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append('\n');
        sb.append("系统  Android ").append(android.os.Build.VERSION.RELEASE)
                .append("  (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("屏幕  ").append(getResources().getDisplayMetrics().widthPixels)
                .append(" × ").append(getResources().getDisplayMetrics().heightPixels).append('\n');
        sb.append("模式  ").append(mode == Net.MODE_INTERCEPT ? "拦截 GET"
                : mode == Net.MODE_RECORD ? "仅记录" : "关闭").append('\n');
        sb.append("协议  ").append(proto == Net.PROTO_H1 ? "仅 HTTP/1.1"
                : proto == Net.PROTO_H2 ? "探测 HTTP/2" : "自动（h2 优先）").append('\n');
        sb.append("记录  ").append(store.recordCount()).append(" 条请求 / ")
                .append(store.logCount()).append(" 条日志\n\n");
        sb.append("说明\n");
        sb.append("· 拦截模式下 GET 请求由原生层重发一次，因此能看到状态码、完整响应头、")
                .append("Set-Cookie 和重定向链。\n");
        sb.append("· POST / PUT 不重发（拿不到请求体），只记录 URL 与请求头。\n");
        sb.append("· 页面出现异常时，切到「仅记录」或「关闭」做对比。\n");
        sb.append("· 协议可随时切换；「仅 HTTP/2」在服务端不支持时会直接失败，正好用来探测有没有开 h2。\n");
        sb.append("· 超过 4MB 或流媒体不重放，会标为「跳过重放」并交回 WebView，该请求实际会发两次。\n");
        sb.append("· 主文档的 3xx 不由工具跟随，交回 WebView 自己跳，否则页面里的相对路径会解析错。\n");
        sb.append("· 点击列表任意请求查看详情，详情内文字可长按选中。\n");
        sb.append("· 「元素」tab：开启拾取后点页面元素，可看标签、选择器路径、尺寸和关键样式。\n");
        sb.append("· 「源码」tab：原文来自拦截层拿到的服务器字节，DOM 是 JS 执行后的序列化结果。\n");
        sb.append("· 「控制台」底部输入框可直接在页面上下文执行 JS。\n");
        return sb.toString();
    }

    private String versionText() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            long vc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            return pi.versionName + " (" + vc + ")";
        } catch (Throwable t) {
            return "?";
        }
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
        // 记录里含 Set-Cookie，写文件而不是塞剪贴板（剪贴板对其它 App 可读）
        File dir = getExternalFilesDir("export");
        if (dir == null) {
            Toast.makeText(this, "无法访问存储目录", Toast.LENGTH_SHORT).show();
            return;
        }
        dir.mkdirs();
        File f = new File(dir, "phoneinspect-" + System.currentTimeMillis() + ".txt");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            w.write(sb.toString());
            w.flush();
            Toast.makeText(this, "已导出到\n" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "导出失败：" + t, Toast.LENGTH_SHORT).show();
        } finally {
            try { if (fos != null) fos.close(); } catch (Throwable ignored) { }
        }
    }

    private TextView tabBtn(final int t) {
        TextView tv = new TextView(this);
        tv.setText(t == PanelAdapter.TAB_NET ? "网络 0"
                : t == PanelAdapter.TAB_LOG ? "控制台 0"
                : t == PanelAdapter.TAB_ELEM ? "元素"
                : t == PanelAdapter.TAB_SRC ? "源码" : "信息");
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
        if (panel != null && panel.getVisibility() == View.VISIBLE) {
            panel.setVisibility(View.GONE);
            return;
        }
        super.onBackPressed();
    }

    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // 只有面板高度真的变了才重设。键盘弹出/收起也会走到这里（configChanges 里声明了
        // keyboardHidden），无条件 setLayoutParams 会触发一次多余的 relayout，
        // 和 WebView 的 IME 处理撞在一起会导致键盘弹起后立刻收起。
        if (panel == null) return;
        int h = panelHeight();
        ViewGroup.LayoutParams p = panel.getLayoutParams();
        if (p.height != h) {
            p.height = h;
            panel.setLayoutParams(p);
        }
    }

    /** 键盘弹出时把面板收起来，给页面留出空间，也避免固定高度的面板把 WebView 挤成 0 */
    private void setupImeWatcher(final View root) {
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        Rect r = new Rect();
                        root.getWindowVisibleDisplayFrame(r);
                        int rootH = root.getRootView().getHeight();
                        boolean open = (rootH - r.height()) > rootH / 4;
                        if (open != imeOpen) {
                            imeOpen = open;
                            applyPanel();
                        } else if (open) {
                            // 键盘一直开着时，焦点可能从地址栏换到控制台输入框，
                            // 每帧校正一次底边（padding 没变就什么都不做）
                            fitAboveKeyboard();
                        }
                    }
                });
    }

    /**
     * 1.3.5 为了让页面输入框不被面板挤扁，加了「键盘一弹就收起面板」。
     * 但控制台的输入框本身就在面板里，收起面板等于把要输入的那一行藏起来。
     * 现在按焦点来源分开处理：
     * - 焦点在面板里（控制台执行 JS）→ 面板保留，把内容顶到键盘上沿；
     * - 焦点在页面或地址栏 → 仍按原逻辑收起面板，给 WebView 让出空间。
     */
    private void applyPanel() {
        if (panel == null) return;
        boolean typingInPanel = imeOpen && panel.hasFocus();
        boolean show = panelOn && (!imeOpen || typingInPanel);
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
        fitAboveKeyboard();
    }

    /**
     * 键盘盖住多少就往上顶多少。
     * 窗口若已被 adjustResize 缩过，overlap 天然是 0，不会顶两次。
     */
    private void fitAboveKeyboard() {
        if (rootView == null) return;
        if (!imeOpen || panel == null || panel.getVisibility() != View.VISIBLE) {
            if (rootView.getPaddingBottom() != 0) rootView.setPadding(0, 0, 0, 0);
            return;
        }
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);
        int[] loc = new int[2];
        rootView.getLocationOnScreen(loc);
        int overlap = loc[1] + rootView.getHeight() - r.bottom;
        if (overlap < 0) overlap = 0;
        if (rootView.getPaddingBottom() != overlap) {
            rootView.setPadding(0, 0, 0, overlap);
        }
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
