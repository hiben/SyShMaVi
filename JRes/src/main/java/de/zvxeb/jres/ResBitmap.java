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
 * Created on 19.09.2008
 */
package de.zvxeb.jres;

import java.awt.image.BufferedImage;

import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ResBitmap {
	
	public static final short BT_COMPRESSION = 4;
	public static final short BT_NOCOMPRESSION = 2;
	
	private int magic00;
	private short compression;
	private short magic06;
	private short width;
	private short height;
	private short width_shadow;
	private byte l2_width;
	private byte l2_height;
	private short hot1, hot2, hot3, hot4;
	private int magic18;
	
	private byte [] private_palette;
	private byte [] current_palette;
	
	private byte [] bitmap;
	private BufferedImage bi = null;
	private BufferedImage argbbi = null;
	
	private short cid;
	private int scnum;
	
	private static final byte [] greyPalette;
	
	static {
		greyPalette = new byte [768];
		for(int i=0; i<256; i++) {
			greyPalette[i*3] = (byte)(i);
			greyPalette[i*3 + 1] = (byte)(i);
			greyPalette[i*3 + 2] = (byte)(i);
		}
	}
	
	public ResBitmap(byte [] data, short cid, int scnum)
	{
		this.cid = cid;
		this.scnum = scnum;
		
		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		magic00 = bb.getInt();
		compression = bb.getShort();
		magic06 = bb.getShort();
		width = bb.getShort();
		height = bb.getShort();
		width_shadow = bb.getShort();
		l2_width = bb.get();
		l2_height = bb.get();
		hot1 = bb.getShort();
		hot2 = bb.getShort();
		hot3 = bb.getShort();
		hot4 = bb.getShort();
		magic18 = bb.getInt();
		
		bitmap = null;
		
		if(width*height > 0) {
			if(compression!=BT_COMPRESSION)
			{
				bitmap = new byte [width*height];
				bb.get(bitmap);
			}
			else
			{
				bitmap = Utils.unpackFormat04Frame(bb, null, width*height);
			}
		} else {
			bitmap = new byte [0];
		}
		
		current_palette = null;
		
		int rem = bb.limit() - bb.position();
		
		if(rem>=(768 + 4))
		{
			int palflag = bb.getInt();
			
			if(palflag==0x01000000)
			{
				private_palette = new byte [768];
				bb.get(private_palette);
				current_palette = private_palette;
			}
		}
	}
	
	private byte quickL2Short(int i) {
		for(int l=0; l<16; l++) { // l < 16 because max-value is signed
			if ((1<<l) >= i)
				return (byte)(l-1);
		}
		return 16;
	}
	
	public ResBitmap(BufferedImage bimg, boolean noPalette, short m1, short m2, short m3, short h1, short h2, short h3, short h4, short cid, int scnum) {
		this.cid = cid;
		this.scnum = scnum;
		
		if(bimg.getType() != BufferedImage.TYPE_BYTE_INDEXED) {
			throw new IllegalArgumentException("Can only use byte indexed images!");
		}
		
		width = (short)bimg.getWidth();
		height = (short)bimg.getHeight();

		if(width > Short.MAX_VALUE || height > Short.MAX_VALUE) {
			throw new IllegalArgumentException("Image is to big (" + width + "x" + height + ") (Max is " + Short.MAX_VALUE + " in each dimension)!");
		}
		
		magic00 = m1;
		magic06 = m2;
		magic18 = m3;
		
		l2_width = quickL2Short(width);
		l2_height = quickL2Short(height);
		
		width_shadow = width;
		
		compression = BT_NOCOMPRESSION;
		
		hot1 = h1;
		hot2 = h2;
		hot3 = h3;
		hot4 = h4;
		
		bitmap = new byte [width*height];
		bimg.getRaster().getDataElements(0, 0, width, height, bitmap);
		
		current_palette = null;
		
		if(!noPalette) {
			IndexColorModel icm = (IndexColorModel)bimg.getColorModel();

			int colors = icm.getMapSize();
			byte [] reds = new byte [colors];
			byte [] greens = new byte [colors];
			byte [] blues = new byte [colors];

			icm.getReds(reds);
			icm.getGreens(greens);
			icm.getBlues(blues);

			private_palette = new byte [768];

			for(int i=0; i<768; i++) {
				if(i>=colors)
					break;

				private_palette[i*3] = reds[i];
				private_palette[i*3+1] = greens[i];
				private_palette[i*3+2] = blues[i];
			}
			
			current_palette = private_palette;
		}
	}
	
	public void saveImage(OutputStream os, boolean keepCompressed, boolean forceCompressed, boolean keepPrivatePalette, boolean forcePalette) throws IOException {
		byte [] bits = new byte [4];
		ByteBuffer bb = ByteBuffer.wrap(bits).order(ByteOrder.LITTLE_ENDIAN);
		
		boolean doCompression = forceCompressed ? true : keepCompressed ? (compression == BT_COMPRESSION) : false;
		
		bb.putInt(0, magic00);
		os.write(bits, 0, 4);
		bb.putShort(0, doCompression ? BT_COMPRESSION : BT_NOCOMPRESSION);
		os.write(bits, 0, 2);
		bb.putShort(0, magic06);
		os.write(bits, 0, 2);
		bb.putShort(0, width);
		os.write(bits, 0, 2);
		bb.putShort(0, height);
		os.write(bits, 0, 2);
		bb.putShort(0, width_shadow);
		os.write(bits, 0, 2);

		os.write(l2_width);

		os.write(l2_height);
		
		bb.putShort(0, hot1);
		os.write(bits, 0, 2);
		bb.putShort(0, hot2);
		os.write(bits, 0, 2);
		bb.putShort(0, hot3);
		os.write(bits, 0, 2);
		bb.putShort(0, hot4);
		os.write(bits, 0, 2);
		
		bb.putInt(0, magic18);
		os.write(bits, 0, 4);
		
		if(doCompression) {
			byte [] compressed = Utils.packFormat04Frame(bitmap);
			os.write(compressed);
		} else {
			os.write(bitmap);
		}
		
		if( (hasPrivatePalette() && keepPrivatePalette) || forcePalette) {
			bb.putInt(0, 0x01000000);
			os.write(bits, 0, 4);
			
			if(hasPrivatePalette()) {
				os.write(private_palette);
			} else {
				os.write(current_palette == null ? greyPalette : current_palette);
			}
		}
	}
	
	public BufferedImage getImage()
	{
		if(bitmap == null)
			return null;
		
		if(bi==null)
			bi = makeImage(bitmap, width, height);
		
		return bi;
	}

	public BufferedImage getARGBImage()
	{
		if(bitmap == null)
			return null;
		
		if(argbbi==null)
			argbbi = makeARGBImage(bitmap, width, height);
		
		return argbbi;
	}
	
	public BufferedImage makeImage(byte [] frameBuffer, int fbw, int fbh)
	{
		BufferedImage bi;
		
		if(current_palette==null)
		{
			bi = new BufferedImage(fbw, fbh, BufferedImage.TYPE_BYTE_GRAY);
		}
		else
		{
			IndexColorModel icm = new IndexColorModel(8, 256, current_palette, 0, false);
			bi = new BufferedImage(fbw, fbh, BufferedImage.TYPE_BYTE_INDEXED, icm);
		}
		
		bi.getRaster().setDataElements(0, 0, fbw, fbh, frameBuffer);
		
		return bi;
	}

	public BufferedImage makeARGBImage(byte [] frameBuffer, int fbw, int fbh)
	{
		BufferedImage bi;

		bi = new BufferedImage(fbw, fbh, BufferedImage.TYPE_INT_ARGB);
		
		byte [] imgPal = current_palette;

		if(imgPal==null)
		{
			imgPal = greyPalette;
		}
		
		int [] raster = new int [fbw * fbh];
		
		for(int yi = 0; yi < fbh; yi++) {
			for(int xi = 0; xi < fbw; xi++) {
				int pindex = ((int)frameBuffer[fbw * yi + xi])&0xFF;
				int argbcolor =
					  0xFF000000 
					| ( ( ((int)imgPal[pindex*3])   & 0xFF ) << 16 )
					| ( ( ((int)imgPal[pindex*3+1]) & 0xFF ) <<  8 )
					|   ( ((int)imgPal[pindex*3+2]) & 0xFF )
					;
				raster[yi * fbw + xi] = (pindex==0)?0:argbcolor;
			}
		}
		
		bi.getRaster().setDataElements(0, 0, fbw, fbh, raster);
		
		return bi;
	}
	
	public byte [] getOverlay(byte [] frameBuffer, int [] newwh)
	{
		if(bitmap == null)
			return frameBuffer;
		
		int fbw = newwh[0];
		int fbh = newwh[1];
		
		int nw = width;
		int nh = height;
		
		if(fbw>nw)
			nw = fbw;
		if(fbh>nh)
			nh = fbh;
		
		if(newwh!=null)
		{
			newwh[0] = nw;
			newwh[1] = nh;
		}
		
		if(nw!=fbw || nh!=fbh)
		{
			byte [] overlayed = new byte [nw*nh];
		
			for(int ph=0; ph<fbh; ph++)
				System.arraycopy(frameBuffer, fbw*ph, overlayed, nw*ph, fbw);
			
			frameBuffer = overlayed;
		}
		
		for(int y=0; y<height; y++)
			for(int x=0; x<width; x++)
			{
				byte col = bitmap[y*width+x];
				if(col!=0)
					frameBuffer[y*nw+x] = col;
			}
		
		return frameBuffer;
	}
	
	public void setPalette(byte [] palette)
	{
		if(current_palette == palette)
			return;
		
		if(palette!=null)
			if(palette.length!=768)
				return;
		
		current_palette = palette;
		
		bi = null;
	}
	
	public boolean hasPrivatePalette()
	{
		return (private_palette!=null);
	}
	
	public void setPrivatePalette()
	{
		if(private_palette!=null)
			current_palette = private_palette;
	}
	
	public BufferedImage getImage(byte [] palette)
	{
		setPalette(palette);

		return getImage();		
	}

	public BufferedImage getARGBImage(byte [] palette)
	{
		setPalette(palette);

		return getARGBImage();		
	}
	
	public String toString()
	{
		return
			String.format
			(
				  "BitMap %dx%d (%d, %dx%d) compression: %d %d.%d.%d.%d (magic: %d, %d, %d)"
				, width, height, width_shadow, l2_width, l2_height
				, compression, hot1, hot2, hot3, hot4, magic00, magic06, magic18
			);
	}
	
	public short getChunkId()
	{
		return cid;
	}
	
	public int getSubChunkNumber()
	{
		return scnum;
	}
	
	public int getWidth()
	{
		return width;
	}
	
	public int getHeight()
	{
		return height;
	}
	
	public byte [] getBitmap()
	{
		return bitmap;
	}
	
	public int countPaletteIndex(byte pi) {
		if(bitmap == null)
			return -1;
		
		int r = 0;
		
		for(int yi = 0; yi < height; yi++) {
			for(int xi = 0; xi < width; xi++) {
				if(bitmap[yi * width + xi] == pi)
					r++;
			}
		}
		
		return r;
	}
	
	public boolean isTotalBlack() {
		for(int yi = 0; yi < height; yi++) {
			for(int xi = 0; xi < width; xi++) {
				if(bitmap[yi * width + xi] != 0)
					return false;
			}
		}
		
		return true;
	}
	
	public int getHot1() {
		return hot1;
	}
	
	public int getHot2() {
		return hot2;
	}
	
	public int getHot3() {
		return hot3;
	}

	public int getHot4() {
		return hot4;
	}
}
