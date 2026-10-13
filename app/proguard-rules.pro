# Focus Island release rules. Libraries (Room, Firebase, Retrofit, Moshi codegen, OkHttp, Media3,
# ML Kit, WorkManager, Coil) ship their own consumer rules; only what this app does by reflection is here.

# Keep line numbers so crash reports from Play Console are readable (mapping.txt is uploaded by CI).
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Firestore maps documents onto this class by reflection (CommunityViewModel: toObject(FriendRequest::class.java)).
-keep class com.example.ui.screen.community.FriendRequest { *; }

# Retrofit interface with suspend functions: keep the API and the generic signatures it reads.
-keep,allowobfuscation interface com.example.data.api.RadioBrowserApi
-keepclassmembers,allowobfuscation interface com.example.data.api.RadioBrowserApi { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Moshi codegen adapters are found by name from the @JsonClass model.
-keep class com.example.data.api.RadioStationResponse { *; }
-keep class com.example.data.api.RadioStationResponseJsonAdapter { *; }

# Enum values read back from SharedPreferences / intents by name (valueOf).
-keepclassmembers enum com.example.** { public static **[] values(); public static ** valueOf(java.lang.String); }

# Services, receivers, widgets and the live wallpaper are referenced from the manifest (kept by AGP);
# nothing else is loaded by class name.
-dontwarn org.slf4j.**
