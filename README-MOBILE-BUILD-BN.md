# ফোন দিয়ে APK তৈরি (কম্পিউটার ছাড়াই)

## অ্যাপের ফিচার
- MP4 / MKV ফাইল বাছাই (ফরম্যাট সাপোর্ট ফোনের Android মিডিয়া ডিকোডারের ওপর নির্ভরশীল)
- প্রতি ক্লিপ 2, 3, 5, 10, 15, 20, 30 বা 60 সেকেন্ড
- মোট ভিডিও দৈর্ঘ্য ও সম্ভাব্য ক্লিপ সংখ্যা
- কাটিংয়ের অগ্রগতি, সম্পন্ন/বাকি ক্লিপ এবং প্রথম ক্লিপের পর আনুমানিক বাকি সময়
- Pause, Resume ও Stop
- MP4 ক্লিপ `Movies/2SecVideoCutter` ফোল্ডারে সংরক্ষণ

## মোবাইল থেকেই APK তৈরি
এই প্রজেক্টে GitHub Actions workflow আছে; GitHub-এর cloud runner APK বানাবে।

1. ZIP ডাউনলোড করে ফোনে Extract করুন। ভিতরের `TwoSecVideoCutter` ফোল্ডারের সব ফাইল GitHub-এ নতুন repository-তে আপলোড করুন। Repository-র নাম দিতে পারেন `TwoSecVideoCutter`।
2. GitHub repository-তে **Actions** ট্যাব খুলুন। প্রথমবার workflow চালানোর অনুমতি চাইলে **I understand my workflows, go ahead and enable them** চাপুন।
3. **Build Android APK** workflow খুলে **Run workflow** চাপুন; `main` branch নির্বাচন করে সবুজ **Run workflow** চাপুন।
4. Build সফল হলে ওই run-এর পাতার নিচে **Artifacts** অংশে `TwoSecVideoCutter-debug-apk` চাপুন। ZIP ডাউনলোড হবে।
5. ZIP Extract করে `app-debug.apk` ফাইলটি খুলুন। Android অনুমতি চাইলে browser/file manager-কে শুধু এই ইনস্টলেশনের জন্য **Install unknown apps** অনুমতি দিন, তারপর Install চাপুন।

নোট: GitHub account ও build-এর সময় ইন্টারনেট লাগবে। অ্যাপ ইনস্টল হওয়ার পর ভিডিও কাটার কাজ ফোনেই হবে। APK এখানে সরাসরি কম্পাইল করা হয়নি, কারণ এই build পরিবেশে Android SDK/Gradle নেই।
