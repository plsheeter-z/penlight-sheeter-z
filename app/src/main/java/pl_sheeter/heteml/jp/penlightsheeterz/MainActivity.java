package pl_sheeter.heteml.jp.penlightsheeterz;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {

    public static final String DATA_URI_PNG_HEADER = "data:image/png;base64,";
    public static final String LOG_DEBUG_HEADER = "Personal.out";
    public static final String PENLIGHT_SHEETER_TOP_URL = "https://pl-sheeter.heteml.net/";

    private WebView psWebView;
    private String dataUri = null;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        psWebView = (WebView) findViewById(R.id.webview_penlight_sheeter);
        WebSettings settings = psWebView.getSettings();

        // add Javascript Interface
        settings.setJavaScriptEnabled(true);
        psWebView.addJavascriptInterface(new PenlightJavascriptInterface(this), "Penlight");

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        psWebView.setWebChromeClient(new WebChromeClient());
        psWebView.setWebViewClient(new CustomWebViewClient());

//        psWebView.setDownloadListener(new DownloadListener() {
//            @Override
//            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
//                if (url.startsWith(DATA_URI_PNG_HEADER)) {
//                    Log.d(LOG_DEBUG_HEADER, "Start dllink from DownloadListener");
//                    onClickDllink(url);
//                } else {
//                    psWebView.loadUrl(url);
//                }
//            }
//        });

        // 画面回転対応
        if (savedInstanceState != null) {
            ((WebView) findViewById(R.id.webview_penlight_sheeter)).restoreState(savedInstanceState);
            return;
        }

        psWebView.loadUrl(PENLIGHT_SHEETER_TOP_URL);
    }

    private class PenlightJavascriptInterface{
        Context mContext;
        PenlightJavascriptInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void sendDataURI(String uri) {
            if (uri.startsWith(DATA_URI_PNG_HEADER)) {
                dataUri = uri;
                onClickDllink();
            } else {
                Toast.makeText(MainActivity.this, R.string.save_png_failure, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        ((WebView) findViewById(R.id.webview_penlight_sheeter)).saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(LOG_DEBUG_HEADER, psWebView.getUrl());

            if (psWebView.canGoBack()) {
                if (psWebView.getUrl().endsWith("help.php")) {
                    psWebView.goBack();
                } else if (psWebView.getUrl().indexOf("edit.php") != -1) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.dialog_title_stop_drawing_canvas);
                    builder.setMessage(R.string.dialog_message_stop_drawing_canvas);
                    builder.setNegativeButton(R.string.dialog_button_cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            });
                    builder.setPositiveButton(R.string.dialog_button_stop_drawing_canvas,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    psWebView.goBack();
                                }
                            });
                    builder.setCancelable(true);
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                }
            } else {
                finish();
                return true;
            }
        }
        return false;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permisions, int[] grantResults) {
        if (1 == requestCode) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveSmartPhone();
            } else {
                Toast.makeText(MainActivity.this, R.string.reject_grant_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onClickDllink() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(LOG_DEBUG_HEADER, "Over API Level 23, not grant WRITE_EXTERNAL_STRAGE Permission to save smart phone");
                requestPermissions(new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        1);
            } else {
                Log.d(LOG_DEBUG_HEADER, "Over API Level 23, already granted WRITE_EXTERNAL_STRAGE Permission to save smart phone");
                saveSmartPhone();
            }
        } else {
            Log.d(LOG_DEBUG_HEADER, "Under API Level 23, save smart phone");
            saveSmartPhone();
        }
    }

    private void saveSmartPhone() {
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage("保存しています");
        progressDialog.setCancelable(false);
        progressDialog.show();

        final Handler handler = new Handler();

        new Thread(new Runnable() {

            @Override
            public void run() {
                String absolutePath = savePNG(dataUri);

                if (!absolutePath.isEmpty()) {
                    addGallery(absolutePath);
                }

                progressDialog.dismiss();
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, R.string.save_png_successful, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private String savePNG(final String base64Character) {
        String dirPath = Environment.getExternalStorageDirectory().getPath() + "/penlightsheeterz/";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        File pngPath = new File(dir.getAbsolutePath() + "/" + "plsz_" + sdf.format(date) + ".png");

        String data = base64Character.replaceFirst("data:image/png;base64,", "");
        byte[] bytes = Base64.decode(data, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(pngPath);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (IOException ioe) {

        } finally {
            try {
                fos.close();
            } catch (IOException e) {

            }
        }

        return pngPath.getAbsolutePath();
    }

    private void addGallery(String absolutePath) {
        File file = new File(absolutePath);

        ContentValues values = new ContentValues();
        ContentResolver resolver = getContentResolver();

        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.SIZE, file.length());
        values.put(MediaStore.Images.Media.TITLE, file.getName());
        values.put(MediaStore.Images.Media.DATA, file.getPath());

        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }
}