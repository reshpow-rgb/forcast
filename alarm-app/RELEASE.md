# Releasing RiseUp on Google Play (and making money from it)

Rough timeline: about a day of setup, then **14 days of closed testing** (required for new personal
accounts), then Google's review (usually a few days). Costs: a one-time **$25** Play Console fee. AdMob is free.

---

## Part 1: Before you start

### 1.1 Pick your permanent app ID (do this first)
Every Play app has a unique ID that can **never change** after the first upload. Right now it's
`com.riseup.alarm`, which may already be taken. Pick something only you would use, for example
`com.yourname.riseup`, and change `applicationId` in `app/build.gradle.kts` (or ask Claude to do it).

The display name "RiseUp" *can* be changed later. Search Play for "RiseUp alarm" first. If a well-known
app already uses the name, choose another so you don't get a trademark complaint.

### 1.2 Create your accounts
1. **Google Play Console**: <https://play.google.com/console/signup>. Pay $25 and verify your identity.
   - **Personal** account: quickest, but you must run a closed test with at least 12 testers for 14 days
     before you can publish publicly (see Part 4).
   - **Organisation** account: no tester requirement, but needs a registered business and a free D-U-N-S number.
2. **AdMob**: <https://admob.google.com>, signed in with the same Google account. Add your payment and tax details.

### 1.3 Host the privacy policy (required because the app shows ads)
1. Open `alarm-app/privacy-policy.html` and replace both `YOUR-CONTACT-EMAIL` with the email you'll use for the app.
2. Merge this branch into `main`, then in GitHub go to **Settings → Pages → Deploy from a branch → `main` / root → Save**.
3. Your policy URL will be `https://reshpow-rgb.github.io/forcast/alarm-app/privacy-policy.html`.
   Open it on your phone to check it works.

---

## Part 2: Set up the money side

### 2.1 AdMob ads
1. In AdMob: **Apps → Add app → Android → "No, it's not listed yet"**. Name it RiseUp.
2. Copy the **App ID** (looks like `ca-app-pub-1234…~5678…`).
3. **Ad units → Add ad unit → Banner**, name it "Home banner". Copy the **Ad unit ID** (`ca-app-pub-1234…/9876…`).
   The app uses this unit for both the home-screen banner and the "Good morning" box.
4. Put both IDs in `alarm-app/gradle.properties`:
   ```
   ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
   ADMOB_BANNER_ID=ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz
   ```
5. **Privacy & messaging → GDPR → Create message**, select RiseUp, and publish. The app already shows it
   to users in Europe and the UK.
6. **Never tap your own real ads.** AdMob bans accounts for this. To test safely, add your phone under
   **Settings → Test devices**.
7. After the app is live on Play, go back to **Apps → App settings → Link to app store**.

AdMob pays monthly once you've earned at least about $100 (the threshold depends on your currency).

### 2.2 Premium (one-time purchase)
1. Play Console → **Setup → Payments profile**: create a merchant profile (bank and tax details). Without it you can't sell anything.
2. Enrol in the **15% service fee tier**: search "service fee" in Play Console's help, then follow the
   steps (it involves creating an "account group"). Google then takes 15% instead of 30% on your first
   $1M a year. It's free and takes a couple of minutes.
3. You can only create products after uploading a build (Part 3). After that: your app →
   **Monetize with Play → Products → One-time products → Create**:
   - Product ID: **`premium_unlock`**. This must match exactly.
   - Name: "RiseUp Premium". Description: "No ads, unlimited alarms, Hard missions, up to 5 rounds."
   - Price: $2.99–$4.99 is typical. Play converts it into local prices.
   - **Activate** it.
4. **Settings → License testing**: add your Gmail address. Your test purchases are then free and never charged.

---

## Part 3: Build the file you upload (`.aab`)

Easiest with **Android Studio** (free, Windows/Mac): <https://developer.android.com/studio>

1. Download this branch as a ZIP:
   <https://github.com/reshpow-rgb/forcast/archive/refs/heads/ccr-9168557d-m691c3.zip> and unzip it.
2. Android Studio → **Open** → choose the `alarm-app` folder → wait for "Gradle sync" to finish.
3. **Build → Generate Signed App Bundle or APK → Android App Bundle → Next**.
4. **Create new…** keystore. This is your *upload key*.
   - Save it **outside** the project folder, e.g. `Documents/riseup-upload.jks`.
   - Use a strong password and the alias `upload`. Validity: 25 years.
   - **Back it up** (cloud drive and password manager). With Play App Signing a lost key can be reset by
     Google support, but it takes days.
5. Choose **release → Create**. You'll get `alarm-app/app/release/app-release.aab`.

For every later update, increase `versionCode` (1 → 2 → 3…) and `versionName` in
`app/build.gradle.kts` before building. Play rejects a repeated `versionCode`.

> No computer? Claude can set up GitHub to build the signed file for you using encrypted GitHub
> Secrets instead. Just ask.

---

## Part 4: Play Console, step by step

### 4.1 Create the app
**Create app** → name "RiseUp", default language, **App**, **Free** (in-app purchases are still allowed), accept the declarations.

### 4.2 "Set up your app" checklist (Dashboard)
| Item | What to enter |
|---|---|
| Privacy policy | Your GitHub Pages URL from 1.3 |
| App access | All functionality available without special access |
| Ads | **Yes, my app contains ads** |
| Content rating | Fill in the questionnaire: category **Utility**; no violence, no user content. Usually rated Everyone/PEGI 3 |
| Target audience | **13+** (or 18+). Don't include under-13 age groups: that brings in the Families policy and restricts ads. Answer the "appeals to children" question honestly; the cute animals are fine for a general-audience app |
| News app | No |
| Data safety | See 4.3 |
| Government app / Financial features / Health | No / none / none |
| Advertising ID | **Yes**, used for **Advertising or marketing** (AdMob) |
| Store settings | Category **Tools** (or Lifestyle). Add your contact email |

### 4.3 Data safety form
- Does your app collect or share user data? **Yes** (because of AdMob).
- Is all data encrypted in transit? **Yes**. Can users request deletion? **No** (nothing is stored on a server; uninstalling deletes local data).
- Declare these as **collected and shared**, purpose **Advertising or marketing, Analytics, Fraud prevention/security**, not required (ads can be personalised or not):
  - **Location → Approximate location** (from IP address)
  - **App activity → App interactions**
  - **App info and performance → Crash logs, Diagnostics**
  - **Device or other IDs**

  Double-check against Google's own list for the ads SDK:
  <https://developers.google.com/admob/android/privacy/play-data-disclosure>
- Alarms, streaks and settings stay on the phone and are **not** "collected", so don't declare them.

### 4.4 Permission declarations (Policy → App content)
RiseUp uses permissions Google restricts to alarm-clock apps. You'll be asked about them after uploading:
- **Exact alarms (`USE_EXACT_ALARM`)**: choose **Alarm clock**. "RiseUp is an alarm clock; alarms must ring at the exact time the user set."
- **Full-screen intent**: choose **Alarm**. "Shows the ringing alarm and wake-up mission over the lock screen."
- **Foreground service: media playback**: "Plays the alarm sound until the user completes the wake-up mission."
  It asks for a **video**: screen-record your phone setting an alarm, the alarm ringing, and finishing
  the mission. Upload it to YouTube as **Unlisted** and paste the link.

### 4.5 Store listing (Grow → Store presence → Main store listing)
- **App icon**: `alarm-app/store/icon-512.png`
- **Feature graphic**: `alarm-app/store/feature-graphic.png`
- **Phone screenshots** (at least 2, ideally 4–6). Take them on your phone: the home screen with streak,
  the editor with the buddy picker, an animal asleep on the ringing screen, a maths mission, and the
  "Good morning" screen.
- **Short description** (max 80 characters):
  `Mission alarm clock: solve, type & wake up with a cute buddy. Build a streak!`
- **Full description** (suggestion):
  ```
  Can't stop hitting snooze? RiseUp won't turn off until you're actually awake.

  WAKE-UP MISSIONS
  • Solve a maths problem
  • Re-type a motivational quote
  • Re-type a list of words
  Easy, Normal or Hard, with up to 5 rounds.

  A BUDDY WHO WAKES UP WITH YOU
  Pick a corgi, kitten, baby giraffe, baby elephant, marmot, otters or a bluebird. They sleep while
  your alarm rings and wake up, yawning and stretching, as you complete your mission.

  STREAKS
  Every morning you complete your mission grows your streak. Days without an alarm never break it.
  Hit milestones at 3, 7, 30, 100 and 365 days.

  MAKE IT YOURS
  • 7 accent colours and Black, Midnight or Light themes
  • Built-in sounds, your phone's tones, or any song you love
  • Rings on time, even on the lock screen and after a restart

  RiseUp Premium (one-time purchase): no ads, unlimited alarms, Hard missions and up to 5 rounds.
  ```

### 4.6 Closed test: 12 testers for 14 days (personal accounts)
1. **Test and release → Testing → Closed testing → Create track**. Upload your `.aab`.
2. **Testers**: create an email list with **at least 12 people** (aim for 15–20 in case some drop out).
   Friends and family with Android phones are ideal. Their Gmail must be the one used on their phone's Play Store.
3. Share the **opt-in link** with them. They must tap "Become a tester", install the app and keep it
   installed for the full **14 days**.
4. Meanwhile, create the `premium_unlock` product (2.2) and try buying Premium yourself (it's free as a licence tester).
5. After 14 days: **Dashboard → Apply for production**. Answer the questions about your testing.

### 4.7 Go live
**Production → Create new release** → upload the latest `.aab` → choose countries (all, or start with a
few) → **Review release → Start rollout**. Google usually reviews it within a few days, sometimes longer for new accounts.

---

## Part 5: After launch: growing revenue
- **Reviews drive installs.** Reply to every review in Play Console. A later version can add Google's in-app review prompt after a 7-day streak.
- **Watch the numbers.** AdMob shows earnings per day. Play Console shows installs, uninstalls and Premium sales.
- **Ideas that tend to earn more**, all ready to build:
  - Make some animals and colours Premium-only, or unlock animals with streak milestones
  - A "streak freeze" purchase
  - An optional rewarded ad ("watch an ad to unlock a Hard mission today")
  - A yearly subscription instead of, or alongside, the one-time purchase
- **Translate the store listing** (Play Console can auto-translate it) to reach more countries.
