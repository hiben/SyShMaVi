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
 * Created on 20.10.2008
 */
package de.zvxeb.jres;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import de.zvxeb.jres.ResFile.DirEntry;
import de.zvxeb.jres.SSLogic.MapChunkType;

/**
 * System Shock Map 
 * @author hendrik
 */
public class SSMap {
	private int lnum;
	private String lname;
	private short baseChunk;
	
	private ResManager rm;
	
	private byte [] tiles;
	private short [] textures;
	
	static Map<Integer, SSMap> mapcache;
	
	private Map<Integer, Map<Integer, MapTile>> tileCache;
	
	public static final int tileGridSize = 256;
	public static final int defaultMapSizeHorz = 64;
	public static final int defaultMapSizeVert = 64;
	
	public static final int X_DELTA_NORTH = 0;
	public static final int Y_DELTA_NORTH = +1;

	public static final int X_DELTA_WEST = -1;
	public static final int Y_DELTA_WEST = 0;
	
	public static final int X_DELTA_SOUTH = 0;
	public static final int Y_DELTA_SOUTH = -1;

	public static final int X_DELTA_EAST = +1;
	public static final int Y_DELTA_EAST = 0;
	
	private int hsize;
	private int vsize;
	private int log_hsize;
	private int log_vsize;
	private int height_shift;
	private int cyberspace;
	
	private int level_height;
	private int height_scale;
	
	static
	{
		mapcache = new TreeMap<Integer, SSMap>();
	}
	
	public static SSMap getMap(ResManager rm, int lnum)
	{
		SSMap res = mapcache.get(Integer.valueOf(lnum));
		
		if(res==null)
		{
			res = new SSMap(rm, lnum);
			mapcache.put(Integer.valueOf(lnum), res);
		}
		
		return res;
	}
	
	private SSMap(ResManager rm, int lnum)
	{
		if(lnum<0 || lnum >= SSLogic.numberOfMaps)
			throw new InvalidParameterException("Invalid Map Number: " + lnum);
		
		this.lnum = lnum;
		
		lname = SSLogic.getLevelName(lnum);
		
		baseChunk = SSLogic.chunkIdFromLevelNumber(lnum);
		
		this.rm = rm;
		
		byte linfo [] = rm.getData(SSLogic.getChunkIdFor(lnum, MapChunkType.LevelInformation));
		
		ByteBuffer bb = ByteBuffer.wrap(linfo).order(ByteOrder.LITTLE_ENDIAN);
		
		hsize = bb.getInt();
		vsize = bb.getInt();
		log_hsize = bb.getInt();
		log_vsize = bb.getInt();
		height_shift = bb.getInt();
		bb.getInt(); // skip (unused) tile map pointer
		cyberspace = bb.getInt();
		
		height_scale = 256 >>> height_shift;
		level_height = 32 * height_scale;
		
		tileCache = new TreeMap<Integer, Map<Integer, MapTile>>();
	}
	
	public String getName()
	{
		return lname;
	}
	
	public int getNumber()
	{
		return lnum;
	}
	
	public int getHorzSize()
	{
		return hsize;
	}

	public int getVertSize()
	{
		return vsize;
	}
	
	public int getLogHorzSize()
	{
		return log_hsize;
	}

	public int getLogVertSize()
	{
		return log_vsize;
	}
	
	public int getHeightShift()
	{
		return height_shift;
	}
	
	public int getHeightScale()
	{
		return height_scale;
	}
	
	public int getLevelHeight()
	{
		return level_height;
	}

	public int getCyberspace()
	{
		return cyberspace;
	}
	
	public boolean isCyberspace()
	{
		return cyberspace != 0;
	}
	
	private void ensureTextureList()
	{
		if(textures==null)
		{
			DirEntry detexturelist = rm.getChunkEntry(baseChunk + SSLogic.textureListChunkIdOffset);
			
			if(detexturelist==null)
				return;

			int tlsize = detexturelist.getLength() / 2;
			ShortBuffer sb = detexturelist.getDataBuffer().asShortBuffer();
			
			textures = new short [tlsize];
			
			sb.get(textures);
		}
	}
	
	public short [] getUsedTextures()
	{
		ensureTextureList();
		
		return textures;
	}
	
	public void printUsedTextures()
	{
		ensureTextureList();
		
		if(textures==null)
		{
				System.err.println("No Chunk for texture list...");
				return;
		}
		
		if(textures!=null)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Used textures (" + textures.length + "): ");
			for(int i=0; i<textures.length; i++)
			{
				sb.append(i + "->" + textures[i]);
				if(i<textures.length-1)
					sb.append(", ");
			}
			System.out.println(sb.toString());
		}
		else
		{
			System.err.println("No texturelist...");
		}
	}
	
	private void ensureTileMap()
	{
		if(tiles==null)
		{
			DirEntry detilemap = rm.getChunkEntry(baseChunk + SSLogic.tileMapChunkIdOffset);
			
			if(detilemap==null)
				return;
			
			tiles = detilemap.getData();
		}
	}
	
	public void printMap()
	{
		ensureTileMap();
		
		if(tiles==null)
		{
			System.err.println("No Chunk for map...");
			return;
		}
		
		assert(tiles!=null && tiles.length>=64*64*MapTile.tileDataSize);
		
		for(int h=0; h<hsize; h++)
		{
			for(int v=0; v<vsize; v++)
			{
				byte tb = tiles[(h * hsize + v)*MapTile.tileDataSize];
				// cases 1, 6 - 0x11
				String s = ".";
				switch(tb)
				{
				case 0:
					s=" ";
					break;
				case 2: // diag. open s/e
					s="\\";
					break;
				case 3: // diag. open s/w
					s="/";
					break;
				case 4: // diag. open n/w
					s="\\";
					break;
				case 5: // diag. open n/e
					s="/";
					break;
				}
				System.out.print(s);
			}
			System.out.println();
		}
	}
	
	public boolean isSolidTile(int tx, int ty)
	{
		ensureTileMap();
		
		if(tiles==null)
		{
			System.err.println("No Chunk for map...");
			return true;
		}

		byte tb = tiles[(ty * hsize + tx)*MapTile.tileDataSize];
		
		return tb==0;
	}
	
	public int getTextureInfo(int tx, int ty)
	{
		if(tx < 0 || tx >= hsize || ty < 0 || ty >= vsize)
			return -1;
		
		ensureTileMap();

		if(tiles==null)
		{
			System.err.println("No Chunk for map...");
			return -1;
		}
		
		int texinfo = (((int)tiles[(ty * hsize + tx)*MapTile.tileDataSize + 6])&0xFF);
		texinfo |= (((int)tiles[(ty * hsize + tx)*MapTile.tileDataSize + 7])&0xFF)<<8;
		
		return texinfo;
	}
	
	private MapTile getTileFromCache(int tx, int ty)
	{
		Map<Integer, MapTile> ytmap = tileCache.get(Integer.valueOf(tx));
		if(ytmap == null)
			return null;
		
		return ytmap.get(Integer.valueOf(ty));
	}
	
	private void addTileToCache(int tx, int ty, MapTile mt)
	{
		Map<Integer, MapTile> ytmap = tileCache.get(Integer.valueOf(tx));
		if(ytmap==null)
		{
			ytmap = new TreeMap<Integer, MapTile>();
			tileCache.put(Integer.valueOf(tx), ytmap);
		}
		ytmap.put(Integer.valueOf(ty), mt);
	}
	
	public MapTile getTile(int tx, int ty)
	{
		if(tx < 0 || tx >= hsize || ty<0 || ty >= vsize)
			return null;

		MapTile mt = getTileFromCache(tx, ty);
		
		if(mt!=null)
			return mt;

		ensureTileMap();
		
		if(tiles==null)
		{
			System.err.println("No Chunk for map...");
			return null;
		}

		ensureTextureList();

		if(textures==null)
		{
			System.err.println("No texture list for map...");
			return null;
		}
		
		int offs = (ty * hsize + tx) * MapTile.tileDataSize;
		
		int texNorth = getTextureInfo(tx + X_DELTA_NORTH, ty + Y_DELTA_NORTH);
		int texWest = getTextureInfo(tx + X_DELTA_WEST, ty + Y_DELTA_WEST);
		int texSouth = getTextureInfo(tx + X_DELTA_SOUTH, ty + Y_DELTA_SOUTH);
		int texEast = getTextureInfo(tx + X_DELTA_EAST, ty + Y_DELTA_EAST);

		mt = new MapTile
		(
			  tx
			, ty
			, level_height
			, height_scale
			, ByteBuffer.wrap(tiles, offs, 16).order(ByteOrder.LITTLE_ENDIAN)
			, textures
			, texNorth
			, texWest
			, texSouth
			, texEast
		);

		// ensure this is in cache to prevent loop in texture-assignment
		addTileToCache(tx, ty, mt);
		
		return mt;
	}
	
	public List<SSObject.MOTEntry> getMOTEntries()
	{
		DirEntry demot = rm.getChunkEntry(baseChunk + SSLogic.masterObjectTableChunkIdOffset);
		
		if(demot==null)
			return new Vector<SSObject.MOTEntry>();
		
		int entries = demot.getLength() / SSObject.MOTEntry.MOTEntrySize;
		
		return SSObject.parseMasterObjectTable(demot.getDataBuffer(), entries);
	}
	
	public void printEntries()
	{
		for(SSObject.MOTEntry mote : getMOTEntries())
			System.out.println(mote.toString());
	}
	
	public byte [] getTileHash()
	{
		ensureTileMap();
		
		if(tiles==null)
		{
			System.err.println("No Chunk for map...");
			return null;
		}
		
		return Utils.md5sum(tiles);
	}
}
