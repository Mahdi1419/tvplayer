# Media3/ExoPlayer ships its own consumer rules. Keep app Activities and model classes
# that may be referenced from Android/resources or Kotlin reflection.
-keep class com.example.tvplayer.**Activity { *; }
-keep class com.example.tvplayer.VideoItem { *; }
