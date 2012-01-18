package me.grantland.twitter;

public class TwitterError extends Throwable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

//    private int mErrorCode;
//    private String mFailingUrl;

    public TwitterError(String message) {
        super(message);
    }

    public TwitterError(String message, int errorCode, String failingUrl) {
        super(message);
//        mErrorCode = errorCode;
//        mFailingUrl = failingUrl;
    }

//    public int getErrorCode() {
//        return mErrorCode;
//    }
}
