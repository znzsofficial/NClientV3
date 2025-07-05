package com.maxwai.nclientv3.components.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import com.google.android.material.color.DynamicColors;
import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.ScrapeTags;
import com.maxwai.nclientv3.async.database.DatabaseHelper;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagV2;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.network.NetworkUtil;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import kotlin.Suppress;


public class CrashApplication extends MultiDexApplication {
    private static final String SIGNATURE_GITHUB = "ce96fdbcc89991f083320140c148db5f";

    @Override
    public void onCreate() {
        super.onCreate();
        Global.initLanguage(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Global.initStorage(this);
        Database.setDatabase(new DatabaseHelper(getApplicationContext()).getWritableDatabase());
        String version = Global.getLastVersion(this), actualVersion = Global.getVersionName(this);
        SharedPreferences preferences = getSharedPreferences("Settings", 0);
        if (!actualVersion.equals(version))
            afterUpdateChecks(preferences, version);

        Global.initFromShared(this);
        NetworkUtil.initConnectivity(this);
        TagV2.initMinCount(this);
        TagV2.initSortByName(this);
        DownloadGalleryV2.loadDownloads(this);
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    @SuppressWarnings("deprecation")
    private boolean signatureCheck() {
        try {
            final String packageName = getPackageName();
            final PackageInfo packageInfo;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 新方案 (Android P / API 28 及以上)
                packageInfo = getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_SIGNING_CERTIFICATES);

                SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo == null) {
                    return false;
                }

                // hasMultipleSigners() 检查 APK 是否由多个签名者签名。
                // getApkContentsSigners() 获取用于签署 APK 的实际签名。
                // 如果你支持密钥轮换，可能需要检查 getSigningCertificateHistory()。
                // 对于简单的检查，getApkContentsSigners() 是最合适的。
                if (signingInfo.hasMultipleSigners()) {
                    for (Signature signature : signingInfo.getApkContentsSigners()) {
                        if (isSignatureValid(signature)) return true;
                    }
                } else {
                    for (Signature signature : signingInfo.getSigningCertificateHistory()) {
                        if (isSignatureValid(signature)) return true;
                    }
                }

            } else {
                // 旧方案 (Android P / API 28 以下)
                packageInfo = getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_SIGNATURES);

                if (packageInfo == null || packageInfo.signatures == null || packageInfo.signatures.length == 0) {
                    return false;
                }

                for (Signature signature : packageInfo.signatures) {
                    if (isSignatureValid(signature)) return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            // 包名未找到，这在正常情况下不应发生。
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 辅助方法，用于检查单个签名的哈希值。
     *
     * @param signature 签名对象
     * @return 如果签名匹配，则为 true
     */
    private boolean isSignatureValid(Signature signature) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(signature.toByteArray());
            String hash = new BigInteger(1, m.digest()).toString(16);
            LogUtility.d("Find signature: " + hash);
            // SIGNATURE_GITHUB 是你预定义的常量
            return SIGNATURE_GITHUB.equals(hash);
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法几乎总是可用的。
            e.printStackTrace();
            return false;
        }
    }

    private void afterUpdateChecks(SharedPreferences preferences, String oldVersion) {
        SharedPreferences.Editor editor = preferences.edit();
        removeOldUpdates();
        //update tags
        ScrapeTags.startWork(this);
        if ("0.0.0".equals(oldVersion))
            editor.putBoolean(getString(R.string.key_check_update), signatureCheck());
        editor.apply();
        Global.setLastVersion(this);
    }


    private void removeOldUpdates() {
        if (!Global.hasStoragePermission(this)) return;
        Global.recursiveDelete(Global.UPDATEFOLDER);
        Global.UPDATEFOLDER.mkdir();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder()
            .withBuildConfigClass(BuildConfig.class)
            .withReportContent(ReportField.PACKAGE_NAME,
                ReportField.BUILD_CONFIG,
                ReportField.APP_VERSION_CODE,
                ReportField.STACK_TRACE,
                ReportField.ANDROID_VERSION,
                ReportField.LOGCAT);

        ACRA.init(this, builder);
        ACRA.getErrorReporter().setEnabled(getSharedPreferences("Settings", 0).getBoolean(getString(R.string.key_send_report), false));
    }
}
