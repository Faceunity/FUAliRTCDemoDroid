package com.aliyun.rtcdemo;

import android.util.Log;

import com.alivc.rtc.AliRtcAuthInfo;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author benyq
 * @time 2020/10/23
 * @e-mail 1520063035@qq.com
 * @note
 */
public class UserInfo {

    private static final String APP_ID = ;
    private static final String APP_KEY = ;
    private static final String[] glsb = new String[]{""};

    public static void injectUserInfo(String roomId, String userId, AliRtcAuthInfo userInfo) {
        long timestamp = System.currentTimeMillis() / 1000 + 10800;
        SimpleDateFormat format = new SimpleDateFormat("MM-dd", Locale.getDefault());
        String dateStrDir = format.format(new Date(System.currentTimeMillis()));

        if ("test".equals(userId)) {
            //test
            String nonce = "" + dateStrDir;
            userInfo.setAppId(APP_ID);
            userInfo.setNonce(nonce);
            userInfo.setGslb(glsb);
            userInfo.setTimestamp(timestamp);
            userInfo.setToken(getToken(roomId, userId, nonce, timestamp));
            userInfo.setChannelId(roomId);
            userInfo.setUserId(userId);
        }else {
            //test2
            String nonce = "" + dateStrDir;
            userInfo.setAppId(APP_ID);
            userInfo.setNonce(nonce);
            userInfo.setGslb(glsb);
            userInfo.setTimestamp(timestamp);
            userInfo.setToken(getToken(roomId, userId, nonce, timestamp));
            userInfo.setChannelId(roomId);
            userInfo.setUserId(userId);
        }
    }


    // token = sha256(appId + appKey + channelId + userId + nonce + timestamp)
    public static String getToken(String roomId, String userId, String nonce, long timestamp) {
        String originKey = APP_ID + APP_KEY + roomId + userId + nonce + timestamp;
        return getSHA256(originKey);
    }


    /**
     * 利用java原生的类实现SHA256加密
     * @param str 加密后的报文
     * @return
     */
    public static String getSHA256(String str){
        MessageDigest messageDigest;
        String encodestr = "";
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(str.getBytes("UTF-8"));
            encodestr = byte2Hex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encodestr;
    }
    /**
     * 将byte转为16进制
     * @param bytes
     * @return
     */
    private static String byte2Hex(byte[] bytes){
        StringBuffer stringBuffer = new StringBuffer();
        String temp = null;
        for (int i=0;i<bytes.length;i++){
            temp = Integer.toHexString(bytes[i] & 0xFF);
            if (temp.length()==1){
                //1得到一位的进行补0操作
                stringBuffer.append("0");
            }
            stringBuffer.append(temp);
        }
        return stringBuffer.toString();
    }
}
