package com.phoneinspect;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PanelAdapter extends BaseAdapter {

    public static final int TAB_NET = 0;
    public static final int TAB_LOG = 1;
    public static final int TAB_INFO = 2;

    private final Context ctx;
    private final Net.Store store = Net.Store.get();
    private int tab = TAB_NET;
    private String info = "";

    public PanelAdapter(Context c) {
        this.ctx = c;
    }

    public void setTab(int t) {
        tab = t;
        notifyDataSetChanged();
    }

    public void setInfo(String s) {
        info = s;
        notifyDataSetChanged();
    }

    public int getCount() {
        if (tab == TAB_NET) return store.recordCount();
        if (tab == TAB_LOG) return store.logCount();
        return 1;
    }

    public Object getItem(int p) { return null; }

    public long getItemId(int p) { return p; }

    public View getView(int pos, View cv, ViewGroup parent) {
        if (tab == TAB_NET) return netRow(pos);
        if (tab == TAB_LOG) return logRow(pos);
        return infoRow();
    }

    private View netRow(int pos) {
        Net.Record r;
        synchronized (store.records) {
            if (pos >= store.records.size()) return new View(ctx);
            r = store.records.get(pos);
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView m = text(r.method, 10f, 0xFF8B929E, true);
        m.setWidth(dp(46));
        top.addView(m);

        TextView u = text(pathOf(r.url), 12.5f, 0xFFE6E8EB, false);
        u.setSingleLine(true);
        u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        top.addView(u, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView s = text(statusText(r.status), 10.5f, statusColor(r.status), true);
        s.setPadding(dp(8), 0, 0, 0);
        top.addView(s);

        row.addView(top);

        String sub = subOf(r);
        if (sub.length() > 0) {
            TextView d = text(sub, 10.5f, 0xFF6E7681, false);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(d);
        }
        return row;
    }

    private View logRow(int pos) {
        Net.LogItem l;
        synchronized (store.logs) {
            if (pos >= store.logs.size()) return new View(ctx);
            l = store.logs.get(pos);
        }
        TextView t = text("[" + l.level + "] " + l.msg, 11.5f, logColor(l.level), true);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setTextIsSelectable(true);
        return t;
    }

    private View infoRow() {
        TextView t = text(info, 11.5f, 0xFFB4BAC4, false);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private String pathOf(String url) {
        if (url == null) return "";
        try {
            int i = url.indexOf("://");
            if (i < 0) return url;
            int j = url.indexOf('/', i + 3);
            if (j < 0) return url.substring(i + 3);
            String host = url.substring(i + 3, j);
            String path = url.substring(j);
            if (path.length() > 60) path = path.substring(0, 60) + "…";
            return host + path;
        } catch (Throwable t) {
            return url;
        }
    }

    private String statusText(int s) {
        if (s < 0) return "ERR";
        return String.valueOf(s);
    }

    private int statusColor(int s) {
        if (s < 0) return 0xFFFF6B6B;
        if (s < 300) return 0xFF3FD07A;
        if (s < 400) return 0xFFFFB454;
        return 0xFFFF6B6B;
    }

    private int logColor(String level) {
        if ("error".equals(level)) return 0xFFFF6B6B;
        if ("warn".equals(level)) return 0xFFFFB454;
        if ("info".equals(level)) return 0xFF7FB0FF;
        return 0xFFB4BAC4;
    }

    private String subOf(Net.Record r) {
        StringBuilder sb = new StringBuilder();
        if (r.mainFrame) sb.append("主文档");
        if (r.status < 0) {
            return sb.length() > 0 ? sb.append(" · 未拦截").toString() : "未拦截";
        }
        if (sb.length() > 0) sb.append(" · ");
        sb.append(r.cost).append("ms");
        sb.append(" · ").append(size(r.size));
        return sb.toString();
    }

    private String size(long b) {
        if (b < 1024) return b + "B";
        if (b < 1024 * 1024) return (b / 1024) + "KB";
        return String.format(java.util.Locale.US, "%.1fMB", b / 1048576f);
    }

    private TextView text(String s, float sp, int color, boolean mono) {
        TextView t = new TextView(ctx);
        t.setText(s == null ? "" : s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (mono) t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private int dp(int v) {
        return dp(ctx, v);
    }
}
