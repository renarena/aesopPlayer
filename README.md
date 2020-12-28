Aesop Player 
============
An audio book player for the elderly and visually impaired, or for those
who just want a very simple way to play audio books.

[Go to the project website](http://donnKey.github.io/aesopPlayer/)
or
[watch the video](https://www.youtube.com/watch?v=RfLkoLtxzng).

Project Goal
------------
Turn a regular Android tablet into a dedicated audio book player that can be
easily used, particularly by the visually impaired and the elderly.

Assumptions
-----------
* focused on audio book playback, not music,
* controlled with imprecise gestures and subject to accidental touch,
* the user isn't able to make out letters or small UI controls,
* device can (optionally) be dedicated to a single function (runs in kiosk-like mode, no access to
  other applications).

Status
------
The main functionality has been implemented and the app is available in the
[Play Store](https://play.google.com/store/apps/details?id=github.io.donnKey.aesopPlayer).

See the [website](http://donnKey.github.io/aesopPlayer/features.html) for details
on the main features.

Expert Installation (Particularly QR Mode)
------------------------------------------
The documentation on the website is intended non-expert users for whom
the default installation procedures using Play Store are appropriate.
Kiosk Mode installation is difficult enough to explain clearly because of the
(justifiable, but nevertheless complicating) policies from Google.
There are potential users who would prefer not to use Play Store.
We'll presume that those users do not need detailed explanations,
but provide a quick guide to what's going on and why.

You can install Aesop Player directly from GitHub: we publish apk files
(and tarballs)
for each release. There's nothing special about those files, they
can be installed directly.

If you wish to use QR mode installation,
you can to so by modifying the qr-provisioning.json file to point to the
appropriate version here on GitHub and regenerating the QR image.
(Change the `...DOWNLOAD_LOCATION` entry to point to an Aesop .apk file.
The (multi-line) entry `...EXTRAS_BUNDLE` isn't needed (and *probably*
ignored).)

The web page [appspot.com](http://down-box.appsopt.com)
is handy for creating the QR code,
but any of several others on the Web will do.
(Right click and download the image to save the .png.)
(Downbox is simple, but not very secure, depending on your content.)
(Recently I've noted something that appears to be phishing on the
appspot website - there's a redirect that looks highly suspicious.)

Aesop would then be installed as the device
owner (exactly as occurs if you use adb to set it as device owner).
Note that this will not allow automatic updates: you will be responsible
for checking for and installing any new versions, should it matter to you.
(Play store will not update an application it did not install itself,
even if it's "the same" application.)

The procedure for typical users on the website is technically somewhat different, because
Google doesn't (yet?) allow QR mode installations from Play Store. Rather
than directly installing Aesop Player, a small intermediate application is
installed (from GitHub) as the device owner, and it starts the Play Store
application to install Aesop. It also gives Aesop the "Lock Screen" permission,
(which is what Aesop really is looking for when it is device owner),
and retains device owner itself. That application will sit on the
device, unused, after that.  (The application is DonnKey/KioskInstaller
on GitHub, and you can see from the source what it does, and how it deals
with attempts to run it.) This allows automatic updates of Aesop from Play
Store.


Contributions
-------------
The original Homer Player by Marcin Simonides is the basis for this application.

Contact
-------
aesopPlayer@gmail.com

License
-------
Copyright (c) 2015-2017 Marcin Simonides Licensed under the MIT license.  
Copyright (c) 2018-2020 Donn Terry Licensed under the MIT license.  
A copy of that license may be found in the file LICENSE.

Other Licenses
--------------

A list of all packages used and their associated licenses may be found at
[this location](http://donnKey.github.io/aesopPlayer/licenses.html).
