# Privacy

Glance shows on the lock screen what the apps that take part hand it and, if you turn it on, how
many notifications the apps you choose have waiting. On a phone without Datebook it can read
today's events itself, if you turn that on. It has no network access and sends nothing anywhere.

## What it reads

- **The lines the apps hand it.** Field Kit, Datebook, Medicine, Sky, Memo, Wallet, Music Box,
  Audio Reader, Bitcoin, Messaging and Mailbox each keep a small provider that answers Glance alone: the
  emergency card's ticked fields, today's event titles and times, the next dose, the weather,
  pinned notes, the names of today's tickets, what is playing, the bitcoin price, and two unread counts - never a
  message, a sender or a subject. Each app hands over nothing while its own lock-screen switch
  is off.
- **Any other app that offers lines the same way**, found by its own provider: it is listed in
  Glance's apps, off until you switch it on, and read only then.
- **Today's events, without Datebook - only if you turn it on.** On a phone without
  [Datebook](https://github.com/wanderwildwood/koyomi), **Today's events** (off until you turn it
  on) reads what is left of today, titles and times only. From Mudita's own Calendar, which
  answers any app that asks, so this needs no permission. And from the phone's other calendars
  (DAVx5, Etar, anything that syncs), which need calendar access: Glance asks for it when you
  turn the switch on, and you can refuse and keep Mudita's alone. It reads only today, and only
  calendars set to be shown. The code is `app/src/main/kotlin/com/wanderwildwood/hitome/Today.kt`.
- **The lock screen itself**, through the accessibility service: only where its own items end,
  so the panel can sit under them, and whether the PIN pad is up, so the panel can stand aside.
  The service listens to the system UI alone (`app/src/main/res/xml/glance_service.xml`) and
  reads nothing from any other app, except that with Katapult it looks at where Katapult's
  lock-screen widgets sit, their position only, to fit around them.
- **Whether DuraSpeed is on the phone** (a Kompakt's background manager), only to show the
  **Open DuraSpeed** row in Settings, which opens DuraSpeed's App info page when you press it.
- **Other apps' notifications, counted - only if you turn it on.** This is Android's notification
  access, off until you switch it on for Glance, and it is the one place Glance could see more
  than it shows: Android gives notification access to every notification. Glance uses it to
  count, and only for the apps you chose in Glance - with none chosen it counts nothing. It reads
  a notification's title and text only if you turn on **Show what they say** (off until you do),
  and then only the newest from each chosen app, to put on the lock screen. Where Android would
  hide a notification's content on the lock screen - the phone set to hide private content, and
  the notification or its channel marked private - Glance shows its public version or nothing
  but the count. With the phone set to show no notifications on the lock screen at all, Glance
  counts none and shows none of their words; set to hide silent ones there, Glance leaves the
  silent ones out the same way. Left uncounted: ongoing ones (a player, a download), a
  group's summary, and anything the app or you have marked secret on the lock screen. Datebook,
  Sky, Messaging and Mailbox are never counted this way; they have their own place on the panel.
  With **Show what they say** on, Messaging's and Mailbox's newest notification is read too, for
  its words only, under the same rules. The code is
  `app/src/main/kotlin/com/wanderwildwood/hitome/Notices.kt`.
- **Do Not Disturb, only if you turn on Ring for calls only.** Glance then adds one Do Not
  Disturb rule of its own (calls from anyone and alarms ring, nothing is hidden) and removes it
  when you turn it off. It reads nothing through it. The code is
  `app/src/main/kotlin/com/wanderwildwood/hitome/Quiet.kt`.
- **Do Not Disturb on a schedule, only if you turn on Quiet hours.** Glance adds a second Do
  Not Disturb rule of its own (alarms ring, media plays when you play it, and a second call from
  the same number within 15 minutes if you leave that on; nothing is hidden), turns it on and off
  at the times you set with an alarm, and removes it when you turn Quiet hours off. It keeps the
  times and days you set, and nothing about calls. The code is
  `app/src/main/kotlin/com/wanderwildwood/hitome/QuietHours.kt`.
- **The torch, only if you turn on Power pressed twice.** Glance notes when the screen goes on
  and off, keeps nothing of it, and switches the torch. It does not open the camera. The code is
  `app/src/main/kotlin/com/wanderwildwood/hitome/Torch.kt`.
- **Home's dark switch**, so Glance and its panel can follow it. Glance asks Home
  (`com.wanderwildwood.iori`), and only Home's own release, whether that switch is on and when it
  was last pressed: while Glance's settings are open, as the screen comes on, and when Home says
  the switch was pressed. With no such Home on the phone it asks nothing. The code is `app/src/main/kotlin/com/wanderwildwood/hitome/HomeDark.kt`
  and `app/src/main/kotlin/com/wanderwildwood/hitome/ui/DarkMode.kt`.

## What anyone can see

The panel is on the lock screen, so whoever holds the phone can read it without unlocking it:
today's event titles among them, which chosen apps have something waiting, and - if you turn it
on - what their newest notifications say. Each app's switch,
and the list of apps you choose, is there for exactly this. Pressing the panel
asks for the phone's lock before opening anything, so a PIN still protects what is behind it.

## What it hands on

Glance hands the panel's lines to one app only, Home (`com.wanderwildwood.iori`), to draw
on its home screen, and only when that app is signed with Home's own release certificate:
the same lines from the apps that take part, in your order and with your switches here, and for
each app whether it shows and why (on, off in the app, off in Glance, not installed, needs an
update, did not answer); and, when today's events are on here on a phone without Datebook, the
events Glance reads from the phone's calendars. Never other apps' notifications. Any other app that asks gets nothing.
The code is `app/src/main/kotlin/com/wanderwildwood/hitome/PanelForHome.kt`.

## What it stores

Your choices: the apps, their order, the text size, its switches (what notifications say,
today's events, the torch, Ring for calls only, Quiet hours and its times and days, whether
repeat callers ring, dark mode), what Home's dark switch last said and when, and nothing else:
no history and no copy of what it shows.

## Checking for yourself

```
aapt2 dump badging hitome.apk | grep uses-permission
```

prints five lines. `android.permission.READ_CALENDAR` is calendar access, asked for only when you
turn on **Today's events** on a phone without Datebook, and never otherwise.
`android.permission.ACCESS_NOTIFICATION_POLICY` lets Glance set its own Do Not Disturb rules
(Ring for calls only, Quiet hours) once notification access is on.
`android.permission.SCHEDULE_EXACT_ALARM` and `android.permission.RECEIVE_BOOT_COMPLETED` let
Quiet hours begin and end on time while the phone sleeps, and again after a restart; with Quiet
hours off, Glance sets no alarm and does nothing when the phone starts.
`com.wanderwildwood.hitome.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is not a permission over
anything on the phone: AndroidX declares it inside every app built against it, so that the app's
own internal broadcasts cannot be sent by anyone else, and it names Glance itself. Notification
access is not a permission an app asks for; it is a switch in Android's settings that you turn on
or leave off.

<!-- shared-about privacy section -->
## Who we are

Glance is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Glance keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Glance is sold or shared.

Since Glance keeps what it stores only on your phone, you can exercise most of these rights yourself: change or switch off what it shows in its own settings, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->
