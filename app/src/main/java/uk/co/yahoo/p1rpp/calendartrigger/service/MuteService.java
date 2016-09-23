package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.text.DateFormat;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;

import static android.location.LocationManager.KEY_LOCATION_CHANGED;

public class MuteService extends IntentService
	implements SensorEventListener {

	public MuteService() {
		super("CalendarTriggerService");
	}

	public boolean muteRequested;
	public boolean vibrateRequested;
	public boolean canRestoreRinger;
	public boolean wantRestoreRinger;
	public boolean anyStepCountActive;
	public String startEvent;
	public String endEvent;

	// -2 means the step counter is not active
	// -1 means  we've registered our listener but it hasn't been called yet
	// zero or positive is a real step count
	// -1 or greater means we're holding a wake lock because the step counter
	// isn't a wakeup sensor
	private static int lastCounterSteps = -2;
	private static PowerManager.WakeLock wakelock = null;

	// -2 means the location watcher is not active
	// -1 means  we've requested the initial location
	// zero means we're waiting for the next location update
	private static int locationState = -2;

	private static int notifyId = 1400;

	public static final String EXTRA_WAKE_TIME = "wakeTime";
	public static final String EXTRA_WAKE_CAUSE = "wakeCause";
	public static final String EXTRA_PROXIMITY = "wakeEntered";

	// We don't know anything sensible to do here
	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		int newCounterSteps = (int)sensorEvent.values[0];
		if (newCounterSteps != lastCounterSteps)
		{
			lastCounterSteps = newCounterSteps;
			startIfNecessary(this, "Step counter changed");
		}
	}

	public static class StartServiceReceiver
		extends WakefulBroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			long wakeTime = System.currentTimeMillis();
			String wakeCause = intent.toString();
			Intent mute = new Intent(context, MuteService.class);
			mute.putExtra(EXTRA_WAKE_TIME, wakeTime);
			mute.putExtra(EXTRA_WAKE_CAUSE, wakeCause);
			if (intent.hasExtra(KEY_LOCATION_CHANGED))
			{
				mute.putExtra(KEY_LOCATION_CHANGED,
							  intent.getParcelableExtra(KEY_LOCATION_CHANGED));
			}
			startWakefulService(context, mute);
		}
	}

	private void emitNotification(int resNum, String event) {
		Resources res = getResources();
		Notification.Builder builder
			= new Notification.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(res.getString(resNum) + " " + event)
			.setAutoCancel(true);
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(notifyId++, builder.build());
	}

	// return true if step counter is now running
	private boolean StartStepCounter(int classNum) {
		if (lastCounterSteps == -2)
		{
			lastCounterSteps = -1;
			new MyLog(this,
					  "Step counter activated for class"
						  .concat(PrefsManager.getClassName(this, classNum)));
			SensorManager sensorManager =
				(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
			Sensor sensor =
				sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
			if (   (sensor != null)
				&& (sensorManager.registerListener(this, sensor,
						   SensorManager.SENSOR_DELAY_NORMAL)))
			{
				PowerManager powerManager
					= (PowerManager) getSystemService(POWER_SERVICE);
				wakelock = powerManager.newWakeLock(
					PowerManager.PARTIAL_WAKE_LOCK, "CalendarTrigger");
				wakelock.acquire();
				return true;
			}
			else
			{
				return false; // could not activate step counter
			}
		}
		else
		{
			// already starting it for another class
			return true;
		}
	}

	private void startLocationWait(int classNum, Intent intent) {
		Location here = intent.getParcelableExtra(KEY_LOCATION_CHANGED);
		if (locationState == -2)
		{
			locationState = -1;
			LocationManager lm =
				(LocationManager)getSystemService(Context.LOCATION_SERVICE);
			Criteria cr = new Criteria();
			cr.setAccuracy(Criteria.ACCURACY_FINE);
			String s = "CalendarTrigger.Location_"
				.concat(String.valueOf(classNum))
				.concat("_")
				.concat(PrefsManager.getClassName(this, classNum));
			PendingIntent pi = PendingIntent.getBroadcast(
				this, 0 /*requestCode*/,
				new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
			PrefsManager.setLatitude(this, classNum, 300.0);
			lm.requestSingleUpdate(cr, pi);
			new MyLog(this,
					  "Starting location wait for class "
					  .concat(PrefsManager.getClassName(this, classNum)));
		}
		else if (here != null)
		{
			float meters = (float)PrefsManager.getAfterMetres(this, classNum);
			PrefsManager.setLatitude(this, classNum, here.getLatitude());
			PrefsManager.setLongitude(this, classNum, here.getLongitude());
			new MyLog(this,
					  "Set up geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
			if (locationState == -1)
			{
				locationState = 0;
				LocationManager lm =
					(LocationManager)getSystemService(Context.LOCATION_SERVICE);
				Criteria cr = new Criteria();
				cr.setAccuracy(Criteria.ACCURACY_FINE);
				String s = "CalendarTrigger.Location";
				PendingIntent pi = PendingIntent.getBroadcast(
					this, 0 /*requestCode*/,
					new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
				lm.requestLocationUpdates(
					5 * 60 * 1000, (float)(meters * 0.7), cr, pi);
				new MyLog(this,
						  "Requesting location updates for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
			}
		}
	}

	// return true if not left geofence yet
	private boolean checkLocationWait(
		int classNum, double latitude, Intent intent) {
		Location here = intent.getParcelableExtra(KEY_LOCATION_CHANGED);
		if (here != null)
		{
			float meters = (float)PrefsManager.getAfterMetres(this, classNum);
			if (locationState == -1)
			{
				locationState = 0;
				LocationManager lm =
					(LocationManager)getSystemService(Context.LOCATION_SERVICE);
				Criteria cr = new Criteria();
				cr.setAccuracy(Criteria.ACCURACY_FINE);
				String s = "CalendarTrigger.Location";
				PendingIntent pi = PendingIntent.getBroadcast(
					this, 0 /*requestCode*/,
					new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
				lm.requestLocationUpdates(
					5 * 60 * 1000, (float)(meters * 0.7), cr, pi);
				new MyLog(this,
						  "Requesting location updates for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
			}
			if (latitude == 300.0)
			{
				// waiting for current location, and got it
				PrefsManager.setLatitude(this, classNum, here.getLatitude());
				PrefsManager.setLongitude(this, classNum, here.getLongitude());
				new MyLog(this,
						  "Set up geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
				return true;
			}
			// waiting for geofence exit
			{
				float[] results = new float[1];
				double longitude = PrefsManager.getLongitude(this, classNum);
				Location.distanceBetween(latitude, longitude,
										 here.getLatitude(),
										 here.getLongitude(),
										 results);
				if (results[0] < meters)
				{
					new MyLog(this,
							  "Still within geofence for class "
							  .concat(PrefsManager.getClassName(this, classNum))
							  .concat(" at location ")
							  .concat(((Double)here.getLatitude()).toString())
							  .concat(", ")
							  .concat(
								  ((Double)here.getLongitude()).toString()));
					return true;
				}
				// else we've exited the geofence
				PrefsManager.setLatitude(this, classNum, 360.0);
				new MyLog(this,
						  "Exited geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
				return false;
			}
		}
		// location wait active, but no new location
		return true;
	}


	// Determine which event classes have become active
	// and which event classes have become inactive
	// and consequently what we need to do.
	// Incidentally we compute the next alarm time.
	public void updateState(Intent intent) {
		// Timestamp used in all requests (so it remains consistent)
		long currentTime = System.currentTimeMillis();
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int userRinger = audio.getRingerMode();
		canRestoreRinger = userRinger == PrefsManager.getLastRinger(this);
		wantRestoreRinger = false;
		muteRequested = false;
		vibrateRequested = false;
		long nextAlarmTime = Long.MAX_VALUE;
		startEvent = "";
		int classNum;
		String startClassName = "";
		endEvent = "";
		String endClassName = "";
		String alarmReason = "";
		anyStepCountActive = false;
		boolean anyLocationActive = false;
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		PackageManager packageManager = getPackageManager();
		final boolean haveStepCounter =
			currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
			&& packageManager.hasSystemFeature(
				PackageManager.FEATURE_SENSOR_STEP_COUNTER);
		final boolean havelocation =
			PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(
				this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (lastCounterSteps >= 0)
		{
			new MyLog(this, "Step counter running");
		}
		int n = PrefsManager.getNumClasses(this);
		CalendarProvider provider = new CalendarProvider(this);
		for (classNum = 0; classNum < n; ++classNum)
		{
			if (PrefsManager.isClassUsed(this, classNum))
			{
				String className = PrefsManager.getClassName(this, classNum);
				int ringerAction = PrefsManager.getRingerAction(this, classNum);
				CalendarProvider.startAndEnd result
					= provider.nextActionTimes(this, currentTime, classNum);
				boolean triggered
					= PrefsManager.isClassTriggered(this, classNum);
				if (triggered)
				{
					PrefsManager.setClassTriggered(this, classNum, false);
				}
				boolean active =   (result.startTime <= currentTime)
					            && (result.endTime > currentTime);
				if (triggered || active)
				{
					// class should be currently active
					int resNum = R.string.mode_sonnerie_pas_de_change_pour;
					if (ringerAction == AudioManager.RINGER_MODE_SILENT)
					{
						if (!muteRequested)
						{
							resNum = R.string.mode_sonnerie_change_silencieux_pour;
							muteRequested = true;
							wantRestoreRinger = false;
						}
					}
					else if (ringerAction == AudioManager.RINGER_MODE_VIBRATE)
					{
						if (!(muteRequested | vibrateRequested))
						{
							resNum = R.string.mode_sonnerie_change_vibreur_pour;
							vibrateRequested = true;
							wantRestoreRinger = false;
						}
					}
					if (!PrefsManager.isClassActive(this, classNum))
					{
						startEvent = result.startEventName;
						if (PrefsManager.getNotifyStart(this, classNum))
						{
							emitNotification(resNum, startEvent);
						}
						PrefsManager.setTargetSteps(this, classNum, 0);
						PrefsManager.setLatitude(this, classNum, 360.0);
						PrefsManager.setClassActive(this, classNum, true);
						startClassName = className;
					}
					if (result.endTime < nextAlarmTime)
					{
						nextAlarmTime = result.endTime;
						endClassName = className;
						alarmReason = " for end of event "
									  .concat(result.endEventName)
									  .concat(" of class ")
									  .concat(className);
					}
					PrefsManager.setLastActive(
						this, classNum, result.endEventName);
				}
				if (triggered || !active)
				{
					// class should not be currently active
					boolean done = false;
					boolean waiting = false;
					if (PrefsManager.isClassActive(this, classNum))
					{
						// ... but it is
						PrefsManager.setClassActive(this, classNum, false);
						done = true;
						if (haveStepCounter
							&& PrefsManager.getAfterSteps(this, classNum) > 0)
						{
							if (lastCounterSteps < 0)
							{
								// need to start up the sensor
								if (StartStepCounter(classNum))
								{
									PrefsManager.setTargetSteps(
										this, classNum, 0);
									anyStepCountActive = true;
									waiting = true;
									done = false;
								}
							}
							else if (lastCounterSteps >= 0)
							{
								PrefsManager.setTargetSteps(
									this, classNum,
									lastCounterSteps +
									PrefsManager.getAfterSteps(this, classNum));
								anyStepCountActive = true;
								waiting = true;
								done = false;
							}
						}
						if (   havelocation
							&& (PrefsManager.getAfterMetres(
									this, classNum) > 0))
						{
							// keep it active while waiting for location
							startLocationWait(classNum, intent);
							anyLocationActive = true;
							waiting = true;
							done = false;
						}
						if (waiting)
						{
							PrefsManager.setClassWaiting(this, classNum, true);
						}
					}
					else if (PrefsManager.isClassWaiting(this, classNum))
					{
						done = true;
						int steps = PrefsManager.getAfterSteps(this, classNum);
						if ((lastCounterSteps > -2) && (steps > 0))
						{
							steps = PrefsManager.getTargetSteps(this, classNum);
							if (steps == 0)
							{
								if (lastCounterSteps >= 0)
								{
									PrefsManager.setTargetSteps(
										this, classNum,
										lastCounterSteps + steps);
								}
								anyStepCountActive = true;
								waiting = true;
								done = false;
							}
							else if (lastCounterSteps < steps)
							{
								anyStepCountActive = true;
								waiting = true;
								done = false;
							}
						}
						double latitude
							= PrefsManager.getLatitude(this, classNum);
						if (   (latitude != 360.0)
							&& checkLocationWait(classNum, latitude, intent))
						{
							anyLocationActive = true;
							waiting = true;
							done = false;
						}
					}
					if (done)
					{
						String last =
							PrefsManager.getLastActive(this, classNum);
						if (   (PrefsManager.getRestoreRinger(this, classNum))
							   && !(muteRequested | vibrateRequested))
						{
							wantRestoreRinger = true;
							if (PrefsManager.getNotifyEnd(this, classNum))
							{
								int resNum =
									R.string.mode_sonnerie_pas_de_change_apres;
								int ringer = PrefsManager.getUserRinger(this);
								if (   (ringer != audio.getRingerMode())
									&& canRestoreRinger)
								{
									resNum =
										(ringer == AudioManager.RINGER_MODE_VIBRATE)
										? R.string.mode_sonnerie_change_vibreur_apres
										: R.string.mode_sonnerie_change_normale_apres;
								}
								emitNotification(resNum, last);
							}
							endEvent = last;
							endClassName = className;
						}
						PrefsManager.setClassActive(this, classNum, false);
						PrefsManager.setClassWaiting(this, classNum, false);
					}
					else if (waiting)
					{
						if (ringerAction == AudioManager.RINGER_MODE_SILENT)
						{
							if (!muteRequested)
							{
								muteRequested = true;
								wantRestoreRinger = false;
							}
						}
						else if (ringerAction == AudioManager.RINGER_MODE_VIBRATE)
						{
							if (!(muteRequested | vibrateRequested))
							{
								vibrateRequested = true;
								wantRestoreRinger = false;
							}
						}
					}
					if (result.startTime < nextAlarmTime)
					{
						nextAlarmTime = result.startTime;
						alarmReason = " for start of event "
							.concat(result.startEventName)
							.concat(" of class ")
							.concat(className);
					}
				}
			}
		}
		if (muteRequested)
		{
			if (PrefsManager.getLastRinger(this) == PrefsManager
				.RINGER_MODE_NONE)
			{
				PrefsManager.setUserRinger(this, userRinger);
			}
			audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
			PrefsManager.setLastRinger(this,AudioManager.RINGER_MODE_SILENT);
			if (!startEvent.equals(""))
			{
				new MyLog(this, "Setting ringer to "
					.concat(MyLog.rm(AudioManager.RINGER_MODE_SILENT))
					.concat(" for start of event ")
					.concat(startEvent)
					.concat(" of class ")
					.concat(startClassName));
			}
		}
		else if (vibrateRequested)
		{
			if (PrefsManager.getLastRinger(this) == PrefsManager
				.RINGER_MODE_NONE)
			{
				PrefsManager.setUserRinger(this, userRinger);
			}
			audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
			PrefsManager.setLastRinger(this,AudioManager.RINGER_MODE_VIBRATE);
			if (!startEvent.equals(""))
			{
				new MyLog(this, "Setting ringer to "
					.concat(MyLog.rm(AudioManager.RINGER_MODE_VIBRATE))
					.concat(" for start of event ")
					.concat(startEvent)
					.concat(" of class ")
					.concat(startClassName));
			}
		}
		else if (wantRestoreRinger)
		{
			int ringer = PrefsManager.getUserRinger(this);
			if ((ringer != userRinger) && canRestoreRinger)
			{
				audio.setRingerMode(ringer);
				if (!endEvent.equals(""))
				{
					new MyLog(this, "Restoring ringer to "
						.concat(MyLog.rm(ringer))
						.concat(" after event ")
						.concat(endEvent)
						.concat(" of class ")
						.concat(endClassName));
				}
			}
			PrefsManager.setLastRinger(this, PrefsManager.RINGER_MODE_NONE);
		}

		long lastAlarm = PrefsManager.getLastAlarmTime(this);
		// Try always setting alarm to see if it works better
		if (true/*nextAlarmTime != lastAlarm*/)
		{
			int flags = 0;
			PendingIntent pIntent = PendingIntent.getBroadcast(
				this, 0 /*requestCode*/,
				new Intent("CalendarTrigger.Alarm", Uri.EMPTY,
					this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
			AlarmManager alarmManager = (AlarmManager)
				getSystemService(Context.ALARM_SERVICE);
			// Remove previous alarms
			if (lastAlarm != Long.MAX_VALUE)
			{
				alarmManager.cancel(pIntent);
				new MyLog(this, "Alarm cancelled");
			}

			if (nextAlarmTime != Long.MAX_VALUE)
			{
				// Add new alarm
				alarmManager.setExact(
					AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				DateFormat df = DateFormat.getDateTimeInstance();
				new MyLog(this, "Alarm time set to "
								.concat(df.format(nextAlarmTime))
								.concat(alarmReason));
			}
		}
		else
		{
			DateFormat df = DateFormat.getDateTimeInstance();
			new MyLog(this, "Alarm unchanged at "
							.concat(df.format(nextAlarmTime)));
		}
		PrefsManager.setLastAlarmTime(this, nextAlarmTime);
		PrefsManager.setLastInvocationTime(this,currentTime);
		if (!anyStepCountActive)
		{
			if (lastCounterSteps >= 0)
			{
				lastCounterSteps = -2;
				SensorManager sensorManager =
					(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
				sensorManager.unregisterListener(this);
				new MyLog(this, "Step counter deactivated");
				wakelock.release();
			}
		}
		if (!anyLocationActive)
		{
			if (locationState == 0)
			{
				String s = "CalendarTrigger.Location";
				PendingIntent pi = PendingIntent.getBroadcast(
					this, 0 /*requestCode*/,
					new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
				LocationManager lm = (LocationManager)getSystemService(
					Context.LOCATION_SERVICE);
				lm.removeUpdates(pi);
			}
			locationState = -2;
		}
	}


	@Override
	public void onHandleIntent(Intent intent) {
		String action = intent.getAction();
		if (action == null)
		{
			action = "";
		}
		String wake;
		String proximity;
		DateFormat df = DateFormat.getDateTimeInstance();
		if (intent.hasExtra(EXTRA_WAKE_TIME))
		{
			wake = " received at "
				.concat(df.format(intent.getLongExtra(EXTRA_WAKE_TIME, 0)));
		}
		else
		{
			wake = "";
		}
		String cause;
		if (intent.hasExtra(EXTRA_WAKE_CAUSE))
		{
			cause = intent.getStringExtra(EXTRA_WAKE_CAUSE);
		}
		else
		{
			cause = "null action";
		}
		if (intent.hasExtra(EXTRA_PROXIMITY))
		{
			proximity = intent.getBooleanExtra(EXTRA_PROXIMITY, false)
						? " entering" : " leaving";
		}
		else
		{
			proximity = "";
		}
		new MyLog(this, "onReceive("
				        .concat(action)
				  		.concat(") from ")
						.concat(cause)
						.concat(wake)
						.concat(proximity));

		updateState(intent);
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}
	
	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
