package com.phoneinspect;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class Detail {

    public static void show(final Activity a, final Net.Record r) {
        final String txt = build(r);

        TextView tv = new TextView(a);
        tv.setText(txt);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(0xFFE6E8EB);
        tv.setPadding(26, 26, 26, 26);
        tv.setTextIsSelectable(true);
        tv.setLineSpacing(6f, 1f);

        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(0xFF14161A);
        sv.addView(tv);
        int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.58f);
        sv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        new AlertDialog.Builder(a)
                .setTitle(clip(r.method + " " + r.url, 64))
                .setView(sv)
                .setPositiveButton("复制全部", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        copy(a, txt);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    public static void copy(Context c, String s) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("phoneinspect", s));
            Toast.makeText(c, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(c, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static String build(Net.Record r) {
        StringBuilder sb = new StringBuilder();

        sb.append("注意：以下内容含 Cookie 等敏感信息，复制后请勿粘贴到公开场合。\n\n");

        head(sb, "请求");
        sb.append(r.method).append(' ').append(r.url).append('\n');
        if (r.mainFrame) sb.append("(主文档请求)\n");
        if (r.finalUrl != null && r.finalUrl.length() > 0 && !r.finalUrl.equals(r.url)) {
            sb.append("最终地址: ").append(r.finalUrl).append('\n');
        }
        sb.append('\n');

        head(sb, "请求头");
        sb.append(nz(r.requestHeaders)).append("\n\n");

        head(sb, "响应");
        String proto = (r.protocol == null || r.protocol.length() == 0) ? "HTTP/1.1" : r.protocol;
        sb.append(proto).append(' ').append(r.status < 0 ? "ERR" : String.valueOf(r.status));
        if (r.cost > 0) sb.append("    ").append(r.cost).append("ms");
        if (r.size > 0) sb.append("    ").append(r.size).append(" bytes");
        sb.append('\n');
        if (r.note != null && r.note.length() > 0) {
            sb.append("说明: ").append(r.note).append('\n');
        }
        sb.append('\n');

        head(sb, "响应头");
        sb.append(nz(r.responseHeaders)).append("\n\n");

        if (r.chain != null && r.chain.length() > 0) {
            head(sb, "重定向链");
            sb.append(r.chain).append('\n');
        }

        if (r.bodyPreview != null && r.bodyPreview.length() > 0) {
            head(sb, "响应体预览");
            sb.append(r.bodyPreview).append('\n');
        }
        return sb.toString();
    }

    private static void head(StringBuilder sb, String t) {
        sb.append("── ").append(t).append(" ──\n");
    }

    private static String nz(String s) {
        return (s == null || s.length() == 0) ? "(空)" : s;
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
