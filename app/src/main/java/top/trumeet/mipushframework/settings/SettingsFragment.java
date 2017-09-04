package top.trumeet.mipushframework.settings;

import android.os.Bundle;

import com.xiaomi.xmsf.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import moe.shizuku.preference.Preference;
import moe.shizuku.preference.PreferenceFragment;
import top.trumeet.mipushframework.log.LogUtils;
import top.trumeet.mipushframework.push.PushController;
import top.trumeet.mipushframework.push.PushServiceAccessibility;

/**
 * Created by Trumeet on 2017/8/27.
 * Main settings
 * @see MainActivity
 * @author Trumeet
 */

public class SettingsFragment extends PreferenceFragment {
    private Logger logger = LoggerFactory.getLogger(SettingsFragment.class);

    private Preference mDozePreference;
    private Preference mCheckServicePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        mDozePreference = getPreferenceScreen()
                .findPreference("key_remove_doze");
        mCheckServicePreference = getPreferenceScreen()
                .findPreference("key_check_service");

        Preference getLogPrefernece = getPreferenceScreen()
                .findPreference("key_get_log");
        getLogPrefernece.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                LogUtils.shareFile(getActivity());
                return true;
            }
        });
    }

    @Override
    public void onStart () {
        super.onStart();
        long time = System.currentTimeMillis();
        mDozePreference.setVisible(!PushServiceAccessibility.isInDozeWhiteList(getActivity()));
        mCheckServicePreference.setVisible(!(PushController.isPrefsEnable(getActivity()) &&
                PushController.isServiceRunning(getActivity())));
        logger.debug("rebuild UI took: " + String.valueOf(System.currentTimeMillis() -
                time));
    }
}
