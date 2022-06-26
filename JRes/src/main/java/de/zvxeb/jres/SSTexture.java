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

/*
 * Animated & Glowing palette indices
 * 13, 14, #1192 guess -> 12, 13, 14, 15
 * 16, 17, 18, 19, #1028 guess 16 17 18 19 [20 ?]
 * 30, 31, #1028, guess [27 ? - black delay] 28, 29, 30, 31
 * 3, 4, 5, 6, 7, #1028 guess 3, 4, 5, 6, 7 
 * 2 is not animated but seems to glow
 * 19.01.2009: this is not true, 2 does not glow #1101, everything below 3 
 * can be said to not glow (0, 1 are black)
 * 21, 22, 23 animated at half speed ? #1222 guess 21, 22, 23 not 20!
 * 16, 17, 18, 19, 20 #1222, guess 16, 17, 18, 19, 20
 * 24, 25, 26 #1222 guess 24, 25, 26... seems to stop sometimes ?
 * 27, 28, 29, 30, 31 #1222 and #1162 (two dark states)
 */

public class SSTexture {
	public static enum TextureSize { TS16, TS32, TS64, TS128 };

	public static final short textures16ChunkOffset = 76;	
	public static final short textures32ChunkOffset = 77;	
	public static final short textures64ChunkOffset = 707;	
	public static final short textures128ChunkOffset = 1000;
	
	public static final short texturesScreenChunkOffset = 321;
	
	public static final int numberOfTextures = 279;
	public static final int numberOfTexturesScreen = 111;
	
	public static final int starTexture = 205;

	public static final short [] textureOffsets =
	{
		  textures16ChunkOffset
		, textures32ChunkOffset
		, textures64ChunkOffset
		, textures128ChunkOffset
	};

	/**
	 * Array contains <em>true</em>, iff textures are stored in subchunks<br/>
	 * Indices: 0 -&gt; 16x16, 1 -&gt; 32x32, 2 -&gt; 64x64, 3 -&gt; 128x128 
	 */
	public static final boolean [] texturesInSubChunks=  {true, true, false, false};
	
	private static Map<TextureID, ResBitmap> textureCache;
	
	static
	{
		textureCache = new TreeMap<TextureID, ResBitmap>();
	}
	
	// this class is a factory, no instance needed.
	private SSTexture() { };
	
	public static ResBitmap getTexture(ResManager rm, TextureID tid)
	{
		ResBitmap rb = textureCache.get(tid);
		
		if(rb==null)
		{
			byte [] data = rm.getData(tid.getChunkId(), tid.getSubChunk());
			
			if(data!=null)
			{
				rb = new ResBitmap(data, tid.getChunkId(), tid.getSubChunk());
				textureCache.put(tid, rb);
			}
		}
		
		return rb;
	}
	
	public static void clearTextureCache()
	{
		textureCache.clear();
	}
	
	public static class TextureID implements Comparable<TextureID>
	{
		private int texnum;
		private TextureSize ts;
		private short chunkid;
		private int subchunk;
	
		public TextureID(int texnum, TextureSize ts)
		{
			this.texnum = texnum;
			this.ts = ts;
			
			chunkid = textureOffsets[ts.ordinal()];
			
			if(texturesInSubChunks[ts.ordinal()])
			{
				subchunk = texnum;
			}
			else
			{
				chunkid += texnum;
				subchunk = 0;
			}
		}
		
		public short getChunkId()
		{
			return chunkid;
		}
		
		public int getSubChunk()
		{
			return subchunk;
		}
		
		public int getTextureNumber()
		{
			return texnum;
		}
		
		public TextureSize getTextureSize()
		{
			return ts;
		}
		
		public boolean usesSubChunks()
		{
			return texturesInSubChunks[ts.ordinal()];
		}

		public int compareTo(TextureID tid)
		{
			if(tid.ts == this.ts)
			{
				return this.texnum - tid.texnum;
			}
			
			return this.ts.compareTo(tid.ts);
		}
	}
}
