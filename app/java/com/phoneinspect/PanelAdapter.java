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

import java.util.ArrayList;
import java.util.List;

/**
 * 列表数据层。
 *
 * 1. 每次刷新先取一份快照（reload），渲染和点击都对着同一份快照；
 * 2. 记录带稳定 id，点击时优先用「被点那一行自己携带的 id」（View tag）反查，
 *    即使快照刚换过，打开的也一定是用户看到的那条；
 * 3. 按 navId 插入分段行，换页后的记录不会和上一页混在一起；
 * 4. 状态按四态着色：已接管（绿）/ 未发送（灰）/ 已探测未接管（橙）/ 失败（红）。
 */
public class PanelAdapter extends BaseAdapter {

    public static final int TAB_NET = 0;
    public static final int TAB_LOG = 1;
    public static final int TAB_INFO = 2;
    public static final int TAB_ELEM = 3;
    public static final int TAB_SRC = 4;

    private final Context ctx;
    private final Net.Store store = Net.Store.get();
    private int tab = TAB_NET;

    /** 渲染用的行快照：分隔行 + 数据行混排，position 只对这份列表有效 */
    private final List<Row> netRows = new ArrayList<Row>();
    private final List<Row> logRows = new ArrayList<Row>();

    /** 过滤条件：关键字 + 快捷分类。只影响展示，不动数据 */
    private String filterText = "";
    private int filterNet = FILTER_ALL;
    private int filterLog = FILTER_ALL;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_FAIL = 1;   // 网络：失败 / 控制台：警告以上
    public static final int FILTER_MAIN = 2;   // 网络：主文档 / 控制台：只看错误

    public PanelAdapter(Context c) {
        this.ctx = c;
    }

    public void setTab(int t) {
        tab = t;
        notifyDataSetChanged();
    }

    public void setFilter(String q, int kind) {
        String t = q == null ? "" : q.trim().toLowerCase();
        if (t.equals(filterText) && kind == filterNet) return;
        filterText = t;
        filterNet = kind;
        notifyDataSetChanged();
    }

    public void setLogFilter(String q, int level) {
        String t = q == null ? "" : q.trim().toLowerCase();
        if (t.equals(filterText) && level == filterLog) return;
        filterText = t;
        filterLog = level;
        notifyDataSetChanged();
    }

    /** 当前展示的记录条数（不含分隔行），用来在 tab 上显示 "12/80" */
    public int visibleCount() {
        List<Row> rows = tab == TAB_NET ? netRows : logRows;
        int n = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type != Row.HEADER) n++;
        }
        return n;
    }

    private boolean match(Net.Record r) {
        if (filterNet == FILTER_FAIL && !isFail(r)) return false;
        if (filterNet == FILTER_MAIN && !r.mainFrame) return false;
        if (filterText.length() == 0) return true;
        return has(r.url) || has(r.finalUrl) || has(r.method) || has(r.note);
    }

    private boolean isFail(Net.Record r) {
        return r.kind == Net.Record.KIND_FAILED || r.status >= 400;
    }

    private boolean matchLog(Net.LogItem l) {
        if (filterLog == FILTER_FAIL
                && !("warn".equals(l.level) || "error".equals(l.level))) return false;
        if (filterLog == FILTER_MAIN && !"error".equals(l.level)) return false;
        return filterText.length() == 0 || (l.msg != null
                && l.msg.toLowerCase().contains(filterText));
    }

    private boolean has(String s) {
        return s != null && s.toLowerCase().contains(filterText);
    }

    /** 取快照：把「记录列表」翻译成「行列表」，中间插入导航分隔行 */
    public void reload() {
        if (tab == TAB_NET) {
            List<Net.Record> copy;
            synchronized (store.records) {
                copy = new ArrayList<Net.Record>(store.records);
            }
            netRows.clear();
            int lastNav = Integer.MIN_VALUE;
            int shown = 0;
            for (int i = 0; i < copy.size(); i++) {
                Net.Record r = copy.get(i);
                if (!match(r)) continue;
                if (r.navId != lastNav) {
                    netRows.add(header(r.navId, r.pageUrl));
                    lastNav = r.navId;
                }
                Row row = new Row();
                row.type = Row.RECORD;
                row.id = r.id;
                row.r = r;
                netRows.add(row);
                shown++;
            }
            if (shown == 0) netRows.add(hint(copy.size()));
        } else if (tab == TAB_LOG) {
            List<Net.LogItem> copy;
            synchronized (store.logs) {
                copy = new ArrayList<Net.LogItem>(store.logs);
            }
            logRows.clear();
            int lastNav = Integer.MIN_VALUE;
            int shown = 0;
            for (int i = 0; i < copy.size(); i++) {
                Net.LogItem l = copy.get(i);
                if (!matchLog(l)) continue;
                if (l.navId != lastNav) {
                    logRows.add(header(l.navId, l.pageUrl));
                    lastNav = l.navId;
                }
                Row row = new Row();
                row.type = Row.LOG;
                row.id = l.id;
                row.l = l;
                logRows.add(row);
                shown++;
            }
            if (shown == 0) logRows.add(hint(copy.size()));
        }
    }

    /** 统一入口：先重新取快照再通知，避免出现「行数和数据不同步」的那一帧 */
    public void notifyDataSetChanged() {
        reload();
        super.notifyDataSetChanged();
    }

    private Row hint(int total) {
        Row row = new Row();
        row.type = Row.HEADER;
        row.id = -1;
        row.title = total == 0
                ? "还没有记录。加载一个页面，或切到「仅记录」模式对照。"
                : "没有匹配的记录（共 " + total + " 条，试试清空关键字或换个分类）";
        return row;
    }

    private Row header(int navId, String pageUrl) {
        Row row = new Row();
        row.type = Row.HEADER;
        row.navId = navId;
        // 分隔行用负数 id，和记录的 id 不冲突（记录 id 从 1 开始）
        row.id = -1000000L * (navId + 1) - 1;
        String where = pageUrl == null ? "" : pageUrl;
        if (navId <= 0) {
            // 内置主页是 data: 页面，不经过拦截层，所以没有导航编号
            row.title = "未归属页面" + (where.length() > 0 ? "  " + where : "");
        } else {
            row.title = "#" + navId + "  " + (where.length() > 0 ? where : "(页面地址未知)");
        }
        return row;
    }

    public int getCount() {
        if (tab == TAB_NET) return netRows.size();
        if (tab == TAB_LOG) return logRows.size();
        return 0;
    }

    public Object getItem(int p) {
        if (tab == TAB_NET) return p >= 0 && p < netRows.size() ? netRows.get(p) : null;
        if (tab == TAB_LOG) return p >= 0 && p < logRows.size() ? logRows.get(p) : null;
        return null;
    }

    public long getItemId(int p) {
        Object o = getItem(p);
        return o instanceof Row ? ((Row) o).id : -1;
    }

    public boolean hasStableIds() {
        return true;
    }

    /**
     * 点击时反查：先用行的 tag 里的 id，找不到再退回 position。
     * 这条路径不依赖「position 与 store.records 的 index 一致」这个曾经不成立的假设。
     */
    public Net.Record recordFor(View row, int pos) {
        long id = 0;
        if (row != null && row.getTag() instanceof Holder) {
            id = ((Holder) row.getTag()).recId;
        }
        if (id > 0) {
            for (int i = 0; i < netRows.size(); i++) {
                Row r = netRows.get(i);
                if (r.type == Row.RECORD && r.id == id) return r.r;
            }
        }
        Object o = getItem(pos);
        return o instanceof Row && ((Row) o).r != null ? ((Row) o).r : null;
    }

    public static class Row {
        public static final int HEADER = 0;
        public static final int RECORD = 1;
        public static final int LOG = 2;

        public int type;
        public long id;
        public int navId;
        public String title;
        public Net.Record r;
        public Net.LogItem l;
    }

    static class Holder {
        int kind;
        long recId;
        TextView method;
        TextView path;
        TextView status;
        TextView sub;
        TextView text;
    }

    public View getView(int pos, View cv, ViewGroup parent) {
        Row row = (Row) getItem(pos);
        if (row == null) return cv == null ? new TextView(ctx) : cv;

        Holder h;
        if (cv == null || !(cv.getTag() instanceof Holder) || ((Holder) cv.getTag()).kind != row.type) {
            h = new Holder();
            h.kind = row.type;
            cv = build(h, row.type);
            cv.setTag(h);
        } else {
            h = (Holder) cv.getTag();
        }

        if (row.type == Row.HEADER) {
            h.text.setText(row.title);
        } else if (row.type == Row.RECORD) {
            bindNet(h, row.r);
        } else {
            bindLog(h, row.l);
        }
        return cv;
    }

    private View build(Holder h, int kind) {
        if (kind == Row.HEADER) {
            h.text = new TextView(ctx);
            h.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            h.text.setTextColor(0xFF7C8593);
            h.text.setBackgroundColor(0xFF1B1F26);
            h.text.setPadding(dp(12), dp(5), dp(12), dp(5));
            h.text.setSingleLine(true);
            h.text.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            return h.text;
        }

        if (kind == Row.RECORD) {
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
            h.status.setPadding(dp(8), 0, dp(4), 0);
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

    private void bindNet(Holder h, Net.Record r) {
        if (r == null) return;
        h.recId = r.id;
        h.method.setText(r.method);
        h.path.setText(pathOf(r.url));
        h.status.setText(statusText(r));
        h.status.setTextColor(statusColor(r));
        h.sub.setText(subOf(r));
    }

    private void bindLog(Holder h, Net.LogItem l) {
        if (l == null) return;
        h.recId = l.id;
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

    /**
     * 状态列。四态各自说自己的话：
     * 有码就显示码（橙色表示"拿到了但内容没回填"），没码就说清楚是没发还是失败。
     */
    private String statusText(Net.Record r) {
        if (r.kind == Net.Record.KIND_SKIPPED_BEFORE) return "跳过";
        if (r.kind == Net.Record.KIND_FAILED) return "失败";
        return r.status < 0 ? "—" : String.valueOf(r.status);
    }

    private int statusColor(Net.Record r) {
        switch (r.kind) {
            case Net.Record.KIND_SKIPPED_BEFORE:
                return 0xFF8B929E;
            case Net.Record.KIND_SKIPPED_AFTER:
                return 0xFFFFB454;
            case Net.Record.KIND_FAILED:
                return 0xFFFF6B6B;
            default:
                break;
        }
        if (r.status < 0) return 0xFFFF6B6B;
        if (r.status < 300) return 0xFF3FD07A;
        if (r.status < 400) return 0xFFFFB454;
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

        boolean skipped = r.kind != Net.Record.KIND_INTERCEPTED;
        String note = r.note == null ? "" : r.note;

        if (skipped) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(note.length() > 0 ? note : kindWord(r.kind));
            return sb.toString();
        }
        if (sb.length() > 0) sb.append(" · ");

        if (note.length() > 0) {
            sb.append(note);
        } else {
            sb.append(r.cost).append("ms").append(" · ").append(size(r.size));
        }
        return sb.toString();
    }

    private String kindWord(int kind) {
        switch (kind) {
            case Net.Record.KIND_SKIPPED_BEFORE: return "未发送请求";
            case Net.Record.KIND_SKIPPED_AFTER: return "已探测，内容未接管";
            case Net.Record.KIND_FAILED: return "请求失败";
            default: return "";
        }
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
