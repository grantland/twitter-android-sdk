package me.grantland.twitter;

import me.grantland.twitter.Twitter.DialogListener;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthException;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * @author Grantland Chew
 */
public class TwitterDialog extends Dialog {
    private static final String TAG = Twitter.TAG;
	private static final boolean DEBUG = Twitter.DEBUG;

    private static final int ERROR = -1;
    private static final int RETRIEVE_REQUEST_TOKEN = 1;
    private static final int RETRIEVE_ACCESS_TOKEN = 2;

    private static final String KEY_ERROR = "error";
    private static final String KEY_URL = "url";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_ACCESS_SECRET = "access_secret";

	private static final int PADDING = 10;
	private static final int BORDER_ALPHA = 126;
	private static final int BORDER_RADIUS = 10;

    private H mMainThreadHandler;

    private OAuthConsumer mConsumer;
    private OAuthProvider mProvider;

    private final DialogListener mListener;

    private String mUrl;

	private ProgressDialog mSpinner;
	private FrameLayout mFrame,
					    mContent;
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
                case ERROR: {
                    error((Throwable)data.getSerializable(KEY_ERROR));
                } break;
                case RETRIEVE_REQUEST_TOKEN: {
                    mWebView.loadUrl(data.getString(KEY_URL));
                } break;
                case RETRIEVE_ACCESS_TOKEN: {
                    complete(data.getString(KEY_ACCESS_TOKEN), data.getString(KEY_ACCESS_SECRET));
                } break;
                default: {

                }
            }
        }
    }

    public TwitterDialog(Context context, OAuthConsumer consumer, boolean forceLogin, String screenName, DialogListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mConsumer = consumer;
        mListener = listener;

        mMainThreadHandler = new H();

        mProvider = new CommonsHttpOAuthProvider(
                Twitter.REQUEST_TOKEN,
                Twitter.ACCESS_TOKEN,
                Twitter.AUTHORIZE);
        mProvider.setOAuth10a(true);

         // Retrieve request_token on background thread
        Thread thread = new Thread() {
            @Override
            public void run() {
                Message msg = new Message();
                Bundle bundle = new Bundle();
                try {
                    msg.what = RETRIEVE_REQUEST_TOKEN;
                    bundle.putString(KEY_URL, mProvider.retrieveRequestToken(mConsumer, Twitter.CALLBACK_URI));
                } catch (OAuthException e) {
                    msg.what = ERROR;
                    bundle.putSerializable(KEY_ERROR, e);
                }
                msg.setData(bundle);
                mMainThreadHandler.sendMessage(msg);
            }
        };
        thread.start();
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mSpinner = new ProgressDialog(getContext());
        mSpinner.setMessage("Loading...");
        mSpinner.setOnCancelListener(new OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) {
                cancel();
            }
        });

        mFrame = new FrameLayout(getContext());
        mFrame.setPadding(PADDING, PADDING, PADDING, PADDING);
        setContentView(mFrame);
        mFrame.setVisibility(View.INVISIBLE);

        mContent = new FrameLayout(getContext());
        mContent.setPadding(PADDING, PADDING, PADDING, PADDING);
        mFrame.addView(mContent);

        PaintDrawable background = new PaintDrawable(Color.BLACK);
        background.setAlpha(BORDER_ALPHA);
        background.setCornerRadius(BORDER_RADIUS);
        mContent.setBackgroundDrawable(background);

        setUpWebView();

        setOnCancelListener(new OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) {
                mListener.onCancel();
            }
        });
	}

	private void complete(String accessKey, String accessSecret) {
	    mListener.onComplete(accessKey, accessSecret);
	    dismiss();
	}

	private void error(Throwable error) {
	    mListener.onError(new TwitterError(error.getMessage()));
	    dismiss();
	}

	@Override
	public void dismiss() {
	    super.dismiss();
	    //mWebView.stopLoading();
	}

	@Override
	public void cancel() {
	    super.cancel();
	    //mWebView.stopLoading();
	}

	private void setUpWebView() {
	    LayoutInflater inflater = this.getLayoutInflater();
        mWebView = (WebView)inflater.inflate(R.layout.twitter_layout, null);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setSaveFormData(false);
        mWebView.getSettings().setSavePassword(false);
        mWebView.setWebViewClient(new TwitterWebViewClient());
        mWebView.loadUrl(mUrl);
        mContent.addView(mWebView);
	}

    private void retrieveAccessToken(final Uri uri) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                Message msg = new Message();
                Bundle bundle = new Bundle();
                try {
                    if (DEBUG) Log.d(TAG, uri.toString());
                    String oauth_token = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
                    String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
                    if (DEBUG) Log.d(TAG, oauth_token);
                    if (DEBUG) Log.d(TAG, verifier);

                    mConsumer.setTokenWithSecret(oauth_token, mConsumer.getConsumerSecret());
                    mProvider.retrieveAccessToken(mConsumer, verifier);

                    msg.what = RETRIEVE_ACCESS_TOKEN;
                    bundle.putString(KEY_ACCESS_TOKEN, mConsumer.getToken());
                    bundle.putString(KEY_ACCESS_SECRET, mConsumer.getTokenSecret());
                } catch (OAuthException e)  {
                    msg.what = ERROR;
                    bundle.putSerializable(KEY_ERROR, e);
                }
                msg.setData(bundle);
                mMainThreadHandler.sendMessage(msg);
            }
        };
        thread.start();
    }

	private class TwitterWebViewClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
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

        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (DEBUG) Log.d(TAG, "Webview loading URL: " + url);
            if (!mSpinner.isShowing()) {
                mSpinner.show();
            }
        }

        @Override public void onPageFinished(WebView view, String url) {
            mSpinner.dismiss();
            mFrame.setVisibility(View.VISIBLE);
        }

        @Override public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            mListener.onError(new TwitterError(description, errorCode, failingUrl));
            dismiss();
        }
    };
}
