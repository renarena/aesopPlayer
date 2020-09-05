# Add project specific ProGuard rules here.
# Studio generates a default file... this just extends it
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Just to remember the spelling if needed
# -dontobfuscate # for when readable stack traces needed wuen minimizing

# EventBus (3.x.x)
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Settings fragments that are referenced with the app:fragment property on preferences
# are not recognized by ProGuard as being used and are removed. Keep them.
-keep class com.donnKey.aesopPlayer.ui.settings**

# Guava
-dontwarn sun.misc.Unsafe
## https://github.com/google/guava/issues/2926#issuecomment-325455128
## https://stackoverflow.com/questions/9120338/proguard-configuration-for-guava-with-obfuscation-and-optimization
-dontwarn com.google.common.base.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# Added for guava 23.5-android
-dontwarn afu.org.checkerframework.**
-dontwarn org.checkerframework.**

-dontwarn com.sun.mail.handlers.handler_base.getTransferDataFlavors # "does not type check and will be assumed to be unreachable."

-keepattributes SourceFile, LineNumberTable

# Required to preserve the Flurry SDK - currently disabled in build.gradle
#-keep class com.flurry.** { *; }
#-dontwarn com.flurry.**
#-keepattributes *Annotation*,EnclosingMethod,Signature
#-keepclasseswithmembers class * {
#  public <init>(android.content.Context, android.util.AttributeSet, int);
#}
#  -- end Flurry config --

# Jaudiotagger (Shows up as ugly chapter titles if it fails.)
#-keep class org.jaudiotagger.audio.Audio** {*;}
#-keep class org.jaudiotagger.tag.** {*;}
-keep class com.github.AdrienPoupa.jaudiotagger.tag** {*;}
-keep class org.jaudiotagger.tag.id3** {*;}

# Google Play Services library
# The docs say this is done automatically
-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
  public static final *** NULL;
}

-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
  @com.google.android.gms.common.annotation.KeepName *;
}

-keepnames class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# Mail dynamically loads the resource/settings(?) store.
# We only use imaps (SSL), but if that changes...
#-keep class com.sun.mail.imap.IMAPStore {*;}
-keep class com.sun.mail.imap.IMAPSSLStore {*;}
-keep class com.sun.mail.smtp.SMTPSSLProvider {*;}
-keep class com.sun.mail.smtp.SMTPSSLTransport {*;}

# This is critical to actually read mail content.
-keep class com.sun.mail.handlers** { *; }
