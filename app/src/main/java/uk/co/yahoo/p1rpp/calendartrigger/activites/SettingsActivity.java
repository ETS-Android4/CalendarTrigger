/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.PermissionChecker;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.text.DateFormat;

import uk.co.yahoo.p1rpp.calendartrigger.BuildConfig;
import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;

/**
 * Created by rparkins on 29/08/16.
 */
public class SettingsActivity extends Activity {
    private CheckBox nextLocation;
    public SettingsActivity settingsActivity;
    private Button fakecontact;
    private ListView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        settingsActivity = this;
        fakecontact = null;
        log = null;
    }

    private void doReset() {
        startService(new Intent(
            MuteService.MUTESERVICE_RESET, null, this, MuteService.class));

    }

    private void setNextLocationState(CheckBox v, boolean nl) {
        boolean read = PackageManager.PERMISSION_GRANTED ==
                       PermissionChecker.checkSelfPermission(
                           this, Manifest.permission.READ_CONTACTS);
        boolean write = PackageManager.PERMISSION_GRANTED ==
                        PermissionChecker.checkSelfPermission(
                            this, Manifest.permission.WRITE_CONTACTS);
        if (read && write)
        {
            v.setTextColor(0xFF000000);
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(settingsActivity, R.string.nextLocationHelp,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            PrefsManager.setNextLocationMode(settingsActivity, nl);
            v.setChecked(nl);
        } else
        {
            v.setTextColor(0x80000000);
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(settingsActivity, R.string.nextLocationNotAllowed,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            PrefsManager.setNextLocationMode(settingsActivity, false);
            v.setChecked(false);
        }
    }

    private void showLog() {
        if (log == null)
        {
            log = new ListView(this);
            ArrayAdapter<String> adapter
                = new ArrayAdapter<String> (this, R.layout.activity_text_viewer);
            try
            {
                BufferedReader in
                    = new BufferedReader(
                    new InputStreamReader(
                        new FileInputStream(MyLog.LogFileName())));
                String s = "";
                String line;
                while ((line = in.readLine()) != null)
                {
                    adapter.add(line);
                }
                log.setAdapter(adapter);
            }
            catch (FileNotFoundException e)
            {
                Toast.makeText(this, R.string.nologfile,
                               Toast.LENGTH_LONG).show();
                log = null;
                return;
            }
            catch (java.io.IOException e)
            {
                Toast.makeText(this, "Exception "
                                     + e.getMessage(),
                               Toast.LENGTH_LONG).show();
            }
            log.setFastScrollEnabled(true);
            log.setFastScrollAlwaysVisible(true);
            log.setDivider(null);
            setContentView(log);
            fakecontact = null;
        }
    }

    protected void reResume() {
        final SettingsActivity me = this;
        TextView tv = (TextView)findViewById(R.id.versiontext);
        PackageManager pm = getPackageManager();
        try
        {
            PackageInfo pi = pm.getPackageInfo(
                "uk.co.yahoo.p1rpp.calendartrigger", 0);
            tv.setText("CalendarTrigger ".concat(pi.versionName));
        }
        catch (PackageManager.NameNotFoundException e)
        {
        }
        tv = (TextView)findViewById(R.id.lastcalltext);
        DateFormat df = DateFormat.getDateTimeInstance();
        long t = PrefsManager.getLastInvocationTime(this);
        tv.setText(getString(R.string.lastcalldetail, df.format(t)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.lastCallHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.lastalarmtext);
        t = PrefsManager.getLastAlarmTime(this);
        tv.setText(getString(R.string.lastalarmdetail, df.format(t)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.lastAlarmHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.laststatetext);
        int mode = PrefsManager.getLastRinger(this);
        tv.setText(getString(R.string.laststatedetail,
                             PrefsManager.getRingerStateName(this, mode)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.lastStateHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.userstatetext);
        mode = PrefsManager.getUserRinger(this);
        tv.setText(getString(R.string.userstatedetail,
                             PrefsManager.getRingerStateName(this, mode)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.userStateHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.currentstatetext);
        mode = PrefsManager.getCurrentMode(this);
        tv.setText(getString(R.string.currentstatedetail,
                             PrefsManager.getRingerStateName(this, mode)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.currentStateHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.locationtext);
        mode = PrefsManager.getCurrentMode(this);
        tv.setText(getString(PrefsManager.getLocationState(this) ?
                             R.string.yeslocation :
                             R.string.nolocation));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.LocationStateHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.wakelocktext);
        if (MuteService.wakelock == null)
        {
            tv.setText(getString(R.string.nowakelock));
        } else
        {
            tv.setText(getString(R.string.yeswakelock));
        }
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.wakelockHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById(R.id.logfiletext);
        String s = MyLog.LogFileName();
        tv.setText(getString(R.string.Logging, s));
        boolean canStore = PackageManager.PERMISSION_GRANTED ==
                           PermissionChecker.checkSelfPermission(
                               me, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int colour = canStore ? 0xFF000000 : 0x80000000;
        final RadioGroup rg = (RadioGroup)findViewById(R.id.radioGroupLogging);
        Button b = (Button)findViewById(R.id.radioLoggingOn);
        b.setTextColor(colour);
        if (canStore)
        {
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    PrefsManager.setLoggingMode(me, true);
                }
            });
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(me, R.string.loggingOnHelp,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        } else
        {
            PrefsManager.setLoggingMode(me, false);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    rg.check(R.id.radioLoggingOff);
                }
            });
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(me, R.string.cantLogHelp,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        b = (Button)findViewById(R.id.radioLoggingOff);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PrefsManager.setLoggingMode(me, false);
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.loggingOffHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        rg.check(PrefsManager.getLoggingMode(me)
                 ? R.id.radioLoggingOn : R.id.radioLoggingOff);
        b = (Button)findViewById(R.id.clear_log);
        b.setText(R.string.clearLog);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                (new File(MyLog.LogFileName())).delete();
                Toast.makeText(me, R.string.logCleared, Toast.LENGTH_SHORT)
                     .show();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.clearLogHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.show_log);
        b.setText(R.string.showLog);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showLog();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.showLogHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        nextLocation = (CheckBox)findViewById(R.id.nextlocationbox);
        nextLocation.setText(R.string.nextLocationLabel);
        nextLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBox cb = (CheckBox)v;
                setNextLocationState(
                    cb, !PrefsManager.getNextLocationMode(settingsActivity));
            }
        });
        setNextLocationState(nextLocation,
                             PrefsManager.getNextLocationMode(this));
        b = (Button)findViewById(R.id.resetbutton);
        b.setText(R.string.reset);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doReset();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.resethelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        if (BuildConfig.DEBUG)
        {
            if (fakecontact == null)
            {
                fakecontact = new Button(this);
                fakecontact.setText("Fake Contact");
                fakecontact.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent it = new Intent(me, FakeContact.class);
                        startActivity(it);
                    }
                });
                LinearLayout ll =
                    (LinearLayout)findViewById(R.id.edit_activity_container);
                ll.addView(fakecontact);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (log == null)
        {
            final SettingsActivity me = this;
            reResume();
        }
    }
    
    @Override
    public void onBackPressed() {
        if (log != null)
        {
            setContentView(R.layout.settings_activity);
            log = null;
            reResume();
        }
        else
        {
            super.onBackPressed();
        }
    }

}
