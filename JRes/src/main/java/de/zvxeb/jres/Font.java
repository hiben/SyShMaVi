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

import java.awt.Dimension;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Font {

	private boolean colored;

	private int first_char;
	private int last_char;
	
	private int nchars;
	private int biggest_char_width;

	private int width;

	private int height;

	private int[] xpos;

	private byte[] bitmap;
	
	private static char [] charmap;
	private static char [] charmap_f2_c604; // font #2 in chunk #604 has two zeros... 
	
	private static byte [] r_charmap;
	private static byte [] r_charmap_f2_c604;
	
	public Font(ByteBuffer bb) {
		int pos = bb.position();

		colored = bb.get() != 0;
		
		bb.position(bb.position() + 0x23);

		first_char = ((int)bb.getShort())&0xFFFF;
		last_char = ((int)bb.getShort())&0xFFFF;
		
		bb.position(bb.position() + 0x20);

		int offs_xpos = bb.getInt();
		int offs_bits = bb.getInt();
		
		width = ((int)bb.getShort())&0xFFFF;
		height = ((int)bb.getShort())&0xFFFF;

		int nxpos = (last_char - first_char) + 2;

		xpos = new int[nxpos];

		bb.position(pos + offs_xpos);

		for (int i = 0; i < nxpos; i++)
			xpos[i] = ((int)bb.getShort())&0xFFFF;

		bitmap = new byte[width * height];

		bb.position(pos + offs_bits);

		bb.get(bitmap);
		
		nchars = (last_char - first_char) + 1;
		
		biggest_char_width = biggestCharWidth();
	}
	
	public Dimension stringBounds(byte [] text, int offs, int length) {
		int sx = 0;

		int chno, cx, cw;

		for (int ci = 0; ci < length; ci++) {
			int c = ((int) text[offs + ci]) & 0xFF;

			if (c > last_char)
				c = (Character.toUpperCase(c) & 0xFF);

			if(c >= first_char && c <= last_char)
			{
				chno = c - first_char;
				cx = xpos[chno];
				cw = xpos[chno+1] - cx;
				
				sx += cw;
			}
			else
			{
				sx += biggest_char_width;
			}
		}
		
		return new Dimension(sx, height);
	}
	
	public void renderString(byte[] text, int offs, int length,
			BufferedImage bi, int x, int y, int rgbcol, int rgbcolf, byte [] palette) {
		int iwidth = bi.getWidth();
		int iheight = bi.getHeight();
		
		int bot_y = height;
		
		if(bot_y + y > iheight)
			bot_y = iheight - y;

		int chno, cx, cw;
		
		int sx = x;
		
		int ex;
		
		for (int ci = 0; ci < length; ci++) {
			int c = ((int) text[offs + ci]) & 0xFF;

			if (c > last_char)
				c = (Character.toUpperCase(c) & 0xFF);

			if(c >= first_char && c <= last_char)
			{
				chno = c - first_char;
				cx = xpos[chno];
				cw = xpos[chno+1] - cx;
				
				ex = sx + cw;
				
				if(ex > iwidth)
					ex = iwidth;
				
				for(int line = 0; line<bot_y; ++line)
				{
					for(int pix = 0; pix < ex-sx; ++pix)
					{
						if(colored)
						{
							int index = ((int)bitmap[ (line * width) + pix + cx ])&0xFF;
							int color = index == 0 ? 0 : rgbcol;
							if(color > 0 && palette!=null) {
								color = 0xFF000000 | (palette[index * 3] << 16) | (palette[index * 3 + 1] << 8) | palette[index * 3 + 2];
							}
							if(color!=0)
								bi.setRGB(sx+pix, y + line, color);
						}
						else
						{
							if( ( bitmap[line * width  + ((pix + cx) >>> 3)] & (0x80 >>> ((pix + cx) % 8)) ) !=0 )
							{
								bi.setRGB(sx+pix, y + line, rgbcol);
							}
						}
					}
				}
				
				sx += cw;
			}
			else
			{
				ex = sx + biggest_char_width;
				
				if(ex > iwidth)
					ex = iwidth;
				
				for(int line = 0; line<bot_y; ++line)
				{
					for(int pix = 0; pix < ex-sx; ++pix)
					{
						if((line==0 || line == (height-1)) && pix < biggest_char_width-2)
						{
							bi.setRGB(sx+pix, y + line, rgbcolf);
							continue;
						}
						
						if(pix == 0 || pix == (biggest_char_width-2))
						{
							bi.setRGB(sx+pix, y + line, rgbcolf);
						}
					}
				}
				
				sx += biggest_char_width;
			}
		}
	}

	public void renderString(byte[] text, int offs, int length,
			BufferedImage bi, int x, int y, byte col) {
		int iwidth = bi.getWidth();
		int iheight = bi.getHeight();
		
		int bot_y = height;
		
		if(bot_y + y > iheight)
			bot_y = iheight - y;

		WritableRaster wr = bi.getRaster();

		int chno, cx, cw;
		
		int sx = x;
		
		int ex;
		
		byte [] acolor = new byte [1];
		acolor[0] = col;
		
		for (int ci = 0; ci < length; ci++) {
			int c = ((int) text[offs + ci]) & 0xFF;

			if (c > last_char)
				c = (Character.toUpperCase(c) & 0xFF);

			if(c < first_char || c > last_char)
				c = ' ';
			
			if(c >= first_char && c <= last_char)
			{
				chno = c - first_char;
				cx = xpos[chno];
				cw = xpos[chno+1] - cx;
				
				ex = sx + cw;
				
				if(ex > iwidth)
					ex = iwidth;
				
				for(int line = 0; line<bot_y; ++line)
				{
					for(int pix = 0; pix < ex-sx; ++pix)
					{
						if(colored)
						{
							acolor[0] = bitmap[line * width + pix + cx];
							wr.setDataElements(sx+pix, y + line, acolor);
						}
						else
						{
							if( ( bitmap[line * width  + ((pix + cx) >> 3)] & (0x80>>((pix + cx)%8)) ) !=0 )
							{
								wr.setDataElements(sx+pix, y + line, acolor);
							}
						}
					}
				}
				
				sx += cw;
			}
			else
			{
				sx += biggest_char_width;
			}
		}
	}
	
	private int biggestCharWidth()
	{
		int bcw = 0;
		for(int i=0; i<nchars; i++)
		{
			int cw = xpos[i+1] - xpos[i];
			if(bcw < cw)
				bcw = cw;
		}
		
		return bcw;
	}
	
	public int lineWidth(byte [] text, int offs, int length)
	{
		int w = 0;
		for(int i=0; i<length; i++)
		{
			int c = ((int)text[i])&0xFF;
			
			if(c < first_char || c > last_char)
				w += biggest_char_width;
			else
				w += xpos[i+1] - xpos[i];
		}
		
		return w;
	}
	
	public BufferedImage createFontView(int back00, int back10, int back01, int back11, int color0, int color1, int colorf, byte [] palette)
	{
		int iwidth = biggest_char_width * 16;//longest_line;
		int iheight = 16 * (height+1);

		BufferedImage bi = new BufferedImage(iwidth, iheight, BufferedImage.TYPE_INT_RGB);

		int [] back00area = new int [iwidth * biggest_char_width];
		int [] back10area = new int [iwidth * biggest_char_width];
		int [] back01area = new int [iwidth * biggest_char_width];
		int [] back11area = new int [iwidth * biggest_char_width];
		
		Arrays.fill(back00area, back00);
		Arrays.fill(back10area, back10);
		Arrays.fill(back01area, back01);
		Arrays.fill(back11area, back11);
		
		for(int ty=0; ty<16; ty++)
		{
			for(int tx=0; tx<16; tx++)
			{
				int [] area =
					((ty&1)!=0)
						?((tx&1)!=0)
							?back11area
							:back10area
						:((tx&1)!=0)
							?back01area
							:back00area;
			bi.setRGB(tx*biggest_char_width, ty*(height+1), biggest_char_width, height+1, area, 0, biggest_char_width);
			}
		}
		
		byte [] tmpchar = new byte [1];

		int l = 0;
		
		int c = 0;
		while(c < 256)
		{
			for(int s=0; s<16; s++)
			{
				tmpchar[0] = (byte)(c++&0xFF);
				
				int color = ((s&1)!=0)?color1:color0;
			
				renderString(tmpchar, 0, 1, bi, s*biggest_char_width, l * (height+1), color, colorf, palette);
			}
			l++;
		}
		
		return bi;
	}

	public static String decodeSSString(byte [] data, int offs, int len, boolean wierdFont)
	{
		char [] charmap = wierdFont?getCharMapF2C604():getCharMap();
		
		StringBuilder sb = new StringBuilder();
		
		for(int i=0; i<len; i++)
			sb.append(charmap[((int)data[offs+i])&0xFF]);
		
		return sb.toString();
	}
	
	public static byte [] encodeSSString(String s, boolean wierdFont)
	{
		byte [] result = new byte [s.length()]; // only Latin-1 please...
		char [] tmp = new char [s.length()];
		
		s.getChars(0, s.length(), tmp, 0);
		
		byte [] cm = wierdFont?getReverseCharMapF2C604():getReverseCharMap();

		for(int i=0; i<tmp.length; i++)
			result[i] = cm[tmp[i]];
		
		return result;
	}
	
	/**
	 * Creates a mapping from SystemShock characters to ISO-8859-1/Unicode characters
	 * (ISO-8859-1 is a subset of Unicode)
	 * @return mapping from SystemShock chars to Java chars
	 */
	public static char [] getCharMap()
	{
		if(charmap==null)
		{
			charmap = new char [256];
			
			for(int i=0; i<128; i++)
				charmap[i] = (char)i; // first 128 chars are ASCII
			
			// the rest seems to be some informal cramming of characters... (?)
						
			int c = 128;
			
			// 0x80
			charmap[c++] = 0xC7; // 'Ç'
			charmap[c++] = 0xFC; // 'ü'
			charmap[c++] = 0xE9; // 'é'
			charmap[c++] = 0xE2; // 'â'
			
			charmap[c++] = 0xE4; // 'ä'
			charmap[c++] = 0xE0; // 'à'
			charmap[c++] = 0xE5; // 'å'
			charmap[c++] = 0xE7; // 'ç'

			charmap[c++] = 0xEA; // 'ê'
			charmap[c++] = 0xE8; // 'è'
			charmap[c++] = 0xEB; // 'ë'
			charmap[c++] = 0xEF; // 'ï'

			charmap[c++] = 0xEE; // 'î'
			charmap[c++] = 0xEC; // 'ì'
			charmap[c++] = 0xC4; // 'Ä'
			charmap[c++] = 0xC5; // 'Å'

			// 0x90
			charmap[c++] = 0xC9; // 'É'
			charmap[c++] = 0x20; // ' ' font 602 has 'æ' (E6), 606 / 611 'E', others empty
			charmap[c++] = 0x20; // ' ' font 602 has 'Æ' (C6), 606 / 611 'E', others empty
			charmap[c++] = 0xF4; // 'ô'

			charmap[c++] = 0xF6; // 'ö'
			charmap[c++] = 0xF2; // 'ò'
			charmap[c++] = 0xFB; // 'û'
			charmap[c++] = 0xF9; // 'ù'

			charmap[c++] = 0xFF; // 'ÿ'
			charmap[c++] = 0xD6; // 'Ö'
			charmap[c++] = 0xDC; // 'Ü'
			charmap[c++] = 0xA2; // '¢'

			charmap[c++] = 0xA3; // '£'
			charmap[c++] = 0xA5; // '¥'
			charmap[c++] = 0xC6; // 'Æ' 
			charmap[c++] = 0x20; // ' '

			/* 28.09.2008
			 * I have no idea what character 9E is. It looks
			 * like an A where the right vertical line has a plus added 
			 * at the bottom... Definitly its not Æ ('AE', C6)...
			 * 
			 */
			
			// 0xA0
			charmap[c++] = 0xE1; // 'á'
			charmap[c++] = 0xED; // 'í'
			charmap[c++] = 0xF3; // 'ó'
			charmap[c++] = 0xFA; // 'ú'
			
			charmap[c++] = 0xF1; // 'ñ'
			charmap[c++] = 0xD1; // 'Ñ'
			
			while(c<0xE1)
				charmap[c++] = 0x20;
			
			// 0xE1
			charmap[c++] = 0xDF; // 'ß'

			while(c<0xFF)
				charmap[c++] = 0x20;
		}
		
		return charmap;
	}
	
	/**
	 * Font mapping for the wierd font #2 (chunk 604) (two zeros)
	 * @return mapping from SystemShock chars to Java chars
	 */
	public static char [] getCharMapF2C604()
	{
		if(charmap_f2_c604==null)
		{
			charmap_f2_c604 = new char [256];
			
			int c = 0;
			
			while(c<='9')
				charmap_f2_c604[c] = (char)c++; // chars up to '9' are ASCII

			charmap_f2_c604[c++] = '0'; // second zero...
			
			while(c<=128)
				charmap_f2_c604[c] = (char)(c-1);
			
			// the rest seems to be some informal cramming of characters... (?)
						
			c = 129;
			
			// 0x81
			charmap_f2_c604[c++] = 0xC7; // 'Ç'
			charmap_f2_c604[c++] = 0xFC; // 'ü'
			charmap_f2_c604[c++] = 0xE9; // 'é'
			charmap_f2_c604[c++] = 0xE2; // 'â'
			
			charmap_f2_c604[c++] = 0xE4; // 'ä'
			charmap_f2_c604[c++] = 0xE0; // 'à'
			charmap_f2_c604[c++] = 0xE5; // 'å'
			charmap_f2_c604[c++] = 0xE7; // 'ç'

			charmap_f2_c604[c++] = 0xEA; // 'ê'
			charmap_f2_c604[c++] = 0xE8; // 'è'
			charmap_f2_c604[c++] = 0xEB; // 'ë'
			charmap_f2_c604[c++] = 0xEF; // 'ï'

			charmap_f2_c604[c++] = 0xEE; // 'î'
			charmap_f2_c604[c++] = 0xEC; // 'ì'
			charmap_f2_c604[c++] = 0xC4; // 'Ä'
			charmap_f2_c604[c++] = 0xC5; // 'Å'

			// 0x91
			charmap_f2_c604[c++] = 0xC9; // 'É'
			charmap_f2_c604[c++] = 0x20; // ' '
			charmap_f2_c604[c++] = 0x20; // ' '
			charmap_f2_c604[c++] = 0xF4; // 'ô'

			charmap_f2_c604[c++] = 0xF6; // 'ö'
			charmap_f2_c604[c++] = 0xF2; // 'ò'
			charmap_f2_c604[c++] = 0xFB; // 'û'
			charmap_f2_c604[c++] = 0xF9; // 'ù'

			charmap_f2_c604[c++] = 0xFF; // 'ÿ'
			charmap_f2_c604[c++] = 0xD6; // 'Ö'
			charmap_f2_c604[c++] = 0xDC; // 'Ü'
			charmap_f2_c604[c++] = 0xA2; // '¢'

			charmap_f2_c604[c++] = 0xA3; // '£'
			charmap_f2_c604[c++] = 0xA5; // '¥'
			charmap_f2_c604[c++] = 0xC6; // 'Æ'
			charmap_f2_c604[c++] = 0x20; // ' '

			// 0xA1
			charmap_f2_c604[c++] = 0xE1; // 'á'
			charmap_f2_c604[c++] = 0xED; // 'í'
			charmap_f2_c604[c++] = 0xF3; // 'ó'
			charmap_f2_c604[c++] = 0xFA; // 'ú'
			
			charmap_f2_c604[c++] = 0xF1; // 'ñ'
			charmap_f2_c604[c++] = 0xD1; // 'Ñ'
			
			while(c<0xE1)
				charmap_f2_c604[c++] = 0x20;
			
			// 0xE2
			charmap_f2_c604[c++] = 0xDF; // 'ß'

			while(c<0xFF)
				charmap_f2_c604[c++] = 0x20;
		}
		
		return charmap_f2_c604;
	}
	
	public static byte [] getReverseCharMap()
	{
		if(r_charmap==null)
		{
			r_charmap = new byte [256];
			
			char [] cm = getCharMap();
			
			for(int i=0; i<r_charmap.length; i++)
			{
				r_charmap[i] = (byte)' ';
				for(int c=0; c<cm.length; c++)
					if(cm[c] == i)
						r_charmap[i] = (byte)(c&0xFF);
			}
		}
		
		return r_charmap;
	}
	
	public static byte [] getReverseCharMapF2C604()
	{
		if(r_charmap_f2_c604==null)
		{
			r_charmap_f2_c604 = new byte [256];
			
			char [] cm = getCharMapF2C604();
			
			for(int i=0; i<r_charmap_f2_c604.length; i++)
			{
				r_charmap_f2_c604[i] = (byte)' ';
				for(int c=0; c<cm.length; c++)
					if(cm[c] == i)
						r_charmap_f2_c604[i] = (byte)(c&0xFF);
			}
		}
		
		return r_charmap_f2_c604;
	}

}
