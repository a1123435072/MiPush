package top.trumeet.mipushframework.wizard.support;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.support.annotation.Nullable;

import com.xiaomi.mipush.sdk.PushMessageReceiver;
import com.xiaomi.push.service.XMPushService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.trumeet.mipushframework.Constants;

/**
 * Created by Trumeet on 2017/8/24.
 * A utils to check app support MiPush
 * @author Trumeet
 */

final class CheckSupportUtils {
    private Logger logger = LoggerFactory.getLogger("CheckSupportUtils");

    /**
     * Check package is support MiPush.
     * @param info Package info
     * @return This package support status. If not use MiPush, will returns null
     */
    @Nullable
    public static SupportStatus check (PackageInfo info, PackageManager manager) {
        return new CheckSupportUtils()
                .checkNonStatic(info, manager);
    }
    
    @Nullable
    private SupportStatus checkNonStatic (PackageInfo info, PackageManager manager) {
        String pkg = info.packageName;
        if (!hasMiPushService(info)) {
            logger.error("Pkg " + pkg + " not have push service!");
            return null;
        }
        boolean receiverSupport = customReceiverPass(info, manager);
        logger.debug("Pkg " + pkg + " support receiver: " + receiverSupport);
        if (receiverSupport) {
            return new SupportStatus(pkg, SupportStatus.Status.OK);
        } else {
            return new SupportStatus(pkg, SupportStatus.Status.FAIL_CAN_FIX);
        }
    }

    /**
     * Check package is support MiPush.
     * @param pkg Package Name
     * @param manager Package manager
     * @return This package support status. If not use MiPush, will returns null
     */
    @Nullable
    public static SupportStatus check (String pkg,
                                       PackageManager manager) {
        try {
            PackageInfo info = manager.getPackageInfo(pkg, PackageManager.GET_SERVICES |
                    PackageManager.GET_RECEIVERS | PackageManager.GET_DISABLED_COMPONENTS);
            return check(info, manager);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check this package has MiPushService
     * @see XMPushService
     * @param info Package Info
     * @return has push service
     */
    private boolean hasMiPushService (PackageInfo info) {
        if (info.services == null)
            return false;
        for (ServiceInfo serviceInfo : info.services) {
            logger.debug("Service name -> " + serviceInfo.name);
            if (XMPushService.class.getName().equals(serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check package's MiPush custom receiver (handle com.xiaomi.mipush.RECEIVE_MESSAGE action)
     * is enabled.
     * @see PushMessageReceiver
     * @see Constants#ACTION_RECEIVE_MESSAGE
     * @param info Package info
     * @return enable status. If this package not have custom receiver, will
     * still returns true.
     */
    private boolean customReceiverPass (PackageInfo info, PackageManager packageManager) {
        //if (info.receivers == null)
        //    return true;
        Intent intent = new Intent();
        intent.setPackage(info.packageName);
        //intent.setAction(Constants.ACTION_RECEIVE_MESSAGE);
        for (ResolveInfo resolveInfo : packageManager.queryBroadcastReceivers(intent,
                PackageManager.GET_RESOLVED_FILTER |
        PackageManager.GET_DISABLED_COMPONENTS)) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (!activityInfo.packageName.equals(info.packageName))
                continue;
            IntentFilter filter = resolveInfo.filter;
            if (filter != null && filter.hasAction(Constants.ACTION_RECEIVE_MESSAGE)) {
                boolean enabled = packageManager.getComponentEnabledSetting(new ComponentName(
                        activityInfo.packageName, activityInfo.name
                )) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                logger.info("Handle receiver: " + activityInfo.name +
                "; enabled: " + enabled);
                return enabled;
            }
        }
        logger.debug("Not found custom message receiver in " + info.packageName);
        // Not found
        return true;
    }
}
