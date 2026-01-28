# Music Player

A simple Music Player but with a rich set of custom features.

> [!NOTE]
> Huge chunks of the project were generated with Gemini 2.5 Pro. The project is currently undergoing major refactoring to remove all AI slop.

## Features

### Audio
The app detects and plays all audio files available on the device. It also supports standard playlist management.

### Custom Start and End Point
You can set a custom start and end point for every single music file.

### Song Analysis
A custom-trained YAMNet is used to classify the song into two categories: `music` or `other`. In the future, it should be possible to filter out unwanted noise automatically.

### Music Recommendation
This is still a work in progress. Currently only YAMNet is used to classify the songs at 5 different timestamps and then get the cosine similarity to find the next song. It works well for some genres but for others, it still struggles.

### YouTube Search
The application includes a function to search for a song on YouTube and get the corresponding link to the video.

> [!NOTE]
> You can not stream or download any video from YouTube.

## Build

To build this project from source, Java 21 and Android Studio are required.

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Run the Gradle task `assembleDebug` to generate the APK.
