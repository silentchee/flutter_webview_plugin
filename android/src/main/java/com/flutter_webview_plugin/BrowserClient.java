package com.flutter_webview_plugin;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lejard_h on 20/12/2017.
 */

public class BrowserClient extends WebViewClient {
    private Pattern invalidUrlPattern = null;

    public BrowserClient() {
        this(null);
    }

    public BrowserClient(String invalidUrlRegex) {
        super();
        if (invalidUrlRegex != null) {
            invalidUrlPattern = Pattern.compile(invalidUrlRegex);
        }
    }

    public void updateInvalidUrlRegex(String invalidUrlRegex) {
        if (invalidUrlRegex != null) {
            invalidUrlPattern = Pattern.compile(invalidUrlRegex);
        } else {
            invalidUrlPattern = null;
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("type", "startLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);

        FlutterWebviewPlugin.channel.invokeMethod("onUrlChanged", data);

        data.put("type", "finishLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // returning true causes the current WebView to abort loading the URL,
        // while returning false causes the WebView to continue loading the URL as usual.
        String url = request.getUrl().toString();
        Uri uri = request.getUrl();

        if(openWithSingpassMobile(uri, view)){
            // Return true as openWithSingpassMobile method has handled the URL
            return true;
        }

        if (url.startsWith("intent://")) {
            try {
                Context context = view.getContext();
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);

                if (intent != null) {
                    view.stopLoading();

                    PackageManager packageManager = context.getPackageManager();
                    ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (info != null) {
                        context.startActivity(intent);
                    } else {
                        String fallbackUrl = intent.getStringExtra("browser_fallback_url");

                        // or call external broswer
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                    context.startActivity(browserIntent);
                    }

                    return true;
                }
            } catch (Exception e) {

            }
        }

        boolean isInvalid = checkInvalidUrl(url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("type", isInvalid ? "abortLoad" : "shouldStart");

        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
        return isInvalid;
    }

    private boolean openWithSingpassMobile(Uri uri, WebView view) {
        if (
            // (Scheme == https or intent)
                (
                        uri.getScheme().equalsIgnoreCase("intent") ||
                                uri.getScheme().equalsIgnoreCase("https")
                ) &&
                        // AND
                        // (Host == singpassmobile.sg or www.singpassmobile.sg AND path == qrlogin)
            (
                    (
                            uri.getHost().equalsIgnoreCase("singpassmobile.sg") ||
                                    uri.getHost().equalsIgnoreCase("www.singpassmobile.sg")
                    ) &&
                            uri.getPath().contains("qrlogin")
            )
) {
            Context context = view.getContext();
            PackageManager packageManager = context.getPackageManager();
            // Singpass Mobile Chrome intent scheme
            if (uri.getScheme().equalsIgnoreCase("intent")) {
                // Try to parse Singpass Mobile chrome intent URL to get an intent
                try {
                    Intent intent = Intent.parseUri(uri.toString(),
                            Intent.URI_INTENT_SCHEME);
                    // Try to find activity that can handle Singpass Mobile chrome intent
                    ResolveInfo info = packageManager.resolveActivity(intent, 0);
                    // Singpass Mobile activity found, launch Singpass Mobile
                    if (info != null) {
                        context.startActivity(intent);
                    }
                    // Singpass Mobile not installed on device, load fallback URL from chrome intent
                    else {
                        String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                        view.loadUrl(fallbackUrl);
                    }
                }
                // Uri parse exception, try to load Singpass Mobile landing page in webview
                catch (URISyntaxException e) {
                    view.loadUrl("https://singpassmobile.sg/qrlogin");
                }
            }
            // Https scheme, for app link
            else {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                // Check if there are activities that can handle
                if (packageManager.resolveActivity(intent, 0) != null) {
                    List< ResolveInfo > list = packageManager.queryIntentActivities(intent,
                            0);
                    boolean spmInstalled = false;
                    // Iterate handler activties and filter out SingPass Mobile
                    for (ResolveInfo info: list) {
                        if (info.activityInfo.packageName.equalsIgnoreCase("sg.ndi.sp")) {
                            spmInstalled = true;
                            break;
                        }
                    }
                    // If Singpass Mobile found, launch it
                    if (spmInstalled) {
                        intent.setPackage("sg.ndi.sp");
                        context.startActivity(intent);
                    }
                    // If Singpass Mobile not found, load Url in webview
                    else {
                        view.loadUrl("https://singpassmobile.sg/qrlogin");
                    }
                }
                // If no activities can handle URL, load it in webview
                else {
                    view.loadUrl("https://singpassmobile.sg/qrlogin");
                }
            }
            // Return true if this function handled the URL
            return true;
        }
// Return false if URL has not been handled
        return false;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // returning true causes the current WebView to abort loading the URL,
        // while returning false causes the WebView to continue loading the URL as usual.
        Uri uri = Uri.parse(url);
        if(openWithSingpassMobile(uri, view)){
            // Return true as openWithSingpassMobile method has handled the URL
            return true;
        }
        boolean isInvalid = checkInvalidUrl(url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("type", isInvalid ? "abortLoad" : "shouldStart");

        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
        return isInvalid;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        Map<String, Object> data = new HashMap<>();
        data.put("url", request.getUrl().toString());
        data.put("code", Integer.toString(errorResponse.getStatusCode()));
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        Map<String, Object> data = new HashMap<>();
        data.put("url", failingUrl);
        data.put("code", Integer.toString(errorCode));
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
    }

    private boolean checkInvalidUrl(String url) {
        if (invalidUrlPattern == null) {
            return false;
        } else {
            Matcher matcher = invalidUrlPattern.matcher(url);
            return matcher.lookingAt();
        }
    }
}