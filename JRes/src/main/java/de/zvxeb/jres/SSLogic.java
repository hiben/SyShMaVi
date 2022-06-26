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

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import de.zvxeb.jres.ResFile.DirEntry;


/**
 * System Shock Game Logic Related Constants
 * @author hendrik
 */
public class SSLogic {
	// maps
	public static final String mapArchiveFile = "archive.dat";
	// textures
	public static final String textureFile = "texture.res";
	// in-game palettes
	public static final String paletteFile = "gamepal.res";
	// items
	public static final String objectBitmapFile = "objart.res";
	// critters
	public static final String object2BitmapFile = "objart2.res";
	// critters
	public static final String object3BitmapFile = "objart3.res";
	// sounds
	public static final String digiFXFile = "digifx.res";
	// 3d-object models
	public static final String model3DFile = "obj3d.res";
	// 3d-object textures
	public static final String model3DTextureFile = "citmat.res";
	// words
	public static final String wordsFile = "cybstrng.res";
	// screen decoration, fonts
	public static final String screenFile = "gamescr.res";
	
	// object properties (no res file)
	public static final String objectPropertiesFile = "objprop.dat";

	// texture properties (no res file)
	public static final String texturePropertiesFile = "textprop.dat";
	
	public static final Map<String, ChunkRange> chunkRangeMap;
	
	public static final Map<ChunkRange, Set<String>> rangeFileMap;
	
	public static final int defaultPaletteChunkId = 700;
	public static final int objectBitmapChunkId = 1350;
	public static final int doorBitmapChunkBase = 2400;
	public static final int wordChunk = 2152;
	public static final int fontBaseChunk = 602;
	
	public static final int decalChunk = 78;
	public static final int graffitiChunk = 79;
	public static final int repulsorChunk = 79;
	
	public static final byte defaultFontColor = 54;
	
	public static final String [] cdFilesArray =
	{
		  mapArchiveFile
		, paletteFile
		, objectBitmapFile
	};
	
	public static final String [] hdFilesArray =
	{
		  textureFile
		, object2BitmapFile
		, object3BitmapFile
		, digiFXFile
		, model3DFile
		, model3DTextureFile
	};
	
	static private final Set<String> cdFiles, hdFiles;
	
	static
	{
		chunkRangeMap = new TreeMap<String, ChunkRange>(Collator.getInstance());
		
		chunkRangeMap.put(mapArchiveFile, new ChunkRange(4000, 5553));

		chunkRangeMap.put(textureFile, new ChunkRange(75, 1272));

		chunkRangeMap.put(paletteFile, new ChunkRange(700, 702));

		chunkRangeMap.put(objectBitmapFile, new ChunkRange(1350, 1350));
		
		chunkRangeMap.put(object2BitmapFile, new ChunkRange(1400, 1817));
		
		chunkRangeMap.put(object3BitmapFile, new ChunkRange(78, 2440));

		chunkRangeMap.put(digiFXFile, new ChunkRange(201, 315));

		chunkRangeMap.put(model3DFile, new ChunkRange(2300, 2379));
		
		chunkRangeMap.put(model3DTextureFile, new ChunkRange(475, 2194));
		
		rangeFileMap = new TreeMap<ChunkRange, Set<String>>(new RangeSorter());

		for(Map.Entry<String, ChunkRange> fre : chunkRangeMap.entrySet())
		{
			Set<String> fs = rangeFileMap.get(fre.getValue());
			if(fs==null)
			{
				fs = new TreeSet<String>(Collator.getInstance());
				rangeFileMap.put(fre.getValue(), fs);				
			}
			fs.add(fre.getKey());
		}
		
		cdFiles = new TreeSet<String>(Collator.getInstance());
		cdFiles.addAll(Arrays.asList(cdFilesArray));
		hdFiles = new TreeSet<String>(Collator.getInstance());
		hdFiles.addAll(Arrays.asList(hdFilesArray));
	}
	
	static public class ChunkRange
	{
		int min, max;
		public ChunkRange(int min, int max)
		{
			this.min = min;
			this.max = max;
		}
		
		public ChunkRange(int id)
		{
			this(id, id);
		}
		
		public int getMin()
		{
			return min;
		}
		
		public int getMax()
		{
			return max;
		}
		
		public boolean containsChunk(int cid)
		{
			return (min <= cid) && (cid <= max);
		}
	}
	
	static public class RangeSorter implements Comparator<ChunkRange>
	{
		public int compare(ChunkRange arg0, ChunkRange arg1) {
			if(arg0.min == arg1.min)
			{
				return arg0.max - arg1.max;
			}
			else
			{
				return arg0.min - arg1.min;
			}
		}
	}
	
	static public class RangeSubSetFinder implements Comparator<ChunkRange>
	{
		public int compare(ChunkRange arg0, ChunkRange arg1)
		{
			if(arg0.min < arg1.min)
				return -1;
			
			if(arg0.min > arg1.max)
				return 1;
			
			if(arg0.max > arg1.max)
				return 1;
			
			return 0;
		}
	}
	
	/**
	 * Determines resource-files that could contain a chunk
	 * based on their maximum and minimum chunks. 
	 * @param ci chunk id to search for
	 * @return a (potential empty) list of filenames
	 */
	public static List<String> fileForChunk(int ci)
	{
		List<String> l = new Vector<String>();
		
		for(Map.Entry<String, ChunkRange> mine : chunkRangeMap.entrySet())
		{
			assert(mine.getValue() != null);
			
			if(mine.getValue().containsChunk(ci))
			{
				l.add(mine.getKey());
			}
		}
		
		return l;
	}
	
	public static DirEntry getChunk(String hdDir, String cdromDir, ResManager rm, int ci)
	{
		DirEntry de = rm.getChunkEntry(ci);
		
		if(de==null)
		{
		}
		
		return de;
	}
	
	public static final int numberOfMaps = 16;
	public static final short mapChunkIdOffset = 4000;
	public static final short allocatedChunksPerMap = 100;
	
	public static final short archiveNameChunk = 4000;
	public static final short playerInfoChunk = 4001;
	
	public static final short levelInformationChunkIdOffset = 4;	
	public static final short tileMapChunkIdOffset = 5;
	
	public static final short textureListChunkIdOffset = 7;

	public static final short masterObjectTableChunkIdOffset = 8;
	public static final int objectCrossReferenceTableChunkIdOffset = 9;
	public static final int classTableChunkIdOffset = 10;
	
	public static final String [] levelNames =
	{
		  "Level R - Energy Systems"
		, "Level 1 - Healing Suites"
		, "Level 2 - Research Facilities"
		, "Level 3 - Department of Maintenance"
		, "Level 4 - Storage Cells"
		, "Level 5 - Flight Deck"
		, "Level 6 - Crew Facilities and Executive Suites"
		, "Level 7 - Systems Engineering"
		, "Level 8 - Department of Security"
		, "Level 9 - Bridge"
		, "SHODAN c/space"
		, "Delta Grove"
		, "Alpha Grove"
		, "Beta Grove"
		, "C/space L1-2"
		, "C/space other"
	};
	
	public static enum MapChunkType
	{
		  ArchiveName
		, PlayerInformation
		, Unknown02
		, Unknwon03
		, LevelInformation
		, TileMap
		, Unknown06
		, TextureList
		, MasterObjectTable
		, ObjectCrossReferenceTable
		// Class specific tables
		, Weapon					// 10
		, Ammo 						// 11
		, Projectile 				// 12
		, Explosive					// 13
		, Patch						// 14
		, Hardware					// 15
		, Software					// 16
		, Fixture					// 17
		, Misc						// 18
		, Switch					// 19
		, Door						// 20
		, Animated					// 21
		, Trigger					// 22
		, Container					// 23
		, Critter					// 24
		, UnknownClass	 			// 25*
		, InvalidType				// 26*
	};
	
	public static short chunkIdFromLevelNumber(int lnum)
	{
		if(lnum<0 || lnum >= numberOfMaps)
			return -1;
		
		return (short)(mapChunkIdOffset + lnum * allocatedChunksPerMap);
	}
	
	public static int levelNumberFromChunkId(short cid)
	{
		if(cid < mapChunkIdOffset)
			return -1;
		
		if(cid > (mapChunkIdOffset + numberOfMaps * allocatedChunksPerMap) )
			return -1;
		
		return ( (cid - mapChunkIdOffset) / allocatedChunksPerMap );
	}
	
	public static String getLevelName(int lnum)
	{
		if(lnum<0 || lnum>=numberOfMaps)
			return "<Invalid Level Number>";
		
		return levelNames[lnum];
	}
	
	public static short getChunkIdFor(int lnum, MapChunkType mct)
	{
		short base = chunkIdFromLevelNumber(lnum);
		
		if(base<0)
			return base;
		
		return (short)(base + mct.ordinal());
	}
	
	public static MapChunkType getMapChunkType(short cid)
	{
		if(cid < mapChunkIdOffset)
			return MapChunkType.UnknownClass;
		
		if(cid > (mapChunkIdOffset + numberOfMaps * allocatedChunksPerMap) )
			return MapChunkType.UnknownClass;
		
		if(cid==archiveNameChunk)
			return MapChunkType.ArchiveName;

		if(cid==playerInfoChunk)
			return MapChunkType.PlayerInformation;
		
		int tmp = (cid - mapChunkIdOffset) % allocatedChunksPerMap;

		if(tmp < 2)
			return MapChunkType.InvalidType;
		
		if(tmp >= MapChunkType.values().length)
			return MapChunkType.UnknownClass;
		
		return MapChunkType.values()[tmp];
	}
}