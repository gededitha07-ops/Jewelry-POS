
package com.jewelrypos.app;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PDFPrinter")
public class PDFPrinterPlugin extends Plugin {

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
                        PrintManager printManager =
                            (PrintManager) context.getSystemService(
                                Context.PRINT_SERVICE
                            );

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
                call.reject("Unable to open PDF printing: " + e.getMessage());
            }
        });
    }
}
