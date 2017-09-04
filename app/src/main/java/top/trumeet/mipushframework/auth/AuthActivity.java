package top.trumeet.mipushframework.auth;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.WindowManager;

import com.xiaomi.xmsf.R;

import top.trumeet.mipushframework.register.RegisterDB;
import top.trumeet.mipushframework.register.RegisteredApplication;

import static top.trumeet.mipushframework.Constants.EXTRA_MI_PUSH_PACKAGE;

/**
 * Created by Trumeet on 2017/8/27.
 * @author Trumeet
 */

public class AuthActivity extends AppCompatActivity {
    /**
     * Application details
     */
    public static final String EXTRA_REGISTERED_APPLICATION
            = AuthActivity.class.getName()
            + ".EXTRA_REGISTERED_APPLICATION";

    private RegisteredApplication application;

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().hasExtra(EXTRA_REGISTERED_APPLICATION)) {
            finish();
            return;
        }

        application =
                getIntent().getParcelableExtra(EXTRA_REGISTERED_APPLICATION);

        CharSequence name;
        try {
            name = getPackageManager().getApplicationLabel(getPackageManager()
                    .getApplicationInfo(application.getPackageName(), 0));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            name = application.getPackageName();
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(Html.fromHtml(getString(R.string.auth_message,
                        name)))
                .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        setResultAndFinish(RegisteredApplication.Type.ALLOW);
                    }
                })
                .setNegativeButton(R.string.deny, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        setResultAndFinish(RegisteredApplication.Type.DENY);
                    }
                })
                .setNeutralButton(R.string.allow_once, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        setResultAndFinish(RegisteredApplication.Type.ALLOW_ONCE);
                    }
                })
                .setCancelable(false)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        setResultAndFinish(RegisteredApplication.Type.DENY);
                    }
                })
                .show();
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.dimAmount=0.7f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        dialog.getWindow().setAttributes(lp);
    }

    @Override
    public void onConfigurationChanged (Configuration configuration) {
        super.onConfigurationChanged(configuration);
    }

    /**
     * Update db and restart service.
     * @param type Type
     */
    private void setResultAndFinish (@RegisteredApplication.Type int type) {
        application.setType(type);
        // DB in UI, too bad
        RegisterDB.update(application, this);
        startService(new Intent(this, com.xiaomi.xmsf.push.service.XMPushService.class)
        .putExtra(EXTRA_MI_PUSH_PACKAGE, application.getPackageName()));
        finish();
    }
}
