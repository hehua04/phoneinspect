package com.phoneinspect;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 给 ACTION_SEND 提供 content:// URI 的最小只读 Provider。
 *
 * 项目刻意不引 AndroidX，用不了 FileProvider；这里只暴露 cacheDir/exports 下的
 * 单个文件，文件名做严格校验，避免 ../ 之类的路径穿越。
 */
public class ExportProvider extends ContentProvider {

    public static final String AUTHORITY = "com.phoneinspect.export";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri.getLastPathSegment());
        if (f == null) throw new FileNotFoundException("bad export uri");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * 分享面板会 query DISPLAY_NAME / SIZE 来显示文件名和大小。
     * 只实现 openFile 的话，部分系统会显示成 "-1 B"。
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = fileFor(uri.getLastPathSegment());
        if (f == null) return null;

        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    /** 只允许 exports 目录下的单个文件，挡掉 ../ 和编码过的斜杠 */
    private File fileFor(String name) {
        if (name == null || name.length() == 0
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.contains("..")) {
            return null;
        }
        File dir = new File(getContext().getCacheDir(), "exports");
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) {
                return null;
            }
        } catch (java.io.IOException e) {
            return null;
        }
        return f.isFile() ? f : null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
