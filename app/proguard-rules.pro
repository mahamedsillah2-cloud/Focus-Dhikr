# Room generates code reflectively referenced by name.
-keep class com.focusdhikr.data.db.** { *; }

# Entry points bound by the OS by class name from the manifest.
-keep class com.focusdhikr.service.** extends android.app.Service { *; }
-keep class com.focusdhikr.service.** extends android.content.BroadcastReceiver { *; }
