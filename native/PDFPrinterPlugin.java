package com.jewelrypos.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResult;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.getcapacitor.Plugin;
import com.getcapacitor.ActivityResult;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.ActivityCallback;

@CapacitorPlugin(name = "PDFPrinter")
public class PDFPrinterPlugin extends Plugin {

    // ==============================
    // SAVE REPORT AS PDF
    // ==============================

    @PluginMethod
    public void savePDF(PluginCall call) {

        String html = call.getString("html");

        if (html == null || html.trim().isEmpty()) {
            call.reject("Report content is empty.");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                Context context = getContext();

                WebView webView = new WebView(context);
                webView.getSettings().setJavaScriptEnabled(false);

                webView.setWebViewClient(new WebViewClient() {

                    @Override
                    public void onPageFinished(WebView view, String url) {

                        try {
                            PrintManager printManager =
                                (PrintManager) context.getSystemService(
                                    Context.PRINT_SERVICE
                                );

                            if (printManager == null) {
                                call.reject("Android print service unavailable.");
                                view.destroy();
                                return;
                            }

                            android.print.PrintDocumentAdapter adapter =
                                view.createPrintDocumentAdapter(
                                    "Jewelry-POS-Sales-Report"
                                );

                            printManager.print(
                                "Jewelry POS Sales Report",
                                adapter,
                                new PrintAttributes.Builder()
                                    .setMediaSize(
                                        PrintAttributes.MediaSize.ISO_A4
                                    )
                                    .setColorMode(
                                        PrintAttributes.COLOR_MODE_COLOR
                                    )
                                    .build()
                            );

                            JSObject result = new JSObject();
                            result.put("opened", true);
                            call.resolve(result);

                        } catch (Exception e) {
                            call.reject(
                                "PDF printing failed: " + e.getMessage()
                            );
                            view.destroy();
                        }
                    }
                });

                webView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null
                );

            } catch (Exception e) {
                call.reject(
                    "Unable to open PDF printing: " + e.getMessage()
                );
            }
        });
    }


    // ==============================
    // SAVE BACKUP FILE
    // ==============================

    @PluginMethod
    public void saveBackup(PluginCall call) {

        String content = call.getString("content");
        String fileName = call.getString("fileName");

        if (content == null || content.isEmpty()) {
            call.reject("Backup content is empty.");
            return;
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "Jewelry-POS-Backup.json";
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(
            call,
            intent,
            "saveBackupResult"
        );
    }


    @ActivityCallback
    private void saveBackupResult(
        PluginCall call,
        ActivityResult result
    ) {

        if (call == null) {
            return;
        }

        if (result.getResultCode() != Activity.RESULT_OK) {
            call.reject("Backup saving was cancelled.");
            return;
        }

        Intent data = result.getData();

        if (data == null || data.getData() == null) {
            call.reject("No storage location was selected.");
            return;
        }

        Uri uri = data.getData();

        String content = call.getString("content");

        if (content == null || content.isEmpty()) {
            call.reject("Backup content is unavailable.");
            return;
        }

        try {
            OutputStream output = getContext()
                .getContentResolver()
                .openOutputStream(uri, "w");

            if (output == null) {
                call.reject("Unable to open selected storage location.");
                return;
            }

            try (OutputStream stream = output) {
                stream.write(
                    content.getBytes(StandardCharsets.UTF_8)
                );
                stream.flush();
            }

            JSObject response = new JSObject();
            response.put("saved", true);
            response.put("uri", uri.toString());

            call.resolve(response);

        } catch (Exception e) {
            call.reject(
                "Unable to save backup: " + e.getMessage()
            );
        }
    }
}

