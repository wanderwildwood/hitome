# Privacy

Glance shows on the lock screen what the apps that take part hand it and, if you turn it on, how
many notifications the apps you choose have waiting. On a phone without Calendar it can read
today's events itself, if you turn that on. It has no network access and sends nothing anywhere.

## What it reads

- **The lines the apps hand it.** Field Kit, Calendar, Medicine, Sky, Notes, Wallet, Music Box,
  Audio Reading, Messaging and Email each keep a small provider that answers Glance alone: the
  emergency card's ticked fields, today's event titles and times, the next dose, the weather,
  pinned notes, the names of today's tickets, what is playing, and two unread counts - never a
  message, a sender or a subject. Each app hands over nothing while its own lock-screen switch
  is off.
- **Today's events, without Calendar - only if you turn it on.** On a phone without
  [Calendar](https://github.com/wanderwildwood/koyomi), **Today's events** (off until you turn it
  on) reads what is left of today, titles and times only. From Mudita's own Calendar, which
  answers any app that asks, so this needs no permission. And from the phone's other calendars
  (DAVx5, Etar, anything that syncs), which need calendar access: Glance asks for it when you
  turn the switch on, and you can refuse and keep Mudita's alone. It reads only today, and only
  calendars set to be shown. The code is `app/src/main/kotlin/com/wanderwildwood/hitome/Today.kt`.
- **The lock screen itself**, through the accessibility service: only where its own items end,
  so the panel can sit under them, and whether the PIN pad is up, so the panel can stand aside.
  The service listens to the system UI alone (`app/src/main/res/xml/glance_service.xml`) and
  reads nothing from any other app.
- **Other apps' notifications, counted - only if you turn it on.** This is Android's notification
  access, off until you switch it on for Glance, and it is the one place Glance could see more
  than it shows: Android gives notification access to every notification. Glance uses it to
  count, and only for the apps you chose in Glance - with none chosen it counts nothing. It reads
  a notification's title and text only if you turn on **Show what they say** (off until you do),
  and then only the newest from each chosen app, to put on the lock screen. Where Android would
  hide a notification's content on the lock screen - the phone set to hide private content, and
  the notification or its channel marked private - Glance shows its public version or nothing
  but the count. Left uncounted: ongoing ones (a player, a download), a
  group's summary, and anything the app or you have marked secret on the lock screen. Calendar,
  Sky, Messaging and Email are never counted this way; they have their own place on the panel.
  With **Show what they say** on, Messaging's and Email's newest notification is read too, for
  its words only, under the same rules. The code is
  `app/src/main/kotlin/com/wanderwildwood/hitome/Notices.kt`.
- **Do Not Disturb, only if you turn on Ring for calls only.** Glance then adds one Do Not
  Disturb rule of its own (calls from anyone and alarms ring, nothing is hidden) and removes it
  when you turn it off. It reads nothing through it. The code is
  `app/src/main/kotlin/com/wanderwildwood/hitome/Quiet.kt`.

## What anyone can see

The panel is on the lock screen, so whoever holds the phone can read it without unlocking it:
today's event titles among them, which chosen apps have something waiting, and - if you turn it
on - what their newest notifications say. Each app's switch,
and the list of apps you choose, is there for exactly this. Pressing the panel
asks for the phone's lock before opening anything, so a PIN still protects what is behind it.

## What it stores

The list of apps you chose, whether to show what they say, and whether to read today's events,
and nothing else: no history and no copy of what it shows.

## Checking for yourself

```
aapt2 dump badging hitome.apk | grep uses-permission
```

prints two lines. `android.permission.READ_CALENDAR` is calendar access, asked for only when you
turn on **Today's events** on a phone without Calendar, and never otherwise.
`com.wanderwildwood.hitome.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is not a permission over
anything on the phone: AndroidX declares it inside every app built against it, so that the app's
own internal broadcasts cannot be sent by anyone else, and it names Glance itself. Notification
access is not a permission an app asks for; it is a switch in Android's settings that you turn on
or leave off.
