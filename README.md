# 2 Sec Video Cutter (Android Studio project)

বাংলা UI-সহ Android app source project. এটি Android-এর MediaExtractor/MediaMuxer ব্যবহার করে MP4 output-এ ভিডিও ভাগ করে।

## Build APK
1. Android Studio-তে এই project folder খুলুন।
2. Gradle sync সম্পন্ন করুন (JDK 17 এবং Android SDK Platform 35 প্রয়োজন)।
3. Build > Build Bundle(s) / APK(s) > Build APK(s) নির্বাচন করুন।
4. APK পাবেন `app/build/outputs/apk/debug/app-debug.apk` এ।

## সীমাবদ্ধতা
- ক্লিপের সময়সীমা ২ সেকেন্ড লক্ষ্য করে; ভিডিওর keyframe অবস্থান ও ডিভাইসের codec support অনুযায়ী শুরুর/শেষের সময় কিছুটা এদিক-সেদিক হতে পারে।
- আউটপুট MP4। MKV input চালানো নির্ভর করে Android-এর extractor এবং ভিডিও codec support-এর ওপর; সব MKV/codec সব ফোনে সমর্থিত নয়।
- এটি stream-copy ভিত্তিক, তাই দ্রুত; তবে কিছু ফোনে muxer codec/container সীমাবদ্ধতার কারণে নির্দিষ্ট ভিডিও ব্যর্থ হতে পারে।
