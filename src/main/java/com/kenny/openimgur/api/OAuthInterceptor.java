package com.kenny.openimgur.api;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.kenny.openimgur.api.responses.OAuthResponse;
import com.kenny.openimgur.classes.ImgurUser;
import com.kenny.openimgur.classes.OpengurApp;
import com.kenny.openimgur.util.LogUtil;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.HttpURLConnection;

import retrofit.RetrofitError;

/**
 * Created by kcampagna on 7/15/15.
 */
public class OAuthInterceptor implements Interceptor {
    private static final String TAG = OAuthInterceptor.class.getSimpleName();

    private static final Object sLock = new Object();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED || response.code() == HttpURLConnection.HTTP_FORBIDDEN) {
            OpengurApp app = OpengurApp.getInstance();
            ImgurUser user = app.getUser();

            if (user != null) {
                LogUtil.v(TAG, "Token is no longer valid");
                String token = user.getAccessToken();

                synchronized (sLock) {
                    String currentToken = user.getAccessToken();

                    // Check if our current token has been updated, if it hasn't fetch a new one.
                    if (!TextUtils.isEmpty(currentToken) && currentToken.equals(token)) {
                        refreshToken(app);
                    }
                }

                if (!TextUtils.isEmpty(user.getAccessToken())) {
                    Request newRequest = request.newBuilder()
                            .removeHeader(ApiClient.AUTHORIZATION_HEADER)
                            .addHeader(ApiClient.AUTHORIZATION_HEADER, "Bearer " + user.getAccessToken())
                            .build();

                    return chain.proceed(newRequest);
                }
            } else {
                LogUtil.w(TAG, "Received unauthorized status from API but no user is present... wat?");
            }
        }

        return response;
    }

    @Nullable
    private String refreshToken(OpengurApp app) {
        try {
            OAuthResponse response = ApiClient.getService().refreshToken(ApiClient.CLIENT_ID, ApiClient.CLIENT_SECRET, app.getUser().getRefreshToken(), "refresh_token");

            if (!TextUtils.isEmpty(response.access_token) && !TextUtils.isEmpty(response.refresh_token)) {
                app.getUser().setTokens(response.access_token, response.refresh_token, response.expires_in);
                app.getSql().updateUserTokens(response.access_token, response.refresh_token, response.expires_in);
                return response.access_token;
            }

            app.onLogout();
            return null;
        } catch (RetrofitError error) {
            LogUtil.e(TAG, "Error while refreshing token, logging out user", error);
            app.onLogout();
        }

        return null;
    }
}
