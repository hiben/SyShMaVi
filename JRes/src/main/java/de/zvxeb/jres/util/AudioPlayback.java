/*
    Copyright 2008-2022 Hendrik Iben

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.package com.github.hiben;
*/
package de.zvxeb.jres.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioPlayback extends Thread {
	
	SourceDataLine sdl;
	private AudioFormat af;
	private byte [] data;
	
	private boolean cancelPlayback = false;
	private boolean playbackFinished = false;
	
	public AudioPlayback(AudioFormat af, byte [] data) throws LineUnavailableException {
		setDaemon(true);
		sdl = AudioSystem.getSourceDataLine(af);
		this.data = data;
		this.af = af;
	}
	
	public void cancelPlayback() {
		cancelPlayback = true;
	}
	
	public boolean isPlaybackFinished() {
		return playbackFinished;
	}
	
	public void run() {
		if(cancelPlayback)
			return;
		
		try {
			sdl.open(af);
			sdl.start();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			return;
		}
		
		int written = 0;
		int remaining = data.length;
		
		while(remaining > 0 && !cancelPlayback) {
			int r = sdl.write(data, written, remaining);
			if(r == 0) {
				System.err.println("...playback error! Zero bytes played!");
				break;
			}
			remaining -= r;
			written += r;
		}
		
		sdl.close();
		playbackFinished = true;
	}

}
