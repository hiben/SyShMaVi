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
 * Created on 23.10.2008
 */
package de.zvxeb.jres;

import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

public class MapTile {
	private Type ttype;
	private int rflags;
	private int floor;
	private int ceiling;
	private int slope;
	private int obj_index;
	private int texture;
	private int flags;
	private int state;
	
	private int textureOffset;
	private boolean adjTexture;
	private SlopeType slopeType;
	private int lowerShade;
	private int upperShade;
	private boolean rendered;
	private boolean automapped;
	
	private int fRot;
	private int cRot;
	
	private boolean radiation;
	private boolean biohazard;
	
	private int textureFloor;
	private int textureCeiling;
	private int textureWall;
	
	private int cyberColorCeiling;
	private int cyberColorFloor;
	
	
	private boolean hasInvalidTexture;
	private int invalidTex;
	
	private short [] textureList;

	private int textureIdNorth, textureIdWest, textureIdSouth, textureIdEast;
	
	private int tile_x, tile_y;
	
	public MapTile(int tile_x, int tile_y, int lheight, int hscale, ByteBuffer bb, short [] textureList, int texNorth, int texWest, int texSouth, int texEast)
	{
		this.tile_x = tile_x;
		this.tile_y = tile_y;
		
		int tmp = ((int)bb.get())&0xFF;
		
		if(tmp<0 || tmp>=Type.values().length)
			throw new InvalidParameterException("Invalid tile type: " + tmp);
		
		ttype = Type.values()[tmp];
		
		tmp = ((int)bb.get())&0xFF;
		
		rflags = tmp & 0xE0;
		floor = (tmp & 0x1F) * hscale;

		tmp = ((int)bb.get())&0xFF;
		
		rflags |= (tmp & 0xE0)>>>4;
		
		ceiling = lheight - (tmp&0x1F) * hscale;
		
		tmp = ((int)bb.get())&0xFF;
		
		slope = tmp * hscale;
		
		obj_index = ((int)bb.getShort())&0xFFFF;
		texture = ((int)bb.getShort())&0xFFFF;
		flags = bb.getInt();
		state = bb.getInt();
		
		textureOffset = textureOffset(lheight, hscale, flags);
		adjTexture = adjTexture(flags);
		slopeType = slopeType(flags);
		lowerShade = lowerShade(flags);
		upperShade = upperShade(flags);
		rendered = rendered(flags);
		automapped = automapped(flags);
		
		fRot = rotation(rflags, false);
		cRot = rotation(rflags, true);
		
		radiation = hazard(rflags, true);
		biohazard = hazard(rflags, false);
		
		textureFloor = texture(texture, false);
		textureCeiling = texture(texture, true);
		textureWall = wallTexture(texture);
		
		cyberColorCeiling = cyberSpaceCeilingColor(texture);
		cyberColorFloor = cyberSpaceFloorColor(texture);
		
		hasInvalidTexture = false;
		invalidTex = 0;
		
		this.textureList = textureList; 

		if(textureWall>=textureList.length)
		{
			System.out.println(String.format("Error with texture at a tile. solid: %s, tex: (%d)", ttype==Type.Solid?"yes":"no", textureWall));
			invalidTex = textureWall;
			textureWall = 0;
			hasInvalidTexture = true;
		}
		
		// initialize wall textures with the tiles texture
		textureIdNorth = textureIdEast = textureIdSouth = textureIdWest = textureWall;
		
		if(isAdjTexture())
		{
			int wt = 0;
			if(texNorth>0)
			{
				wt = wallTexture(texNorth);
				if(wt >= textureList.length)
					wt = textureWall;
				textureIdNorth = wt;
			}
			if(texWest>0)
			{
				wt = wallTexture(texWest);
				if(wt >= textureList.length)
					wt = textureWall;
				textureIdWest = wt;
			}
			if(texSouth>0)
			{
				wt = wallTexture(texSouth);
				if(wt >= textureList.length)
					wt = textureWall;
				textureIdSouth = wt;
			}
			if(texEast>0)
			{
				wt = wallTexture(texEast);
				if(wt >= textureList.length)
					wt = textureWall;
				textureIdEast = wt;
			}
		}
	}
	
	public int getTextureIdWallNorth() {
		return textureIdNorth;
	}

	public void setTextureIdWallNorth(int textureNorth) {
		this.textureIdNorth = textureNorth;
	}

	public int getTextureIdWallEast() {
		return textureIdEast;
	}

	public void setTextureIdWallEast(int textureEast) {
		this.textureIdEast = textureEast;
	}

	public int getTextureIdWallSouth() {
		return textureIdSouth;
	}

	public void setTextureIdWallSouth(int textureSouth) {
		this.textureIdSouth = textureSouth;
	}

	public int getTextureIdWallWest() {
		return textureIdWest;
	}

	public void setTextureIdWallWest(int textureWest) {
		this.textureIdWest = textureWest;
	}

	public static int tileDataSize = 16;
	
	/**
	 * Tile types
	 * @author hendrik
	 */
	public static enum Type 
	{
		  Solid
		, Open
		, DiagonalSE
		, DiagonalSW
		, DiagonalNW
		, DiagonalNE
		, SlopeN
		, SlopeE
		, SlopeS
		, SlopeW
		, ValleyNW
		, ValleyNE
		, ValleySE
		, ValleySW
		, RidgeSE
		, RidgeSW
		, RidgeNW
		, RidgeNE
		, Unknown
	}
	
	private static Type [] opTypeMap;
	
	static
	{
		opTypeMap = new Type[Type.values().length];
		
		// base
		opTypeMap[Type.Solid.ordinal()] = Type.Solid;
		opTypeMap[Type.Open.ordinal()] = Type.Open;
		opTypeMap[Type.Unknown.ordinal()] = Type.Unknown;
		
		// Diagonals cannot be opposite at ceiling.
		opTypeMap[Type.DiagonalSE.ordinal()] = Type.DiagonalSE;
		opTypeMap[Type.DiagonalSW.ordinal()] = Type.DiagonalSW;
		opTypeMap[Type.DiagonalNW.ordinal()] = Type.DiagonalNW;
		opTypeMap[Type.DiagonalNE.ordinal()] = Type.DiagonalNE;

		// Slopes
		opTypeMap[Type.SlopeN.ordinal()] = Type.SlopeS;
		opTypeMap[Type.SlopeE.ordinal()] = Type.SlopeW;
		opTypeMap[Type.SlopeS.ordinal()] = Type.SlopeN;
		opTypeMap[Type.SlopeW.ordinal()] = Type.SlopeE;

		// Valleys
		opTypeMap[Type.ValleyNW.ordinal()] = Type.RidgeSE;
		opTypeMap[Type.ValleyNE.ordinal()] = Type.RidgeSW;
		opTypeMap[Type.ValleySE.ordinal()] = Type.RidgeNW;
		opTypeMap[Type.ValleySW.ordinal()] = Type.RidgeNE;
		
		// Ridges
		opTypeMap[Type.RidgeSE.ordinal()] = Type.ValleyNW;
		opTypeMap[Type.RidgeSW.ordinal()] = Type.ValleyNE;
		opTypeMap[Type.RidgeNW.ordinal()] = Type.ValleySE;
		opTypeMap[Type.RidgeNE.ordinal()] = Type.ValleySW;
	}
	
	/**
	 * First tile type with triangular shape
	 */
	public static final Type DiagonalBase = Type.DiagonalSE;
	/**
	 * First tile type with slope
	 */
	public static final Type SlopeBase = Type.SlopeN;
	/**
	 * First tile type with slope from its diagonal
	 */
	public static final Type DSlopeBase = Type.ValleyNW;
	
	/**
	 * tile flags
	 */
	public static final int
		  TF_TEXTURE_OFFSET	= 0x0000001F
		, TF_ADJ_TEXTURE	= 0x00000100
		, TF_WHICH_SLOPE	= 0x00000C00
		, TF_SLOPE_BOTH		= 0x00000000
		, TF_SLOPE_OPPOSITE	= 0x00000400
		, TF_SLOPE_FLOOR	= 0x00000800
		, TF_SLOPE_CEILING	= 0x00000C00
		, TF_LOWER_SHADE	= 0x000F0000
		, TF_UPPER_SHADE	= 0x0F000000
		, TF_RENDERED		= 0x40000000   /* tile was in view     */
		, TF_AUTOMAPPED		= 0x80000000   /* tile shows up on hud */
		;
	
	public static enum SlopeType
	{
		  Both
		, Opposite
		, Floor
		, Ceiling
	}
	
	private static int textureOffset(int lheight, int hscale, int flags)
	{
		return hscale * (flags & TF_TEXTURE_OFFSET);
	}
	
	private static boolean adjTexture(int flags)
	{
		return (flags & TF_ADJ_TEXTURE)!=0;
	}
	
	private static SlopeType slopeType(int flags)
	{
		switch(flags & TF_WHICH_SLOPE)
		{
		case TF_SLOPE_OPPOSITE:
			return SlopeType.Opposite;
		case TF_SLOPE_FLOOR:
			return SlopeType.Floor;
		case TF_SLOPE_BOTH:
			return SlopeType.Both;
		case TF_SLOPE_CEILING:
			return SlopeType.Ceiling;
		}
		
		assert(false);
		
		return null;
	}
	
	private static int lowerShade(int flags)
	{
		return (flags&TF_LOWER_SHADE)>>>16;
	}

	private static int upperShade(int flags)
	{
		return (flags&TF_UPPER_SHADE)>>>24;
	}

	private static boolean rendered(int flags)
	{
		return (flags&TF_RENDERED)!= 0;
	}

	private static boolean automapped(int flags)
	{
		return (flags&TF_AUTOMAPPED)!= 0;
	}
	
	private static int rotation(int rflags, boolean ceiling)
	{
		return (rflags >>> (ceiling?1:5))&3;
	}

	/**
	 * Tile Hazard Information<br>
	 * Information from floor indicates biological hazard,
	 * ceiling indicates radiation.
	 * @param rflags tile-rflags
	 * @param ceiling if this is <em>true</em>, get information from ceiling
	 * @return <em>true</em>, iff hazard information exists
	 */
	private static boolean hazard(int rflags, boolean ceiling)
	{
		return ((rflags >>> (ceiling?3:7))&1)!=0;
	}
	
	private static int texture(int texture, boolean ceiling)
	{
		return (texture >>> (ceiling?6:11)) & 0x1F;
	}
	
	private static int wallTexture(int texture)
	{
		return texture & 0x3F;
	}
	
	private static int cyberSpaceCeilingColor(int texture) {
		return (texture >>> 8) & 0xFF;
	}

	private static int cyberSpaceFloorColor(int texture) {
		return texture & 0xFF;
	}

	public boolean isAutomapped() {
		return automapped;
	}
	
	public boolean getBiohazard()
	{
		return biohazard;
	}

	public int getCeiling() {
		return ceiling;
	}

	public int getCRot() {
		return cRot;
	}

	public int getFlags() {
		return flags;
	}

	public int getFloor() {
		return floor;
	}

	public int getFRot() {
		return fRot;
	}

	public int getLowerShade() {
		return lowerShade;
	}

	public int getObj_index() {
		return obj_index;
	}

	public boolean isAdjTexture() {
		return adjTexture;
	}
	
	public boolean isRendered() {
		return rendered;
	}

	public boolean getRadiation()
	{
		return radiation;
	}
	
	public int getRflags() {
		return rflags;
	}

	public int getSlope() {
		return slope;
	}

	public SlopeType getSlopeType() {
		return slopeType;
	}

	public int getState() {
		return state;
	}

	/**
	 * @return texture-flag (all)
	 */
	public int getTexture() {
		return texture;
	}

	public short getTextureFloor() {
		return textureList[textureFloor];
	}

	public int getTextureIndexFloor() {
		return textureFloor;
	}

	public short getTextureCeiling() {
		return textureList[textureCeiling];
	}

	public int getTextureIndexCeiling() {
		return textureCeiling;
	}

	public short getTextureWall() {
		return textureList[textureWall];
	}

	public int getTextureIndexWall() {
		return textureWall;
	}
	
	public int getCyberColorCeiling() {
		return cyberColorCeiling;
	}

	public int getCyberColorFloor() {
		return cyberColorFloor;
	}

	/**
	 * relative to upper left vertex
	 * @return
	 */
	public int getTextureOffset() {
		return textureOffset;
	}

	public Type getType() {
		return ttype;
	}
	
	public Type getCeilingType()
	{
		if(ttype == Type.Solid)
			return Type.Solid;
		
		if(ttype.compareTo(SlopeBase)>=0 && slopeType == SlopeType.Floor)
			return Type.Open;
		
		if(slopeType == SlopeType.Opposite)
			return opTypeMap[ttype.ordinal()];
		
		// Ceiling
		return ttype;
	}
	
	public Type getFloorType()
	{
		if(ttype == Type.Solid)
			return Type.Solid;
		
		if(ttype.compareTo(SlopeBase) >= 0 && slopeType == SlopeType.Ceiling)
			return Type.Open;
		
		// Opposite refers to ceilings only
		
		// Floor
		return ttype;
	}

	public int getUpperShade() {
		return upperShade;
	}
	
	public boolean hasInvalidTexture()
	{
		return hasInvalidTexture;
	}
	
	public int getInvalidTex()
	{
		return invalidTex;
	}
	
	public static double NORTH = 0.0;
	public static double SOUTH = 1.0;
	public static double WEST = 0.0;
	public static double EAST = 1.0;
	public static double CENTER = 0.5;
	
	public static double getSlopeHeight(Type ttype, double slope, double x, double y)
	{
		if(x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0)
			return 0.0;
		// pos x is east
		// pos y is south
		// y == 0 -> NORTH
		// x == 0 -> WEST
		
		if(ttype == Type.SlopeN)
		// y = 0 -> floor + slope, y = 1 -> floor
		{
			return (1.0 - y) * slope;
		}

		if(ttype == Type.SlopeW)
		// x = 0 -> floor + slope, x = 1 -> floor 
		{
			return (1.0 - x) * slope;
		}

		if(ttype == Type.SlopeS)
		// y = 0 -> floor, y = 1 -> floor + slope
		{
			return y * slope;
		}
		
		if(ttype == Type.SlopeE)
		// x = 0 -> floor, x = 1 -> floor + slope
		{
			return x * slope;
		}

		double tx = 1.0 - x;
		double ty = 1.0 - y;
		
		// NW, NE, SW height, SE low
		//        X = 1
		//       ^-_
		// Y = 0 |/|   Y = 1
		//       ^-^
		//       X = 0
		if(ttype == Type.ValleyNW)
		{
			if(tx > ty) // in right area
			{
				return tx * slope;
			}
			// left area
			return ty * slope;
		}

		// NE, NW, SE height, SW low
		//        X = 1
		//       ^-^
		// Y = 0 |\|   Y = 1
		//       ^-_
		//       X = 0
		if(ttype == Type.ValleyNE)
		{
			if(x > ty) // in right area
			{
				return x * slope;
			}
			// left area
			return ty * slope;
		}
		
		// NE, NW, SE height, SW low
		//        X = 1
		//       ^-^
		// Y = 0 |/|   Y = 1
		//       _-^
		//       X = 0
		if(ttype == Type.ValleySE)
		{
			if(tx > ty) // in right area
			{
				return y * slope;
			}
			// left area
			return x * slope;
		}
		
		// NE, NW, SE height, SW low
		//        X = 1
		//       _-^
		// Y = 0 |\|   Y = 1
		//       ^-^
		//       X = 0
		if(ttype == Type.ValleySW)
		{
			if(x > ty) // in right area
			{
				return y * slope;
			}
			// left area
			return tx * slope;
		}
		
		// NW, NE, SW height, SE low
		//        X = 1
		//       _-^
		// Y = 0 |/|   Y = 1
		//       _-_
		//       X = 0
		if(ttype == Type.RidgeSE)
		{
			if(tx > ty) // in right area
			{
				return x * slope;
			}
			// left area
			return y * slope;
		}
		
		// NE, NW, SE height, SW low
		//        X = 1
		//       _-_
		// Y = 0 |\|   Y = 1
		//       _-^
		//       X = 0
		if(ttype == Type.RidgeSW)
		{
			if(x > ty) // in right area
			{
				return tx * slope;
			}
			// left area
			return y * slope;
		}
		
		// NE, NW, SE height, SW low
		//        X = 1
		//       _-_
		// Y = 0 |/|   Y = 1
		//       ^-_
		//       X = 0
		if(ttype == Type.RidgeNW)
		{
			if(tx > ty) // in right area
			{
				return ty * slope;
			}
			// left area
			return tx * slope;
		}
		
		// NE, NW, SE height, SW low
		//        X = 1
		//       ^-_
		// Y = 0 |\|   Y = 1
		//       _-_
		//       X = 0
		if(ttype == Type.RidgeNE)
		{
			if(x > ty) // in right area
			{
				return ty * slope;
			}
			// left area
			return x * slope;
		}
		
		return 0.0;

	}

	public double getFloorAt(double x, double y)
	{
		if(slopeType == SlopeType.Ceiling)
			return floor;
		
		return floor + getSlopeHeight(ttype, slope, x, y);
	}
	
	public double getCeilingAt(double x, double y)
	{
		Type ctype = getCeilingType();
		if(ctype.compareTo(SlopeBase)<0)
			return ceiling;
		
		return (ceiling - slope) + getSlopeHeight(ctype, slope, x, y);
	}
	
	public static boolean isSolid(Type ttype)
	{
		return ttype == Type.Solid;
	}
	
	public static boolean isOpen(Type ttype)
	{
		return ttype == Type.Open;
	}

	public static boolean isDiag(Type ttype)
	{
		int ct = ttype.compareTo(DiagonalBase);
		return (ct>=0 && ct<4);
	}

	public static boolean isSlope(Type ttype)
	{
		int ct = ttype.compareTo(SlopeBase);
		return (ct>=0 && ct<4);
	}

	public static boolean isDSlope(Type ttype)
	{
		int ct = ttype.compareTo(DSlopeBase);
		return (ct>=0 && ct<8);
	}
	
	public int getTileX() {
		return tile_x;
	}
	
	public int getTileY() {
		return tile_y;
	}

}
