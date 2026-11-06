# MTG Reforged — Privacy Policy

_Last updated: 2026-11-07_

MTG Reforged is a free, open-source Magic: The Gathering app for Android. It has no accounts
and no ads, and the developer only receives personal data if you accept sharing your account (it's
needed for sync and Google Drive backups) or send a bug report. Otherwise the only thing it reports is an anonymous install count (see below).

## What stays on your device
Your decks, card collection, saved matches, profile name and avatars are stored only on your
device.

## Google Drive backups (optional)
If you choose **Back up to Google Drive**, the app asks Google for permission to store its own
backup files in your Drive. It can only see and manage the files it creates; it cannot read any
other file in your Drive. Backups contain your decks and card collection and are stored in your
own Google account. The developer has no access to them. You can revoke the permission at any
time at https://myaccount.google.com/permissions and delete the backups from your Drive.

## Network requests
The app downloads card images and data from Scryfall, card prices from Card Kingdom, updates and
news from this GitHub repository, and talks to Google Drive only when you back up or restore.
These services receive standard request information (such as your IP address) under their own
privacy policies.

## Anonymous install count
At most once a day the app sends the project's own server three things: a random ID created by
the app when it first runs, the platform (Android or Windows) and the app version. The ID is not
linked to you, your device, your Google account or your decks, and it's used only to count how
many installs are active and which versions they run. The server also records the country the
request came from, which Cloudflare works out from your IP address. It doesn't store the IP address.
Uninstalling the app deletes the ID from your device.

## Sharing your account (needed for sync and Google Drive backups)
To use sync and Google Drive backups you must accept sharing your Google name and email address, the
app version and your Android or Windows version with the developers. They will not be shared with
anyone. They're used only for practical purposes: fixing errors, improving your experience with the
app and contacting you about them. They're linked to
your install, so the developers can see the details of the anonymous install count for your device.
If you don't accept, the rest of the app works the same, including backups saved to a file. You can
change your mind at any time in Backup & restore: turning it off deletes these details from the
project's server and turns off sync and Google Drive backups.

## Bug reports (optional)
If you choose **Report a bug**, the app needs your Google account connected. Your report goes to
the developer with your Google name and email address, your platform and the app version, so the
developer can answer you. It's delivered through Telegram. To confirm who is sending it, the
project's server briefly uses the app's Google access token, without storing it. The server keeps a
scrambled (hashed) form of your email for one day, only to limit how many reports can be sent.

## Contact
Questions: open an issue at https://github.com/nmanuelides/mtg-reforged/issues
