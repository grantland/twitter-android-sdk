package me.grantland.twitter;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;

/**
 * @author Grantland Chew
 */
public class Twitter {
    public static final String TAG = "me.grantland.twitter";
    public static final boolean DEBUG = false;

    public static final String REQUEST_TOKEN = "https://twitter.com/oauth/request_token";
    public static final String ACCESS_TOKEN = "https://twitter.com/oauth/access_token";
    public static final String AUTHORIZE = "https://twitter.com/oauth/authorize";
    public static final String DENIED = "denied";
    public static final String CALLBACK_SCHEME = "gc";
    public static final String CALLBACK_URI = CALLBACK_SCHEME + "://twitt";

    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_CONSUMER = "consumer";
//    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_FORCE_LOGIN = "force_login";
    public static final String EXTRA_SCREEN_NAME = "screen_name";
    public static final String EXTRA_ACCESS_KEY = "access_key";
    public static final String EXTRA_ACCESS_SECRET = "access_secret";

    // Used as default activityCode by authorize(). See authorize() below.
    private static final int DEFAULT_AUTH_ACTIVITY_CODE = 12345;

    private OAuthConsumer mConsumer = null;
//    private OAuthProvider mProvider = null;

    private int mRequestCode = DEFAULT_AUTH_ACTIVITY_CODE;
    private DialogListener mListener = null;
    private TwitterDialog mDialog = null;

    public Twitter(String consumerKey, String consumerSecret) {
        if (consumerKey == null || consumerSecret == null) {
            throw new IllegalArgumentException(
                    "You must specify your consumer key and secret when instantiating " +
                    "a Twitter object. See README for details.");
        }
        mConsumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
    }

    public boolean authorize(Activity activity, final DialogListener listener) {
        return authorize(activity, false, "", true, listener);
    }

    public boolean authorize(Activity activity, boolean forceLogin, String screenName, boolean dialog, final DialogListener listener) {

        if (!dialog) {
            mListener = listener;
            Intent intent = new Intent(activity, TwitterActivity.class);
            intent.putExtra(EXTRA_CONSUMER, mConsumer);
            intent.putExtra(EXTRA_FORCE_LOGIN, forceLogin);
            intent.putExtra(EXTRA_SCREEN_NAME, screenName);
            activity.startActivityForResult(intent, DEFAULT_AUTH_ACTIVITY_CODE);
        } else {
            if (mDialog != null && mDialog.isShowing()) {
                return false;
            }

            mDialog = new TwitterDialog(activity, mConsumer, forceLogin, screenName, new DialogListener() {
                @Override
                public void onComplete(String token, String secret) {
                    if (DEBUG) Log.d(TAG, "access_key: " + token);
                    if (DEBUG) Log.d(TAG, "access_secret: " + secret);

                    mConsumer.setTokenWithSecret(token, secret);

                    listener.onComplete(token, token);
                }

                @Override
                public void onError(TwitterError error) {
                    listener.onError(error);
                }

                @Override
                public void onCancel() {
                    listener.onCancel();
                }
            });
            mDialog.show();
        }

        return true;
    }

    public void authorizeCallback(int requestCode, int resultCode, Intent data) {
        if (mRequestCode != requestCode) {
            return;
        }

        String accessKey, accessSecret;

        if (Activity.RESULT_OK == resultCode) {
            String error = data.getStringExtra(EXTRA_ERROR);
            if (error != null) {
                mListener.onError(new TwitterError(error));
            } else {
                accessKey = data.getStringExtra(EXTRA_ACCESS_KEY);
                accessSecret = data.getStringExtra(EXTRA_ACCESS_SECRET);

                if (DEBUG) Log.d(TAG, "access_key: " + accessKey);
                if (DEBUG) Log.d(TAG, "access_secret: " + accessSecret);

                mConsumer.setTokenWithSecret(accessKey, accessSecret);

                if (mListener != null) {
                    mListener.onComplete(accessKey, accessSecret);
                    return;
                }
            }
        } else if (Activity.RESULT_CANCELED == resultCode) {
            if (mListener != null) {
                mListener.onCancel();
            }
        }
    }

    //==============================================================================================
    // Properties
    //==============================================================================================

    /**
     * @return boolean - whether this object has an non-expired session token
     */
    public boolean isSessionValid() {
        return mConsumer != null && (getAccessToken() != null && getAccessTokenSecret() != null);
    }

    public String getConsumerKey() {
        return mConsumer.getConsumerKey();
    }

    public String getConsumerSecret() {
        return mConsumer.getConsumerSecret();
    }

    public void setConsumerKeyAndSecret(String consumerKey, String consumerSecret) {
        mConsumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
    }

    public String getAccessToken() {
        return mConsumer.getToken();
    }

    public String getAccessTokenSecret() {
        return mConsumer.getTokenSecret();
    }

    public void setTokenWithSecret(String token, String secret) {
        mConsumer.setTokenWithSecret(token, secret);
    }


    /**
     * Callback interface for dialog requests.
     */
    public static interface DialogListener {

        /**
         * Called when a dialog completes.
         *
         * Executed by the thread that initiated the dialog.
         *
         * @param values
         *            Key-value string pairs extracted from the response.
         */
        public void onComplete(String accessKey, String accessSecret);

        /**
         * Called when a dialog has an error.
         *
         * Executed by the thread that initiated the dialog.
         */
        public void onError(TwitterError error);

        /**
         * Called when a dialog is canceled by the user.
         *
         * Executed by the thread that initiated the dialog.
         */
        public void onCancel();

    }
}
