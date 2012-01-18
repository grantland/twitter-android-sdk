package me.grantland.twitter;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthException;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class TwitterActivity extends Activity {
    private static final String TAG = Twitter.TAG;
    private static final boolean DEBUG = Twitter.DEBUG;

    private static final int RETRIEVE_REQUEST_TOKEN = 1;
    private static final int RETRIEVE_ACCESS_TOKEN = 2;

    private static final String KEY_URL = "url";

    private H mMainThreadHandler;

    private OAuthConsumer mConsumer;
    private OAuthProvider mProvider;

    private ProgressDialog mSpinner;
    private WebView mWebView;

    /**
     * Handler to run shit on the UI thread
     *
     * @author Grantland Chew
     */
    private class H extends Handler {
        @Override
        public void handleMessage (Message msg) {
            Bundle data = msg.getData();

            switch (msg.what) {
                case RETRIEVE_REQUEST_TOKEN: {
                    mWebView.loadUrl(data.getString(KEY_URL));
                } break;
                case RETRIEVE_ACCESS_TOKEN: {

                } break;
                default: {

                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.twitter_layout);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mConsumer = (OAuthConsumer)extras.get(Twitter.EXTRA_CONSUMER);
        }

        mMainThreadHandler = new H();

        mProvider = new CommonsHttpOAuthProvider(
                Twitter.REQUEST_TOKEN,
                Twitter.ACCESS_TOKEN,
                Twitter.AUTHORIZE);
        mProvider.setOAuth10a(true);

        mSpinner = new ProgressDialog(this);
        mSpinner.setMessage(getResources().getString(R.string.loading_loading));
        mSpinner.setOnCancelListener(new OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) {
                cancel();
            }
        });

        mWebView = (WebView)findViewById(R.id.twitter_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setSaveFormData(false);
        mWebView.getSettings().setSavePassword(false);
        mWebView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        mWebView.setWebViewClient(new TwitterWebViewClient());

        // Retrieve request_token on background thread
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Message msg = new Message();
                    msg.what = RETRIEVE_REQUEST_TOKEN;

                    Bundle bundle = new Bundle();
                    bundle.putString(KEY_URL, mProvider.retrieveRequestToken(mConsumer, Twitter.CALLBACK_URI));
                    msg.setData(bundle);

                    if (DEBUG) Log.d(TAG, "url: " + bundle.getString(KEY_URL));
                    mMainThreadHandler.sendMessage(msg);
                } catch (OAuthException e) {
                    error(e);
                }
            }
        };
        thread.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            cancel();
            return true; // consume event
        }

        return false; // don't consume event
    }

    private void error(Throwable error) {
        Intent intent = this.getIntent();
        intent.putExtra(Twitter.EXTRA_ERROR, error.getMessage());
        this.setResult(RESULT_OK, intent);
        finish();
    }

    private void cancel() {
        Intent intent = this.getIntent();
        this.setResult(RESULT_CANCELED, intent);
        finish();
    }

    private void complete(String accessKey, String accessSecret) {
        Intent intent = this.getIntent();
        intent.putExtra(Twitter.EXTRA_ACCESS_KEY, accessKey);
        intent.putExtra(Twitter.EXTRA_ACCESS_SECRET, accessSecret);
        this.setResult(RESULT_OK, intent);
        finish();
    }

    private void retrieveAccessToken(final Uri uri) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    if (DEBUG) Log.d(TAG, uri.toString());
                    String oauth_token = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
                    String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
                    if (DEBUG) Log.d(TAG, oauth_token);
                    if (DEBUG) Log.d(TAG, verifier);

                    mConsumer.setTokenWithSecret(oauth_token, mConsumer.getConsumerSecret());
                    mProvider.retrieveAccessToken(mConsumer, verifier);

                    complete(mConsumer.getToken(), mConsumer.getTokenSecret());
                } catch (OAuthException e) {
                    error(e);
                }
            }
        };
        thread.start();
    }

    private class TwitterWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (DEBUG) Log.d(TAG, url);
            Uri uri = Uri.parse(url);
            if (uri != null && Twitter.CALLBACK_SCHEME.equals(uri.getScheme())) {
                String denied = uri.getQueryParameter(Twitter.DENIED);

                if (denied != null) {
                    cancel();
                } else {
                    retrieveAccessToken(uri);
                }

                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (DEBUG) Log.d(TAG, "Webview loading URL: " + url);
            if (!mSpinner.isShowing()) {
                mSpinner.show();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            mSpinner.dismiss();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            error(new TwitterError(description, errorCode, failingUrl));
        }
    };
}
