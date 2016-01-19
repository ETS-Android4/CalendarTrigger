package com.RPP.calendartrigger.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;

import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.models.Calendar;

import java.util.ArrayList;
import java.util.GregorianCalendar;

public class CalendarProvider {

	private Context context;

	public CalendarProvider(Context context) {
		this.context = context;
	}

	// Projection for calendar queries
	public static final String[] CALENDAR_PROJECTION = new String[] {
		Calendars._ID,
		Calendars.CALENDAR_DISPLAY_NAME,
		Calendars.SYNC_EVENTS
	};

	public static final int CALENDAR_PROJECTION_ID_INDEX = 0;
	public static final int CALENDAR_PROJECTION_DISPLAY_NAME_INDEX = 1;
	public static final int CALENDAR_PROJECTION_IS_SYNCED_INDEX = 2;

	/**
	 * List the user's calendars
	 *
	 * @return All calendars of the user
	 */
	public Calendar[] listCalendars() {

		ContentResolver cr = context.getContentResolver();

		Uri calendarUri = Calendars.CONTENT_URI;

		Cursor cur
			= cr.query(calendarUri, CALENDAR_PROJECTION, null, null, null);

		if (cur == null)
			return null;

		Calendar[] res = new Calendar[cur.getCount()];

		int i = 0;
		while (cur.moveToNext())
		{

			long calendarId = cur.getLong(CALENDAR_PROJECTION_ID_INDEX);
			res[i] = new Calendar(calendarId,
						cur.getString(CALENDAR_PROJECTION_DISPLAY_NAME_INDEX),
						cur.getInt(CALENDAR_PROJECTION_IS_SYNCED_INDEX) == 1);

			i++;
		}
		cur.close();

		return res;
	}

	private Uri getInstancesQueryUri() {
		// Event search window : from one month before to one month after, to be sure
		GregorianCalendar dateDebut = new GregorianCalendar();
		dateDebut.add(GregorianCalendar.MONTH, -1);
		GregorianCalendar dateFin = new GregorianCalendar();
		dateFin.add(GregorianCalendar.MONTH, 1);

		// search URI (contains the search window)
		Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
		ContentUris.appendId(builder, dateDebut.getTimeInMillis());
		ContentUris.appendId(builder, dateFin.getTimeInMillis());

		return builder.build();
	}

	private String likeQuote(String s) {
		StringBuilder result = new StringBuilder(" LIKE \"%");
		String es = s.replace("*", "**").replace("%", "*%")
					 .replace("_", "*_").replace("\"", "\"\"");
		result.append(es).append("%\" ESCAPE \"*\"");
		return result.toString();
	}

	// Make selection string for an event class
	private StringBuilder selection(Context context, int classNum) {
		ArrayList<Long> calendarIds
			= PrefsManager.getCalendars(context, classNum);
		StringBuilder selClause = new StringBuilder();
		// for now we don't handle state-only events with no calendars
		if (calendarIds.isEmpty())
		{
			return selClause;
		}
		selClause.append("(");
		boolean first = true;
		for (long id : calendarIds)
		{
			if (first)
			{
				first = false;
			} else
			{
				selClause.append(" OR ");
			}
			selClause.append("(").append(Instances.CALENDAR_ID)
					 .append("=").append(id).append(")");
		}
		selClause.append(") AND ").append(Instances.ALL_DAY)
				 .append(" = 0");
		String s = PrefsManager.getEventName(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.TITLE)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventLocation(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.EVENT_LOCATION)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventDescription(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.DESCRIPTION)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventColour(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.EVENT_COLOR)
					 .append(likeQuote(s));
		}
		switch (PrefsManager.getWhetherBusy(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.AVAILABILITY)
						 .append("=").append(Instances.AVAILABILITY_BUSY);
				break;
			case 2:
				selClause.append(" AND ").append(Instances.AVAILABILITY)
						 .append("=").append(Instances.AVAILABILITY_FREE);
				break;
			default:
		}
		switch (PrefsManager.getWhetherRecurrent(context, classNum))
		{
			// check if missing entry is null or ""
			case 1:
				selClause.append(" AND ").append(Instances.RRULE)
						 .append(" IS NOT NULL");
				break;
			case 2:
				selClause.append(" AND ").append(Instances.RRULE)
						 .append(" IS NULL");
				break;
			default:
		}
		switch (PrefsManager.getWhetherOrganiser(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER)
						 .append(" = 1");
				break;
			case 2:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER)
						 .append(" != 1");
				break;
			default:
		}
		switch (PrefsManager.getWhetherPublic(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.ACCESS_LEVEL)
						 .append(" != ").append(Instances.ACCESS_PRIVATE);
				break;
			case 2:
				selClause.append(" AND ").append(Instances.ACCESS_LEVEL)
						 .append(" = ").append(Instances.ACCESS_PRIVATE);
				break;
			default:
		}
		switch (PrefsManager.getWhetherAttendees(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.HAS_ATTENDEE_DATA)
						 .append(" = 1");
				break;
			case 2:
				selClause.append(" AND ").append(Instances.HAS_ATTENDEE_DATA)
						 .append(" = 0");
				break;
			default:
		}
		return selClause;
	}

	// get next action times for event class
	public class startAndEnd {
		public long startTime;
		public long endTime;
		public String eventName;
	}

	private static final String[] INSTANCE_PROJECTION = new String[] {
		Instances.BEGIN,
		Instances.END,
		Instances.TITLE
	};

	private static final int INSTANCE_PROJECTION_BEGIN_INDEX = 0;
	private static final int INSTANCE_PROJECTION_END_INDEX = 1;
	private static final int INSTANCE_PROJECTION_TITLE_INDEX = 2;

	public startAndEnd nextActionTimes(
		Context context, long currentTime, int classNum) {
		startAndEnd result = new startAndEnd();
		result.startTime = Long.MAX_VALUE;
		result.endTime = currentTime;
		result.eventName = "";
		ContentResolver cr = context.getContentResolver();
		StringBuilder selClause = selection(context, classNum);
		selClause.append(" AND ( ").append(Instances.BEGIN)
				 .append(" > ? OR ").append(Instances.END)
				 .append(" > ? )");
		int before = PrefsManager.getBeforeMinutes(context, classNum);
		int after = PrefsManager.getAfterMinutes(context, classNum);
		String[] selectionArgs = new String[] {
			String.valueOf(currentTime + before),
			String.valueOf(currentTime - after)
		};
		// Do query sorted by start time
		Cursor cur = cr.query(getInstancesQueryUri(), INSTANCE_PROJECTION,
							  selClause.toString(), selectionArgs,
							  Instances.BEGIN);
		while (cur.moveToNext())
		{
			long start = cur.getLong(INSTANCE_PROJECTION_BEGIN_INDEX) - before;
			long end = cur.getLong(INSTANCE_PROJECTION_END_INDEX) + after;
			if (start <= result.endTime)
			{
				if (end > result.endTime)
				{
					// extend end time for overlapping event
					result.endTime = end;
					if (result.startTime < Long.MAX_VALUE)
					{
						result.eventName =
							cur.getString(INSTANCE_PROJECTION_TITLE_INDEX);
					}
				}
			}
			else if (currentTime == result.endTime)
			{
				if (start < result.startTime)
				{
					// This can only happen once, because we sort the
					// query on ascending start time
					result.startTime = start;
					result.endTime = end;
					result.eventName =
						cur.getString(INSTANCE_PROJECTION_TITLE_INDEX);
				}
			}
		}
		cur.close();
		return result;
	}
}