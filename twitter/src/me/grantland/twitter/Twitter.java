package me.grantland.twitter;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import android.content.Context;

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

    private OAuthConsumer mConsumer = null;
    private TwitterDialog mDialog = null;

    public Twitter(String accessKey, String accessSecret) {
        if (accessKey == null || accessSecret == null) {
            throw new IllegalArgumentException(
                    "You must specify your access key and secret when instantiating " +
                    "a Twitter object. See README for details.");
        }
        mConsumer = new CommonsHttpOAuthConsumer(accessKey, accessSecret);
    }

    public boolean authorize(Context context, final DialogListener listener) {
        if (mDialog != null && mDialog.isShowing())
            return false;

        mDialog = new TwitterDialog(context, mConsumer, new DialogListener() {
            @Override public void onComplete(String token, String secret) {
                mConsumer.setTokenWithSecret(token, secret);

                listener.onComplete(token, token);
            }

            @Override public void onError(String description, int errorCode, String failingUrl) {
                listener.onError(description, errorCode, failingUrl);
            }

            @Override public void onCancel() {
                listener.onCancel();
            }
        });
        mDialog.show();
        return true;
    }

    /**
     * @return boolean - whether this object has an non-expired session token
     */
    public boolean isSessionValid() {
        return mConsumer != null && (getToken() != null && getTokenSecret() != null);
    }

//==================================================================================================
// Getters and Setters
//==================================================================================================

    public String getToken() {
        return mConsumer.getToken();
    }

    public String getTokenSecret() {
        return mConsumer.getTokenSecret();
    }

    public void setTokenWithSecret(String token, String secret) {
        mConsumer.setTokenWithSecret(token, secret);
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

//==================================================================================================
// Internal Interfaces
//==================================================================================================

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
        public void onError(String description, int errorCode, String failingUrl);

        /**
         * Called when a dialog is canceled by the user.
         *
         * Executed by the thread that initiated the dialog.
         */
        public void onCancel();

    }
}
