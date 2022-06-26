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
package de.zvxeb.jres;

import java.util.Map;
import java.util.TreeMap;

public class SSSprite {
	public static final short spriteBaseChunk = 1350;
	
	private static Map<Integer, ResBitmap> spriteCache;
	
	private SSSprite() { };
	
	static {
		spriteCache = new TreeMap<Integer, ResBitmap>();
	}
	
	public static void clearSpriteCache() {
		spriteCache.clear();
	}
	
	public static ResBitmap getSprite(ResManager rm, int spriteNum) {
		ResBitmap sprite = spriteCache.get(Integer.valueOf(spriteNum));
		if(sprite==null) {
			byte [] data = rm.getData(spriteBaseChunk, spriteNum);
			
			if(data!=null) {
				sprite = new ResBitmap(data, spriteBaseChunk, spriteNum);
				spriteCache.put(Integer.valueOf(spriteNum), sprite);
			}
		}
		
		return sprite;
	}
}
