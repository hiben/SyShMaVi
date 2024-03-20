# SyShMaVi - System Shock Map Viewer
This is an *incomplete* project with the goal to view resources and maps from the game 'System Shock'.

The main part of this code was written in 2008 but never made public. My interest in this was revived a bit in 2021/22 because of the
soon (?) release of the System Shock remake by *Nightdive* (I'm a backer).

*Edit:* The remake was released in the meantime - and I mostly like what was delivered.
Waiting for patch 1.2 gives me some time to think about this project.

The code is at its core a Java port of TSSHP - The System Shock Hack Project. It is used for reading resources and was the base of the 3d
rendering code. The 3d rendering part uses JOGL and is therefore quite different.

I'm putting this code on GitHub to preserve it.

*Note:* This code is very old and was written originally for Java 1.4. It also not very easy to read and not very *professional* :-)

Changes for release:
- moved to Gradle build system (was an Eclipse project)
- Update to JOGL 2 for Maven dependency support
- Changed package name to *de.zvxeb* (was *org.hiben*)
- Some tweaks with initial visual settings
- Added a license

## Changelog
Keeping track of changes after releasing on github.
- **20.03.2024**
  - Code cleanup / Refactoring
  - Updated readme :-)

- **24.09.2022**
  - Added a simple console / replaced 'cheats' with console commands

    '^' key (dead-circumflex) will open a simple console where you can type
    commands. Key needs to be adjusted for other keyboard layouts (todo)

    Debug commands (see below) are now entered via the console.

    Open console disables mouse look.

  - The internal key handling is switched back to AWT (from NEWT) since this 
    better integrates with Java components.

- **07.09.2022**

  - Initial mouse-look
  
    on by default, while window is focused. Toggle with TAB (no mouse-grab while showing map)

- **05.07.2022**

  - Command line parameters not required anymore. UI popup for setup if needed.
 
  - Level change now keeps old window.

  - Added configuration options via files and properties.
  
    **$HOME/.syshmavi/config.properties** 
    
    Can be used for properties, or override via command line (*-D*):

    - **dataPath**: path to System Shock data directory (e.g. the CD), if not set, will be asked on running
  
      (or via **SS1_DATA_PATH** environment variable)

    - **map**: map to load (0-15), if not set selection box will show

    - **configPath**: alternative path to config file
    
      (or via **CONFIG_PATH** environment variable)
    - **saveVisInfo**/**loadVisInfo**: save and load computed visibility (not saving by default, little impact on modern machines, saves to config path)

## License

I'm putting this under the Apache 2.0 license. See LICENSE file.

## Project Parts

- JKeyboard - a Keyboard abstraction for JOGL - nothing spectacular.
 
- JXMI - some parsing routines for the XMI music from the game. Mostly not useful...
  
  Could be used later to add music to the map viewer.

- JRes - a resource browser / movie and model viewer

  Can read the RES files from System Shock and show information.

  You need to load one of the palette files first to see images correctly

  3D model viewer can show you the assets used in the game - has keyboard navigation (no mouse view)

  Movie viewer can play videos (e.g. intro) and show subtitles. You can also save frames as images.

- SyShMaVi - the main act. Loads level geometry and allows keyboard navigation. Has mostly correct cyberspace coloring 
  and animates doors.

## Usage

Currently, this is more interesting for developers - just load it into an IDE with Gradle support (e.g. IDEA)

*JRes*, *JXMI* and *SyShMaVi* can be build via the application plugin, e.g. *gradlew SyShMaVi:installDist* to use it standalone.


- JKeyboard

  Is a library - used by JRes and SyShMaVi

- JXMI

  Main class: de.zvxeb.jxmi.JXMI

  Command line application.
  
  Parameters are MIDI files to play, e.g. from the GENMIDI folder.


- JRes

  Main class: de.zvxeb.jres.ui.JResBrowser

  Has a Swing UI - mostly intuitive

  Will load all RES files given as parameters

  
  Get started by loading *'GAMEPAL.RES'* from the **CDROM/DATA** folder, select a palette and then load *'TEXTURE.RES'* from the **HD/DATA** folder.

  Now you can browse the game textures, videos, texts and audio files (just load all the RES files).


- SyShMaVi

  Main class: de.zvxeb.syshmavi.Main

  Expects path to System Shock data as first parameter (where the CDROM and HD folders are)
  and optionally a number for the level to load (0-15, 1 (default) is healing suites)

  - Controls
  
    Movement (no mouse): WASD, Left/Right (turn), RFV (look up/center/down), Shift (run), Num +/- Level change, Tab: Map (sort of)
    
    Press Escape to exit.

  - 'Cheats'/Debug (type quickly)

    *fly/nofly*: free movement 

    *light/nolight*: level lighting

    *hide/nohide*: debug info

    *pick/nopick*: draw no wall textures (pick mode)


  You can move around freely in the level - there is no collision. The camera has a fixed height above the ground
  unless you enable *fly* mode. Lighting uses the stored lightmaps from the game, you can disable that with *nolight*.

  To show debug info on sprites etc. enable the *nohide* mode. To see level geometry, enable *pick* mode (this was used in debugging the geometry creation).

  Pressing TAB will show a 'map' of the current level with your position and some information on visibility. This is used internally for optimization.

  Changing the level (Numpad +/-) will reset the view.

## Info & Images

Some info + images here:

https://zvxeb.de/oldshock/

## Background

Around 2008 I was still studying computer science and I had the idea of doing something with System Shock.

I was lurking in some of the forums and at some point got into 'TSSHP' - the 'The System Shock Hack Project' - that was written in C++ for Linux. After some issues getting it to run on my brand-new 64bit processor I became quite familiar with the sources but unfortunately I was (and I am) a Java guy...
So my mission was clear: port over the code to Java using JOGL (Java Open GL) for rendering.

Many months passed and I was making good progress with the resources fixing some bugs in the C++ code along the way and throwing out the special Ultima Underworld cases (because I could not test that). Sadly I was too shy to ever give anything back to the project even though I was quite proud to fix some issues with video decoding.

Anyway, after some time I had made 'JRes'; a Java Browser for System Shock Resource files.

This allowed my to get a good view on all the resource of the game and found some of the easter eggs I missed while playing (the Edward + Shodan heart got me by surprise :-D ).

But resource browsing was not enough: I needed 3D! So I set out to port over all the 3D stuff to Java - that was quite a journey and in the end I barely used any of the C++ code base but wrote my own little engine. It even supported 'cheat codes' for free flying and toggling various settings. SyShMaVi - the System Shock Map Viewer was born.

Initially it was just level geometry and 2D sprites. The 3D models have some clever structure for efficient rendering and I had to wrap my head around that (especially for cyberspace models...). But after some more work they were rendering quite nicely. This was also backported to the resource browser to view models standalone.

I also worked on a parser for the XMI music of the game (that was some fancy stuff allowing dynamic music by turning on and off tracks - so ahead of its time). The XMI stuff was working somewhat but I never included it in the project because it is not easy to control. Maybe it would be nice to have an infinite System Shock music box :-D

So where I am now with this?
Basically I do not know... since it is Java based it should still be working but it is really a niche interest group. I worked (on and off) for about 4 years on this. Java is not the best language for working with old-skool-3d and I do not have time to work on it anymore since I finished my studies and now I am a full time developer (for some time now...). I think it is just a nice reminder of how willing I was to work on this little project just for my love of System Shock :-) 