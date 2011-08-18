package me.grantland.twitter;

import me.grantland.twitter.Twitter.DialogListener;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

	private static final int PADDING = 10;
	private static final int BORDER_ALPHA = 126;
	private static final int BORDER_RADIUS = 10;

    private final DialogListener mListener;

    private String mUrl;

	private ProgressDialog mSpinner;
	private FrameLayout mFrame,
					    mContent;
	private WebView mWebView;

	private OAuthConsumer mConsumer;
	private OAuthProvider mProvider;

    public TwitterDialog(Context context, OAuthConsumer consumer, DialogListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        mConsumer = consumer;
        mListener = listener;
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

        mContent = new FrameLayout(getContext());
        mContent.setPadding(PADDING, PADDING, PADDING, PADDING);
        mFrame.addView(mContent);

        PaintDrawable background = new PaintDrawable(Color.BLACK);
        background.setAlpha(BORDER_ALPHA);
        background.setCornerRadius(BORDER_RADIUS);
        mContent.setBackgroundDrawable(background);

        mProvider = new CommonsHttpOAuthProvider(
                Twitter.REQUEST_TOKEN,
                Twitter.ACCESS_TOKEN,
                Twitter.AUTHORIZE);
        mProvider.setOAuth10a(true);

        try {
            mUrl = mProvider.retrieveRequestToken(mConsumer, Twitter.CALLBACK_URI);
            if (DEBUG) Log.d(TAG, mUrl);
        } catch (OAuthMessageSignerException e) {
            e.printStackTrace();
        } catch (OAuthNotAuthorizedException e) {
            e.printStackTrace();
        } catch (OAuthExpectationFailedException e) {
            e.printStackTrace();
        } catch (OAuthCommunicationException e) {
            e.printStackTrace();
        }

        setUpWebView();

        setOnCancelListener(new OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) {
                mListener.onCancel();
            }
        });
	}

	@Override
	public void dismiss() {
	    super.dismiss();
	    mWebView.stopLoading();
	}

	@Override
	public void cancel() {
	    super.cancel();
	    mWebView.stopLoading();
	}

	private void setUpWebView() {
        mWebView = new WebView(getContext());
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setSaveFormData(false);
        mWebView.getSettings().setSavePassword(false);
        mWebView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        mWebView.setWebViewClient(new TwitterWebViewClient());
        mWebView.loadUrl(mUrl);
        mContent.addView(mWebView);
	}

	private void retrieveAccessToken(Uri uri) {
		if (DEBUG) Log.d(TAG, uri.toString());
		String oauth_token = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
		String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
		if (DEBUG) Log.d(TAG, oauth_token);
		if (DEBUG) Log.d(TAG, verifier);
		try {
		    String accessKey, accessSecret;
			mConsumer.setTokenWithSecret(oauth_token, mConsumer.getConsumerSecret());

			mProvider.retrieveAccessToken(mConsumer, verifier);
			accessKey = mConsumer.getToken();
			accessSecret = mConsumer.getTokenSecret();

			if (DEBUG) Log.d(TAG, "access_key: " + accessKey);
			if (DEBUG) Log.d(TAG, "access_secret: " + accessSecret);

			mListener.onComplete(accessKey, accessSecret);
		} catch (OAuthMessageSignerException e) {
			e.printStackTrace();
		} catch (OAuthNotAuthorizedException e) {
			e.printStackTrace();
		} catch (OAuthExpectationFailedException e) {
			e.printStackTrace();
		} catch (OAuthCommunicationException e) {
			e.printStackTrace();
		}

		dismiss();
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
            if (mFrame.getParent() == null) {
                setContentView(mFrame);
                mWebView.requestFocus();
            }
        }

        @Override public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            mListener.onError(description, errorCode, failingUrl);
            dismiss();
        }
    };
}
