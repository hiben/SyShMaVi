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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

public class TextureProperties {
	public final static int TEXTURE_PROPERTY_SIZE = 11;
	
	private Map<Integer, TextureProperty> texPropMap = new TreeMap<Integer, TextureProperty>();
	
	public TextureProperty getPropertyFor(int textureIndex) {
		return texPropMap.get(textureIndex);
	}
	
	public int getNumberOfProperties() {
		return texPropMap.size();
	}
	
	public TextureProperties(ByteBuffer bb) {
		int texIndex = 0;
		bb.position(bb.position()+4);
		while(bb.limit() - bb.position() > TEXTURE_PROPERTY_SIZE) {
			texPropMap.put(texIndex++, new TextureProperty(bb)); 
		}
	}
	
	public static class TextureProperty {
		private byte head1;
		private byte head2;
		private int stuff;
		private boolean isClimbable;
		private byte z0;
		private boolean isStarfield;
		private byte animationGroup;
		private byte animationIndex;
		
		public TextureProperty(ByteBuffer bb) {
			head1 = bb.get();
			head2 = bb.get();
			stuff = bb.getInt();
			isClimbable = bb.get()==(byte)1;
			z0 = bb.get();
			isStarfield = bb.get()==(byte)1;
			animationGroup = bb.get();
			animationIndex = bb.get();
		}

		public byte getHead1() {
			return head1;
		}
		public byte getHead2() {
			return head2;
		}
		public int getStuff() {
			return stuff;
		}
		public boolean isClimbable() {
			return isClimbable;
		}
		public byte getZ0() {
			return z0;
		}
		public boolean isStarfield() {
			return isStarfield;
		}
		public byte getAnimationGroup() {
			return animationGroup;
		}
		public byte getAnimationIndex() {
			return animationIndex;
		}
		
		public String toString() {
			return String.format("AG:%1$d AI:%2$d S:%3$d (%3$08X)%4$s%5$s", animationGroup, animationIndex, stuff, isStarfield?" starfield":"", isClimbable?" climbable":"");
		}
		
		public String rawString() {
			return String.format("%02X%02X%08X%02X%02X%02X%02X%02X"
					, head1, head2, stuff, isClimbable?1:0, z0, isStarfield?1:0, animationGroup, animationIndex 
					);
		}
	}
}
