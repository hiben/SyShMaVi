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

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.zvxeb.jres.ResFile.DirEntry;
import de.zvxeb.jres.SSLogic.MapChunkType;
import de.zvxeb.jres.util.Util;

public class SSObject {
	public static final int MAXCLASS = 16;
	public static final int MAXSUBCLASS = 8;
	
	public static final int COPSize = 27; // bytes in objprop.dat

	// directly stolen from object.c ...
	// thanks Jim :-)
	public static final int objects_per_subclass [] [] = new int [] [] //[MAXCLASS] [MAXSUBCLASS]                      
	{
	  {  5,  2,  2,  2,  3,  2,  0,  0 },  /* 00 weapons               */
	  {  2,  2,  3,  2,  2,  2,  2,  0 },  /* 01 ammo                  */
	  {  6, 16,  2,  0,  0,  0,  0,  0 },  /* 02 missiles & tracers    */
	  {  5,  3,  0,  0,  0,  0,  0,  0 },  /* 03 grenades & explosives */
	  {  7,  0,  0,  0,  0,  0,  0,  0 },  /* 04 patches               */
	  {  5, 10,  0,  0,  0,  0,  0,  0 },  /* 05 hardware              */
	  {  7,  3,  4,  5,  3,  0,  0,  0 },  /* 06 software & logs       */
	  {  9, 10, 11,  4,  9,  8, 16, 10 },  /* 07 fixtures              */
	  {  8, 10, 15,  6, 12, 12,  9,  8 },  /* 08 */
	  {  9,  7,  3, 11,  2,  3,  0,  0 },  /* 09 switches & panels     */
	  { 10,  9,  7,  5, 10,  0,  0,  0 },  /* 0A doors & gratings      */
	  {  9, 11, 14,  0,  0,  0,  0,  0 },  /* 0B */
	  { 13,  1,  5,  0,  0,  0,  0,  0 },  /* 0C traps & triggers      */
	  {  3,  3,  4,  8, 13,  7,  8,  0 },  /* 0D containers            */
	  {  9, 12,  7,  7,  2,  0,  0,  0 },  /* 0E critters              */
	  {  0,  0,  0,  0,  0,  0,  0,  0 }   /* 0F unmapped UW objects   */
	};
	
	public static final int object_index [] [] = new int [MAXCLASS] [MAXSUBCLASS];
	
	// Object-IDs for box-type fixtures (RT Special)
	public static final int OBJID_BRIDGE		= 174;
	public static final int OBJID_CATWALK		= 175;
	public static final int OBJID_FORCEBRIDGE1	= 181;
	public static final int OBJID_FORCEBRIDGE2	= 183;
	public static final int OBJID_SMALL_CRATE 	= 393;
	public static final int OBJID_LARGE_CRATE 	= 394;
	public static final int OBJID_SECURE_CRATE	= 395;
	
	public static final int number_of_objects;
	
	static {
		int index = 0;
		for(int i=0; i<MAXCLASS; i++) {
			for(int j=0; j<MAXSUBCLASS; j++) {
				object_index [i] [j] = index;
				index += objects_per_subclass [i] [j];
			}
		}
		number_of_objects = index;
	}
	
	public static final int OTID_DECAL = 0x070201;
	public static final int OTID_GRAFITTI = 0x070202;
	public static final int OTID_WORDS = 0x070203;
	
	public enum ObjectClass
	{
		  Weapons
		, Ammunition
		, Projectiles
		, GrenadesAndExplosives
		, Patches
		, Hardware
		, SoftwareAndLogs
		, SceneryAndFixtures
		, GettableAndOther
		, SwitchesAndPanels
		, DoorsAndGratings
		, Animated
		, TrapsAndMarkers
		, Containers
		, Critters
	}
	
	// COP RenderTypes
	public enum RenderType {
		  Model3D
		, Sprite
		, Screen
		, Critter
		, Fragments
		, NotDrawn
		, OrientedSurface
		, Special
		, ForceDoor
		, UnknownRenderType
	}
	
	// COP Flags
	public static int 
		  COP_INVENTORY_OBJECT = 0x0001
		, COP_TOUCHABLE = 0x0002
		, COP_CONSUMABLE = 0x0010
		, COP_BLOCKS = 0x0020
		, COP_SOLID_OPENABLE = 0x0100
		, COP_EXPLOSION = 0x0400
		, COP_EXPLODES = 0x0800
		;
	
	public static List<MOTEntry> parseMasterObjectTable(ByteBuffer bb, int n)
	{
		LinkedList<MOTEntry> l = new LinkedList<MOTEntry>();
		
		if(n<0)
			n = bb.remaining() / MOTEntry.MOTEntrySize;
		
		while(n-- > 0)
			l.add(new MOTEntry(bb));
		
		return l;
	}
	
	// guessing... but its wrong. I assume now that alignment with ceiling triggers this...
	public static final int COP_FLAG_USE_ZOFFS = 8;
	
	public static class CommonObjectProperty {
		public int mass;
		public short hitpoints;
		public byte armour;
		public byte render_type;
		public RenderType renderType;
		public byte scale;
		public byte vulnerabilities;
		public byte special_vulnerabilities;
		public byte defence;
		public short flags;
		public short model3d_index; // in obj3d.res
		public byte num_extra_frames;
		
		private static int load_baseframe = 2;
		
		public int baseframe;
		
		public static RenderType valueToRenderType(byte rt) {
			switch(rt) {
			case 0x01: return RenderType.Model3D;
			case 0x02: return RenderType.Sprite;
			case 0x03: return RenderType.Screen;
			case 0x04: return RenderType.Critter;
			case 0x06: return RenderType.Fragments;
			case 0x07: return RenderType.NotDrawn;
			case 0x08: return RenderType.OrientedSurface;
			case 0x0B: return RenderType.Special;
			case 0x0C: return RenderType.ForceDoor;
			default:   return RenderType.UnknownRenderType;
			}
		}
		
		public static byte renderTypeToValue(RenderType rt) {
			switch(rt) {
			case Model3D:			return 0x01;
			case Sprite: 			return 0x02;
			case Screen: 			return 0x03;
			case Critter: 			return 0x04;
			case Fragments: 		return 0x06;
			case NotDrawn: 			return 0x07;
			case OrientedSurface: 	return 0x08;
			case Special: 			return 0x0B;
			case ForceDoor: 		return 0x0C;
			default: 				return -1; 
			}
		}
		
		public CommonObjectProperty(ByteBuffer bb) {
			
			mass = bb.getInt();								// 00
			hitpoints = bb.getShort();						// 04
			armour = bb.get();								// 06
			render_type = bb.get();							// 07
			renderType = valueToRenderType(render_type);

			bb.position(bb.position()+3);
			scale = bb.get();								// 0B
			
			bb.position(bb.position()+2);
			vulnerabilities = bb.get();						// 0E
			special_vulnerabilities = bb.get();				// 0F

			bb.position(bb.position()+2);
			defence = bb.get();								// 12

			bb.position(bb.position()+1);
			flags = bb.getShort();							// 14
			model3d_index = bb.getShort();					// 16

			bb.position(bb.position()+1);
			num_extra_frames = (byte)(bb.get()>>>4);		// 19
			// 26 bytes read, 1 to skip
			bb.position(bb.position()+1);
			baseframe = CommonObjectProperty.load_baseframe;
			
			CommonObjectProperty.load_baseframe += 3 + num_extra_frames;
		}
	}
	
	public static List<CommonObjectProperty> COP_list = new LinkedList<CommonObjectProperty>();
	
	public static void prepareCommonObjectProperties(ResManager rm) {
		CommonObjectProperty.load_baseframe = 2;
		
		File f = rm.findFileInSearchPath(SSLogic.objectPropertiesFile, false);
		
		if(f!=null) {
			byte [] data = Util.readFileFully(f);
			if(data!=null) {
				int cop_pos = data.length - number_of_objects * COPSize;
				System.out.println("COP-Data starts at " + cop_pos);
				
				if(cop_pos >= 0) {
					ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
					bb.position(cop_pos);
					for(int i=0; i<number_of_objects; i++) {
						COP_list.add(new CommonObjectProperty(bb));
					}
					
					System.out.println("Loaded common object properties...");
				}
			}
		}
		else {
			System.err.printf("Could not find object properties file!");
		}
	}
	
	/**
	 * MasterObjectTable-Entry
	 * @author hendrik
	 *
	 */
	public static class MOTEntry
	{
		public static final int MOTEntrySize = 27;
		
		private boolean inUse;
		private byte objectClass;
		private ObjectClass oClass;
		private byte objectSubClass;
		private int objectClassIndex;
		private int crossRefIndex;
		private int prevLink;
		private int nextLink;
		private int xCoord;
		private int yCoord;
		private int zCoord;
		private int xAngle;
		private int yAngle;
		private int zAngle;
		private byte AIIndex;
		private byte objectType;
		private short hitPoints;
		private byte state;
		
		private int objectId;
		
		private int objectTypeId;
		
		private MOTEntry(ByteBuffer bb)
		{
			int tmp = bb.get();
			
			inUse = false;
			if(tmp!=0)
				inUse = true;
			
			tmp = ((int)bb.get())&0xFF;
			objectClass = (byte)tmp;
			
			if(tmp<0 || tmp>=ObjectClass.values().length)
			{
				System.err.println("Unknown ObjectClass: " + tmp);
				oClass = null;
			}
			else
				oClass = ObjectClass.values()[tmp];
			
			tmp = ((int)bb.get())&0xFF;
			objectSubClass = (byte)tmp;

			tmp = ((int)bb.getShort())&0xFFFF;
			objectClassIndex = tmp;

			tmp = ((int)bb.getShort())&0xFFFF;
			crossRefIndex = tmp;

			tmp = ((int)bb.getShort())&0xFFFF;
			prevLink = tmp;

			tmp = ((int)bb.getShort())&0xFFFF;
			nextLink = tmp;
			
			tmp = ((int)bb.getShort())&0xFFFF;
			xCoord = tmp;
			
			tmp = ((int)bb.getShort())&0xFFFF;
			yCoord = tmp;
			
			tmp = ((int)bb.get())&0xFF;
			zCoord = tmp;

			tmp = ((int)bb.get())&0xFF;
			xAngle = tmp;
			
			tmp = ((int)bb.get())&0xFF;
			yAngle = tmp;
			
			tmp = ((int)bb.get())&0xFF;
			zAngle = tmp;
			
			tmp = ((int)bb.get())&0xFF;
			AIIndex = (byte)tmp;

			tmp = ((int)bb.get())&0xFF;
			objectType = (byte)tmp;
			
			tmp = ((int)bb.getShort())&0xFF;
			hitPoints = (short)tmp;

			tmp = ((int)bb.get())&0xFF;
			state = (byte)tmp;

			bb.get();
			bb.get();
			bb.get();
			
			// class index is used elsewhere, so this only looks wierd...
			objectId = object_index[objectClass][objectSubClass] + objectType;
			
			objectTypeId = ((((int)objectClass)&0xFF) << 16) | ((((int)objectSubClass)&0xFF) << 8) | (((int)objectType)&0xFF); 
		}

		public byte getAIIndex() {
			return AIIndex;
		}

		public short getHitPoints() {
			return hitPoints;
		}

		public boolean isInUse() {
			return inUse;
		}

		public int getNextLink() {
			return nextLink;
		}

		public byte getObjectClass() {
			return objectClass;
		}

		public int getObjectClassIndex() {
			return objectClassIndex;
		}

		public byte getObjectSubClass() {
			return objectSubClass;
		}

		public byte getObjectType() {
			return objectType;
		}

		public ObjectClass getOClass() {
			return oClass;
		}
		
		public int getCrossRefIndex()
		{
			return crossRefIndex;
		}

		public int getPrevLink() {
			return prevLink;
		}

		public byte getState() {
			return state;
		}

		public int getXAngle() {
			return xAngle;
		}

		public int getXCoord() {
			return xCoord;
		}

		public int getYAngle() {
			return yAngle;
		}

		public int getYCoord() {
			return yCoord;
		}

		public int getZAngle() {
			return zAngle;
		}

		public int getZCoord() {
			return zCoord;
		}
		
		public int getObjectId() {
			return objectId;
		}
		
		public int getObjectTypeId() {
			return objectTypeId;
		}
		
		public CommonObjectProperty getCommonProperty() {
			if(COP_list!=null) {
				return COP_list.get(objectId);
			}
			
			return null;
		}
		
		public String toString()
		{
			if(!inUse)
				return "Unused-slot";
			
			return String.format
			(
				  "Object: %d/%d/%d (%s) at %dx%d (Tile: %dx%d)"
				, getObjectClass()
				, getObjectSubClass()
				, getObjectClassIndex()
				, (getOClass()==null)?"<null>":getOClass().toString()
				, getXCoord()
				, getYCoord()
				, getXCoord() / SSMap.tileGridSize
				, getYCoord() / SSMap.tileGridSize
			);
		}
	}
	
	public static class Fixture {
		public static final int FIXTURE_SIZE = 16;
		
		public static final int [] WORDS_FONT = { 4, 7, 0, 10 };
		
		public static final int FONT_SCALE = 4;
		
		private int mapIndex;
		private int prev;
		private int next;
		private int text_or_word;
		private int flags;
		private int texture;
		private int start_frame;
		private int aux;
		
		public String toString() {
			return String.format
			(
				  "FI: text/word: %1$d flags: %2$d (%2$X) tex: %3$d (%3$X) SF: %4$d: Aux: %5$d"
				, text_or_word, flags, texture, start_frame, aux 
			);
		}
		
		public int getMapIndex() {
			return mapIndex;
		}
		
		public int getPrev() {
			return prev;
		}
		
		public int getNext() {
			return next;
		}
		
		public int getText() {
			return text_or_word;
		}

		public int getWord() {
			return text_or_word;
		}
		
		public int getFlags() {
			return flags;
		}
		
		public int getTexture() {
			return texture;
		}
		
		public int getStart_frame() {
			return start_frame;
		}
		
		public int getAux() {
			return aux;
		}
		
		public Fixture(ByteBuffer bb) {
			mapIndex = bb.getShort();
			prev = bb.getShort();
			next = bb.getShort();
			text_or_word = bb.getShort();
			flags = bb.getShort();
			texture = bb.getShort();
			start_frame = bb.getShort();
			aux = bb.getShort();
		}
	}
	
	private static Map<Integer, List<Fixture>> fixtureMap = new TreeMap<Integer, List<Fixture>>();
	
	public static List<Fixture> getFixtureList(ResManager rm, int level) {
		List<Fixture> levelFix = fixtureMap.get(level);
		
		if(levelFix == null) {
			DirEntry de = rm.getChunkEntry(SSLogic.getChunkIdFor(level, MapChunkType.Fixture));
			if(de == null)
				return null;
			
			ByteBuffer bb = de.getDataBuffer();

			levelFix = new LinkedList<Fixture>();
			fixtureMap.put(level, levelFix);
			
			int count = bb.limit() / Fixture.FIXTURE_SIZE;
		
			System.out.println("Loading " + count + " fixtures...");
			while(count-- > 0) {
				levelFix.add(new Fixture(bb));
			}
		}
		
		return levelFix;
	}
	
	public static class Container {
		public static final int CONTAINER_SIZE = 21;
		public static final int CONTAINER_CAPACITY = 4;
		
		public static final int CONTAINER_DEFAULT_TOP_BOTTOM_TEXTURE = 12;
		public static final int CONTAINER_DEFAULT_SIDE_TEXTURE = 11;
		
		public static final int CONTAINER_SMALL_SIZE = 64;
		public static final int CONTAINER_LARGE_SIZE = 128;
		public static final int CONTAINER_SECURE_SIZE = 256;

		private int mapIndex;
		private int prev;
		private int next;
		
		private int [] content = new int [CONTAINER_CAPACITY];
		
		private int width;
		private int height;
		private int depth;
		
		private int top_texture;
		private int side_texture;
		
		public int getMapIndex() {
			return mapIndex;
		}
		
		public int getPrev() {
			return prev;
		}
		
		public int getNext() {
			return next;
		}

		public int[] getContent() {
			return content;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		public int getDepth() {
			return depth;
		}

		public int getTopTexture() {
			return top_texture;
		}

		public int getSideTexture() {
			return side_texture;
		}

		public Container(ByteBuffer bb) {
			mapIndex = bb.getShort();
			prev = bb.getShort();
			next = bb.getShort();
			
			for(int i=0; i<CONTAINER_CAPACITY; i++)
				content[i] = bb.getShort();
			
			width = ((int)bb.get())&0xFF;
			height = ((int)bb.get())&0xFF;
			depth = ((int)bb.get())&0xFF;
			
			top_texture = ((int)bb.get())&0xFF;
			side_texture = ((int)bb.get())&0xFF;
			
			bb.get();
			bb.get();
		}
	}
	
	private static Map<Integer, List<Container>> containerMap = new TreeMap<Integer, List<Container>>();

	public static List<Container> getContainerList(ResManager rm, int level) {
		List<Container> levelCon = containerMap.get(level);
		
		if(levelCon == null) {
			DirEntry de = rm.getChunkEntry(SSLogic.getChunkIdFor(level, MapChunkType.Container));
			if(de == null)
				return null;
			
			ByteBuffer bb = de.getDataBuffer();

			levelCon = new LinkedList<Container>();
			containerMap.put(level, levelCon);
			
			int count = bb.limit() / Container.CONTAINER_SIZE;
		
			System.out.println("Loading " + count + " containers...");
			while(count-- > 0) {
				levelCon.add(new Container(bb));
			}
		}
		
		return levelCon;
	}
}
