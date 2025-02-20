-dontwarn android.**
-optimizationpasses 10
-keep public class android.** { *; }
-keep class android.media.MediaPlayer { *; }
-keep class android.view.SurfaceView { *; }
-keep class android.view.SurfaceHolder { *; }
-keepclassmembers class android.media.MediaPlayer {
    public void setDataSource(...);
    public void setDisplay(...);
    public void prepare();
    public void prepareAsync();
    public void start();
    public void pause();
    public void stop();
    public void release();
    public void seekTo(int);
}
-keep class android.widget.** { *; }
-keep class android.view.** { *; }
-keepclassmembers class * implements android.view.SurfaceHolder$Callback { *; }
-keepclassmembers class * implements android.media.MediaPlayer$OnPreparedListener { *; }
-keepclassmembers class * implements android.media.MediaPlayer$OnCompletionListener { *; }
-keepclassmembers class * implements android.media.MediaPlayer$OnErrorListener { *; }
-keepclassmembers class * implements android.media.MediaPlayer$OnBufferingUpdateListener { *; }
-keepclassmembers class * implements android.media.MediaPlayer$OnInfoListener { *; }
-keepclassmembers class * implements android.media.MediaPlayer$OnSeekCompleteListener { *; }
-keep class com.coara.videoplay.MainActivity { *; }
-keepclassmembers class com.coara.videoplay.MainActivity {
    public void *(...);
}
-keep class * extends android.app.Activity { *; }
-keepclassmembers class * implements java.io.Serializable {  
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    private void readObjectNoData();
}
-allowaccessmodification
-dontpreverify
