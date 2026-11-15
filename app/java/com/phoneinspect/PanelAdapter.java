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
    public static final int TAB_ELEM = 3;
    public static final int TAB_SRC = 4;

    private final Context ctx;
    private final Net.Store store = Net.Store.get();
    private int tab = TAB_NET;

    public PanelAdapter(Context c) {
        this.ctx = c;
    }

    public void setTab(int t) {
        tab = t;
        notifyDataSetChanged();
    }

    public int getCount() {
        if (tab == TAB_NET) return store.recordCount();
        if (tab == TAB_LOG) return store.logCount();
        return 0;
    }

    public Object getItem(int p) { return null; }

    public long getItemId(int p) { return p; }

    static class Holder {
        int type;
        TextView method;
        TextView path;
        TextView status;
        TextView sub;
        TextView text;
    }

    public View getView(int pos, View cv, ViewGroup parent) {
        Holder h;
        if (cv == null || !(cv.getTag() instanceof Holder) || ((Holder) cv.getTag()).type != tab) {
            h = new Holder();
            h.type = tab;
            cv = build(h);
            cv.setTag(h);
        } else {
            h = (Holder) cv.getTag();
        }

        if (tab == TAB_NET) {
            bindNet(h, pos);
        } else {
            bindLog(h, pos);
        }
        return cv;
    }

    private View build(Holder h) {
        if (tab == TAB_NET) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));

            LinearLayout top = new LinearLayout(ctx);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            h.method = text(10f, 0xFF8B929E, true);
            h.method.setWidth(dp(46));
            top.addView(h.method);

            h.path = text(12.5f, 0xFFE6E8EB, false);
            h.path.setSingleLine(true);
            h.path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            top.addView(h.path, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            h.status = text(10.5f, 0xFF8B929E, true);
            h.status.setPadding(dp(8), 0, 0, 0);
            top.addView(h.status);

            row.addView(top);

            h.sub = text(10.5f, 0xFF6E7681, false);
            h.sub.setSingleLine(true);
            h.sub.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(h.sub);

            return row;
        }

        h.text = text(11.5f, 0xFFB4BAC4, true);
        h.text.setPadding(dp(12), dp(6), dp(12), dp(6));
        h.text.setTextIsSelectable(true);
        return h.text;
    }

    private void bindNet(Holder h, int pos) {
        Net.Record r;
        synchronized (store.records) {
            if (pos >= store.records.size()) return;
            r = store.records.get(pos);
        }
        h.method.setText(r.method);
        h.path.setText(pathOf(r.url));
        h.status.setText(statusText(r.status));
        h.status.setTextColor(statusColor(r.status));
        h.sub.setText(subOf(r));
    }

    private void bindLog(Holder h, int pos) {
        Net.LogItem l;
        synchronized (store.logs) {
            if (pos >= store.logs.size()) return;
            l = store.logs.get(pos);
        }
        h.text.setText("[" + l.level + "] " + l.msg);
        h.text.setTextColor(logColor(l.level));
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
        if ("eval".equals(level)) return 0xFFB8A1FF;
        return 0xFFB4BAC4;
    }

    private String subOf(Net.Record r) {
        StringBuilder sb = new StringBuilder();
        if (r.mainFrame) sb.append("主文档");

        if (r.status < 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("未拦截");
            return sb.toString();
        }
        if (sb.length() > 0) sb.append(" · ");

        if (r.note != null && r.note.length() > 0) {
            sb.append(r.note);
        } else {
            sb.append(r.cost).append("ms").append(" · ").append(size(r.size));
        }
        return sb.toString();
    }

    private String size(long b) {
        if (b < 1024) return b + "B";
        if (b < 1024 * 1024) return (b / 1024) + "KB";
        return String.format(java.util.Locale.US, "%.1fMB", b / 1048576f);
    }

    private TextView text(float sp, int color, boolean mono) {
        TextView t = new TextView(ctx);
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
