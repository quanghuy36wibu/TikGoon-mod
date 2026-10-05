package dev.tiktokrootmod;

import android.telephony.TelephonyManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Telephony signals used by TikTok's region selection, scoped to its main process. */
final class RegionHooks {
    private RegionHooks() {}

    static void install() {
        override("getNetworkType", 13); // LTE
        override("getDataNetworkType", 13);
        override("getVoiceNetworkType", 13);
        override("hasIccCard", true);
        override("getSimState", TelephonyManager.SIM_STATE_READY);
        override("getPhoneType", TelephonyManager.PHONE_TYPE_GSM);
        override("getSimCountryIso", Config.REGION_ISO);
        override("getNetworkCountryIso", Config.REGION_ISO);
        override("getSimOperator", Config.REGION_OPERATOR);
        override("getNetworkOperator", Config.REGION_OPERATOR);
        override("getSimOperatorName", Config.REGION_OPERATOR_NAME);
        override("getNetworkOperatorName", Config.REGION_OPERATOR_NAME);
    }

    private static void override(String name, Object value) {
        try {
            XposedBridge.hookAllMethods(TelephonyManager.class, name, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(value);
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: region hook skipped " + name + ": " + error);
        }
    }
}
