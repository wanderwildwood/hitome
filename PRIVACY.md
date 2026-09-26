# Privacy

Glance shows on the lock screen what four apps on the phone hand it. It has no network access,
no permissions, and sends nothing anywhere.

## What it reads

- **The lines the apps hand it.** Calendar, Sky, Messaging and Email each keep a small provider
  that answers Glance alone. Today's event titles and times, the weather, and two unread
  counts - never a message, a sender or a subject. Each app hands over nothing while its own
  lock-screen switch is off.
- **The lock screen itself**, through the accessibility service: only where its own items end,
  so the panel can sit under them, and whether the PIN pad is up, so the panel can stand aside.
  The service listens to the system UI alone (`app/src/main/res/xml/glance_service.xml`) and
  reads nothing from any other app.

## What anyone can see

The panel is on the lock screen, so whoever holds the phone can read it without unlocking it:
today's event titles among them. Each app's switch is there for exactly this. Pressing the panel
asks for the phone's lock before opening anything, so a PIN still protects what is behind it.

## What it stores

Nothing. It keeps no settings, no history and no copy of what it shows.

## Checking for yourself

```
aapt2 dump badging hitome.apk | grep uses-permission
```

prints nothing: there are no permissions to print.
