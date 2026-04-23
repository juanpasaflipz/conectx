# Firebase Retention

This project now assumes Firebase Realtime Database data is cleaned up by a scheduled backend job rather than by clients.

## Policy

- Squad records older than 7 days are deleted.
- Invite records older than 30 days are deleted.
- Empty squad containers are removed when they have no `members` and no remaining `records`.

These values are intentionally conservative:

- 7 days keeps post-event replay/reconnect windows available without allowing `records` to grow without bound.
- 30 days keeps invite links alive long enough for pre-event planning without making stale invite buildup permanent.

## Backend

The scheduled cleanup function lives in [functions/src/index.ts](/Users/juan/Desktop/conectx/functions/src/index.ts).

It is configured in [firebase.json](/Users/juan/Desktop/conectx/firebase.json) and should be deployed with Firebase Functions.

## Deploy

From the repo root:

```sh
cd functions
npm install
npm run build
cd ..
firebase deploy --only functions,database
```

## Shared-client expectation

Both Android and iOS clients now:

- read a bounded recent RTDB window instead of full squad history
- rely on RTDB `members/{firebaseUid}` for access control
- write `firebaseUid` on each record so rules can verify the writer

That means the retention job is shared infrastructure for both mobile apps, not an Android-only or iOS-only cleanup path.
