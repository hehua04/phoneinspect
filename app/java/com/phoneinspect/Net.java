package com.phoneinspect;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class Net {

    public static final int MODE_INTERCEPT = 0;
    public static final int MODE_RECORD = 1;
    public static final int MODE_OFF = 2;

    public static final int PROTO_AUTO = 0;
    public static final int PROTO_H1 = 1;
    public static final int PROTO_H2 = 2;

    public static class Record {
        public String method = "";
        public String url = "";
        public String requestHeaders = "";
        public String responseHeaders = "";
        public String chain = "";
        public String bodyPreview = "";
        public String mime = "";
        public String protocol = "";
        public String finalUrl = "";
        public String note = "";
        public int status = -1;
        public long cost = 0;
        public long size = 0;
        public boolean mainFrame = false;
        public long time = System.currentTimeMillis();
    }

    public static class LogItem {
        public String level = "log";
        public String msg = "";
        public long time = System.currentTimeMillis();
    }

    public static class Store {

        private static Store INSTANCE;

        public static synchronized Store get() {
            if (INSTANCE == null) INSTANCE = new Store();
            return INSTANCE;
        }

        public final Handler main = new Handler(Looper.getMainLooper());
        public final List<Record> records = new ArrayList<Record>();
        public final List<LogItem> logs = new ArrayList<LogItem>();
        public Runnable netListener;
        public Runnable logListener;

        public void addRecord(final Record r) {
            synchronized (records) {
                records.add(0, r);
                while (records.size() > 400) records.remove(records.size() - 1);
            }
            main.post(new Runnable() {
                public void run() {
                    if (netListener != null) netListener.run();
                }
            });
        }

        public void addLog(final LogItem l) {
            synchronized (logs) {
                logs.add(0, l);
                while (logs.size() > 500) logs.remove(logs.size() - 1);
            }
            main.post(new Runnable() {
                public void run() {
                    if (logListener != null) logListener.run();
                }
            });
        }

        public void clearRecords() {
            synchronized (records) { records.clear(); }
        }

        /** 主文档的服务器原始字节解码后的文本，来自拦截层，不是 DOM 序列化 */
        public volatile String sourceHtml = "";
        public volatile String sourceUrl = "";
        /** 元素拾取器回传的格式化文本 */
        public volatile String elementInfo = "";
        /** document.documentElement.outerHTML 快照 */
        public volatile String domHtml = "";

        public void setSource(String url, String html) {
            sourceUrl = url == null ? "" : url;
            sourceHtml = html == null ? "" : html;
        }

        public void setDom(String html) {
            domHtml = html == null ? "" : html;
        }

        public void setElement(String text) {
            elementInfo = text == null ? "" : text;
        }

        public void clearLogs() {
            synchronized (logs) { logs.clear(); }
        }

        public int recordCount() {
            synchronized (records) { return records.size(); }
        }

        public int logCount() {
            synchronized (logs) { return logs.size(); }
        }
    }
}
