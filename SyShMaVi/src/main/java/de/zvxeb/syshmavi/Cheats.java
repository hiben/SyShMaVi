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
/*
 * Created on 20.01.2009
 */
package de.zvxeb.syshmavi;

import java.text.Collator;
import java.util.Map;
import java.util.TreeMap;

import de.zvxeb.jkeyboard.KeyReleaseListener;

class Cheats implements KeyReleaseListener
{
	private long timeout;
	private long lastKeyRelease;
	private StringBuilder currentSequence;

	private Map<String, Boolean> cheats;
	
	public Cheats()
	{
		timeout = 1000;
		Collator c = Collator.getInstance();
		c.setStrength(Collator.PRIMARY);
		cheats = new TreeMap<String, Boolean>(c);
		currentSequence = new StringBuilder();
	}
	
	public boolean activeCheat(String cheatName)
	{
		Boolean b = cheats.get(cheatName);
		
		if(b!=null)
		{
			return b.booleanValue();
		}
		
		return false;
	}
	
	public void setCheat(String cheatName, boolean state)
	{
		cheats.put(cheatName, Boolean.valueOf(state));
	}
	
	public void addCheat(String cheatName)
	{
		setCheat(cheatName, false);
	}

	@Override
	public void keyReleased(int keyid) {
		long curTime = System.currentTimeMillis();
		
		if( (curTime - lastKeyRelease) > timeout )
		{
			currentSequence.setLength(0);
		}
		
		lastKeyRelease = curTime;
		
		char c = (char)keyid;
		
		if(Character.isLetterOrDigit(c)) {
			currentSequence.append(c);
		} else {
			currentSequence.setLength(0);
		}
	
		String curString = currentSequence.toString();
		
		if(cheats.containsKey(curString))
		{
			cheats.put(curString, Boolean.valueOf(true));
			currentSequence.setLength(0);
		}
	}
	
}