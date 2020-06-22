# Changelog - Aesop Player

## General Notes

The oldest changelog entry describes the overall differences between Aesop Player and
its predecessor Homer Player.

I am an "older gentleman". Although I expect to be working on Aesop
for some time to come, it's always possible I suddenly won't be able to do so. If that is
the case, I don't want Aesop to be orphaned if there's someone willing to work on it.
If you are unable to contact me at aesopPlayer@gmail.com and there's no recent activity,
it may be I can no longer work on it. 
If that's the case, and you are willing, please, 
adopt it - take a fork and fix it as needed. I'll contact you if and when I can.
(Be sure to look for, and contact the owner of, any forks that already exist.)

If I'm still working on it, I'll consider all collaboration suggestions seriously.

## Version 1.0.0

This version differs from the Homer Player by Marcin Simonides in a number of ways.

The changes fall into a few groups. The user interface changes were driven
by actual use: testing showed that the buttons were too small for some users to see,
and some users triggered multi-tap accidentally.
* The user interface is changed so the start/stop buttons are nearly full screen.
* The multi-tap anywhere to enter settings is changed to the gear icon multitap/multi-press.
* More status information is shown on the New Books and Playback screens (for caregivers.) 
* Flip to restart.
* Speed and volume setting by dragging or tipping with audio feedback.

A separate group of changes are made for the caregiver in terms of maintaining books 
and generally supporting the User.
* The ability to add and delete books without using a PC, either from the internet, 
On-the-go USB media, or removable SD. Change titles and book grouping.
* Explicit planning for remote access to be able to do the above without touching the 
device.
* Maintenance Mode (and remembering the Kiosk Mode.)
* Make selection of Kiosk mode a radio-button choice rather than bits and pieces.
* Support of Application Pinning and Full Kiosk as distinct modes.
* Ability to set book position anywhere/bulk reset.
* Use audio file metadata where available to provide titles automatically.

Technical changes.
* Increasing the robustness of Simple Kiosk Mode.
* Increasing the robustness of the UI finite state machine in support of 
  face-down-pause / face-up-resume.
* Various bug fixes and (inspection-driven) code cleanup.
* Rework of the motion detector for robustness, flip to restart, and tipping.
* Support for both public and private AudioBooks directories on each physical drive.

Large bodies of the code remain unmodified (or nearly so) from the original.

## Version 1.0.1
Fix rare bug, improve logging in samples downloader.

## Version 1.1.0
Add *two-finger* swipe in playback window to move back (or forward) to recent "stop points".
Stop points are created when the player is stopped (or paused). The few closest  
to the end of the book are kept, and are always at least one minute apart (duplications ignored).
This allows easy backup should the user fall asleep. More details on the web page.

On Current Books page, don't suggest removing a book that has been completed and then restarted.

Add a High Contrast color theme and switch to enable it.
Lots of inspection-related cleanup on the otherwise-changed files, bug fix
to PositionEdit when stop list is empty.

Bug fix so that the hint for swiping to change books is visibly displayed.
(Don't record that it was displayed until the user actually dismisses it.)

Technical changes to the player and metadata reader to widen the range of books that can
be successfully played (and/or title displayed).

When the version changes significantly, notify the user of the
existence of the change with a pointer to the web page.
The major and minor parts of the version (vx.y) are considered
significant. The revision is not involved in the comparison, but  if
there's a tag part (as from git describe) it does affect things.

The last item in the strings.xml file is a string
summarizing the changes (just a short name) that should be updated
for each new release.

Web page only: add note about fre:ac for easy ripping.

## Version 1.1.1
Fix problem with getting audio tag data to form titles (only affected
released versions... proGuard dependency issue.)

## Version 1.1.2
Fix problem with hang if the computation of the book size hasn't
finished before the book is started.

## Version 1.1.3
Fix a number of accessibility issues and some potential crashes shown by
automatic testing.

Add a hint on the SECOND time playback is started about the existence of
the on-screen volume and speed controls.

Don't do "Button Delay" at startup and when returning from Settings.

Make Simple Kiosk work on Android 10, although it's not recommended because
it still is (unfixably) ugly. Make App Pinning more attractive by getting
rid of the "Got It" message on Pie and above. Pinning (where available) is the
best choice if not using Full Kiosk. Update web page to reflect these
changes.

Fix URLs in dialogs to be clickable. Housekeeping in handling the
path to the website. Improve error handling if connection to Samples
Download website fails.

Fix unzip of books when zip file doesn't contain explicit directory entry.
Handle file renumbering when more than 100 files, separate handling of
audio files and directories to avoid renumbering unnecessarily.

## Version 1.1.4

Keep a history of (unique) directories used as downloads dirs. Access
that thru a popup triggered by clicking the DownLoad dir item
in the Add Books page. Clicking on a history entry changes directly
to that entry. New entries are added from the popup, and entries
can be deleted from there by clicking on a delete item entry.

This goes with a web page change on how to install downloadable
books from OverDrive and rbDigital and the long path to the
directory OverDrive uses for that.

The emulator doesn't behave exactly the same as real hardware
when doing Application Pinning for API 21-27.
Get the change in 1.1.3 working for working for those APIs.

Fix low-contrast text when Title Edit cannot proceed due to an
illegal name.

Minor bugfixes.

## Version 1.1.5

Minor bugfixes.