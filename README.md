CalendarTrigger
---------------

Trigger actions on your Android device based on calendar events

This program is a generalisation of RemiNV/CalendarMute.

Latest version (1.3.2) supports posting notifications with sounds. You can browse the filesystem for a suitable sound. Obviously the sound doesn't get played if the event mutes the audio. 1.3.2 also fixes some issues which sometimes caused CalendarTrigger to get confused and need you to reset it.

It is open source, free, and does not display adverts or pester you for a donation. This will never change as long as I am maintaining it. If you want to report a problem, please enable logging and provide a log file.

CalendarTrigger supports classes of events. The event start actions currently available are to set the ringer to mute or vibrate or to set the audio Do Not Disturb mode on Android versions which support it, to play a sound, and optionally to show a notification if it changes the ringer state or plays a sound. I may add other actions. Event start actions can be perfomed a set interval before the start of teh event or can be delayed until the device is in a particular orientation or being charged by a particular type of charger or not being charged at all. The event end actions currently supported are to do nothing or to restore the original ringer state, and optionally to show a notification which can play a sound: again I may add others. The event end action can be delayed by a set time or until the device has moved by a certain distance (if it has a location sensor) or until the person holding the device has taken a certain number of steps (if it has a step counter). This can be useful if you don't know exactly when an event will end, and you want to unmute the ringer when you leave the room or leave the building. This version also has immediate events, useful if you walk into a "quiet" building and want to mute your ringer until you leave.

If an event is in more than one class, the actions for all the classes which contain it are triggered. This can be used, for example, to play a reminder sound a few minutes before the start of an event and then set a muting mode during the event.

The UI is available in English and French: the French version could probably be improved as I am not a native speaker.

Help with the French translations would be welcome, as would UI translations for other languages.

#### What can I legally do with this app ?
This application is released under the GNU GPL v3 or later, do make sure you abide by the license terms when using it.
Read the license terms for more details, but to make it very (too) simple: you can do everything you want with the application, as long as you provide your source code with any version you release, and release it under the same license.
