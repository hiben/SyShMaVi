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

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.zvxeb.jres.util.Util;

public class Utils {
	
	private static final boolean doDebugF04Unpack =
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.Utils.unpackF04Debug", "false"));

	private static final boolean doDebugF04Pack =
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.Utils.packF04Debug", "false"));
	
	public static boolean debugF0F = true;
	
	private static void debugF04Pack(String fmt, Object...args) {
		if(!doDebugF04Pack)
			return;
		printf(fmt, args);
	}

	private static void debugF04Unpack(String fmt, Object...args) {
		if(!doDebugF04Unpack)
			return;
		printf(fmt, args);
	}
	
	public static void printf(String fmt, Object...args) {
		System.out.print(String.format(Locale.US, fmt, args));
	}
	
	private static MessageDigest md5;
	
	static
	{
		try 
		{
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) 
		{
			System.err.println("Error. No MD5 digest available! Fix VM!");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static byte [] md5sum(byte [] data)
	{
		return md5.digest(data);
	}
	
	public static boolean sameArrays(byte [] h1, byte [] h2) 
	{
		if(h1 == null && h2 != null)
			return false;
		if(h1 != null && h2 == null)
			return false;
		
		if(h1.length != h2.length)
			return false;
		
		for(int i=0; i<h1.length; i++)
			if(h1[i] != h2[i])
				return false;
		
		return true;
	}
	
	public static class BitHelper {
		private ByteBuffer bb;
		
		private int unpack_nbits;
		private int unpack_word;
		
		public BitHelper(ByteBuffer bb) {
			this.bb = bb;
			unpack_nbits = 0;
			unpack_word = 0;
		}
		
		public int getBits(int bits, boolean update) {
			while(unpack_nbits < bits)
			{
				unpack_word <<= 8;
				unpack_word |= ((int)this.bb.get())&0xFF;
				unpack_nbits += 8;
			}
			
			int r = (unpack_word >> (unpack_nbits - bits)) & (0xFFFFFFFF >>> (32 - bits));
			
			if(update) {
				unpack_nbits -= bits;
			}
			
			return r;
		}
		
		public void setBitDelta(int delta) {
			unpack_nbits -= delta;
		}
		
		public void setBits(int bits) {
			unpack_nbits = bits;
		}
		
		public int getCurrentBits() {
			return unpack_nbits;
		}
	}
	
	public static class BitWriter {
		private OutputStream os;
		
		private int pack_word = 0;
		private int pack_nbits = 0;
		
		public BitWriter(OutputStream os) {
			this.os = os;
		}
		
		public void putBits(int value, int bits) throws IOException {
			if(bits<1)
				return;
			while(bits > 0) {
				while(pack_nbits >= 8) {
					os.write( (byte)((pack_word >>> (pack_nbits - 8)) & 0xFF) );
					pack_nbits -= 8;
				}
				int b = bits;
				if(b + pack_nbits > 16) {
					b = 16 - pack_nbits;
				}
				pack_word <<= b;
				
				pack_word |= ((value >>> (bits - b)) & (0xFFFF >>> (16 - b)));
				
				pack_nbits += b;
				bits -= b;
			}
			while(pack_nbits >= 8) {
				os.write( (byte)((pack_word >>> (pack_nbits - 8)) & 0xFF) );
				pack_nbits -= 8;
			}
		}
		
		public void flush() throws IOException {
			if(pack_nbits > 0) {
				os.write( pack_word << (8 - pack_nbits) );
				pack_nbits = 0;
			}
		}
	}
	
	public static class BitBufferWriter {
		private ByteBuffer bb;
		
		private int pack_word = 0;
		private int pack_nbits = 0;
		
		public BitBufferWriter(ByteBuffer bb) {
			this.bb = bb;
		}
		
		public void putBits(int value, int bits) {
			if(bits<1)
				return;
			while(bits > 0) {
				while(pack_nbits >= 8) {
					bb.put( (byte)((pack_word >>> (pack_nbits - 8)) & 0xFF) );
					pack_nbits -= 8;
				}
				int b = bits;
				if(b + pack_nbits > 16) {
					b = 16 - pack_nbits;
				}
				pack_word <<= b;
				
				pack_word |= ((value >>> (bits - b)) & (0xFFFF >>> (16 - b)));
				
				pack_nbits += b;
				bits -= b;
			}
			while(pack_nbits >= 8) {
				bb.put( (byte)((pack_word >>> (pack_nbits - 8)) & 0xFF) );
				pack_nbits -= 8;
			}
		}
		
		public void flush() {
			if(pack_nbits > 0) {
				bb.put( (byte)(pack_word << (8 - pack_nbits)) );
				pack_nbits = 0;
			}
		}
	}


	public static class Format0FDecoder
	{
		private byte [] intial_palette, palette;
		
		private byte [] frameBuffer;
		
		private int width, height;
		
		private ByteBuffer fbb, aux_bb, dict_bb;
		private int aux_initial;
		
		private BitHelper bits;
		
		public Format0FDecoder(int width, int height, byte [] palette)
		{
			assert(width>0);
			assert(height>0);
			assert(palette!=null);
			assert(palette.length==768);
			
			this.width = width;
			this.height = height;

			this.intial_palette = palette;
			
			frameBuffer = new byte [width * height];
			
			reset();
		}
		
		private int sequence = 0;
		private int frame = 0;
		
		public void reset()
		{
			sequence = 0;
			frame = 0;
			setPalette(intial_palette);
		}
		
		public void setPalette(byte [] palette)
		{
			assert(palette!=null);
			assert(palette.length==768);
			
			this.palette = palette;
			Arrays.fill(frameBuffer, (byte)0);
		}
		
		public byte [] getPalette()
		{
			return palette;
		}
		
		public byte [] getFrameBuffer()
		{
			return frameBuffer;
		}
		
		public void setAuxPal(ByteBuffer aux_bb)
		{
			this.aux_bb = aux_bb;
			aux_bb.order(ByteOrder.LITTLE_ENDIAN);
			aux_initial = aux_bb.position();
		}
		
		public ByteBuffer unpackDictionary(ByteBuffer packed_bb)
		{
			packed_bb.order(ByteOrder.LITTLE_ENDIAN);
			int dict_size = packed_bb.getInt();
			
			byte [] buffer = new byte [dict_size + 3];
			
			ByteBuffer dbb = ByteBuffer.wrap(buffer);
			dbb.order(ByteOrder.LITTLE_ENDIAN);
			
			while(dbb.position()<dict_size)
			{
				int tmp = packed_bb.getInt();
				int val = tmp&0x00FFFFFF;
				int count = (tmp>>24)&0xFF;
				for(int c=0; c<count; c++)
				{
					dbb.putInt(val); // the highest byte gets overwritten later...
					dbb.position(dbb.position()-1);
				}
			}
			
			dbb.position(0);
			
			return dbb;
		}
	
		private static void addToIntMap(Map<Integer, Integer> m, Integer i) {
			Integer old = m.get(i);
			if(old==null)
				old = 0;
			m.put(i, old+1);
		}
		
		private static float averageCount(Map<Integer, Integer> m) {
			float count = 0;
			for(Map.Entry<Integer, Integer> mi : m.entrySet()) {
				count += mi.getValue();
			}
			if(m.size()>0)
				count /= m.size();
			
			return count;
		}

		private static int maxCount(Map<Integer, Integer> m) {
			int count = 0;
			
			for(Map.Entry<Integer, Integer> mi : m.entrySet()) {
				if(count < mi.getValue())
					count = mi.getValue();
			}
			
			return count;
		}

		private static int medianCount(Map<Integer, Integer> m) {
			if(m.size()==0)
				return 0;
			
			ArrayList<Integer> counts = new ArrayList<Integer>();
			counts.addAll(m.values());
			Collections.sort(counts);
			
			return counts.get((counts.size()-1)/2);
		}

		private static int countOver(Map<Integer, Integer> m, Integer i) {
			int count = 0;

			for(Integer c : m.values()) {
				if(c > i)
					count++;
			}
			
			return count;
		}

		private static int minCount(Map<Integer, Integer> m) {
			int count = Integer.MAX_VALUE;
			
			for(Map.Entry<Integer, Integer> mi : m.entrySet()) {
				if(count > mi.getValue())
					count = mi.getValue();
			}
			
			return count;
		}
		
		private void analyzeDictionary() {
			Map<Integer, Integer> wordCounter = new TreeMap<Integer, Integer>();
			if(dict_bb!=null) {
				for(Integer offs : offsets) {
					int value = dict_bb.getInt(offs * 3) & 0xFFFFF; // 20bit word without count
					addToIntMap(wordCounter, value);
				}
			}
			System.out.println("Offsets: " + wordCounter.size() + " MaxCount: " + maxCount(wordCounter) + " average: " + averageCount(wordCounter) + " median " + medianCount(wordCounter) + " over 1 " + countOver(wordCounter, 1) + " over 2 " + countOver(wordCounter, 2) + " over 3 " + countOver(wordCounter, 3) + " over 4 " + countOver(wordCounter, 4) + " over 5 " + countOver(wordCounter, 5));
		}
		
		private Set<Integer> offsets = new TreeSet<Integer>();
		private Map<Integer, Integer> offsetCountMap12 = new TreeMap<Integer, Integer>();
		private Map<Integer, Integer> offsetCountMap20 = new TreeMap<Integer, Integer>();
		private Map<Integer, Integer> offsetCountMap20i = new TreeMap<Integer, Integer>();
		
		private List<Integer> frameOffsets = new LinkedList<Integer>();
		private List<Integer> frameIndirections = new LinkedList<Integer>();
		
		public void setPackedDictionary(ByteBuffer packed_bb)
		{
			analyzeDictionary();
			System.out.println("Entries in 12bit offsets: " + offsetCountMap12.size() + " avg: " + averageCount(offsetCountMap12) + " max: " + maxCount(offsetCountMap12) + " min: " + minCount(offsetCountMap12));
			System.out.println("Entries in 20bit offsets: " + offsetCountMap20.size() + " avg: " + averageCount(offsetCountMap20) + " max: " + maxCount(offsetCountMap20) + " min: " + minCount(offsetCountMap20));
			System.out.println("Entries in 20bit i-offsets: " + offsetCountMap20i.size() + " avg: " + averageCount(offsetCountMap20i) + " max: " + maxCount(offsetCountMap20i) + " min: " + minCount(offsetCountMap20i));
			
			
			offsets.clear();
			offsetCountMap12.clear();
			offsetCountMap20.clear();
			offsetCountMap20i.clear();
			
			
			dict_bb = unpackDictionary(packed_bb);
			sequence++;
			frame = 0;
			System.out.println(" @ sequence " + sequence);
		}
		
		/*
		 * Quick note on the offsets:
		 * A normal offset is a 12bit value for a dictword. The dictword contains a count-field.
		 * If there is a count of zero, we are about to fetch a 24bit offset
		 *  - 1. get the dict-word from 12bit offset
		 *  - 2. forget last 12 bit (the old offset)
		 *  - 3. read four more bits
		 *  - 4. form new offset by dictword + last four bits (24bits + 0-15; needs fewer 24bit values in stream)
		 *  - 5. get new dict-word from offset (24bit value)
		 *  -- 6. if there is again a zero count, the new offset is just a pointer to a new 24bit base
		 *   - forget last four bits and repeat step 3. (there should _not_ be another zero count...)
		 * 
		 *
		 * the variable bit-skipping allows for recycling of lower bits in the upper bits of the next value
		 * I'm not sure if a count of zero would be allowed in the worst case offset path (12 -> 24 -> 24). The videos
		 * do not do this as the older algorithm would have worked wrong in that case (but worked well).
		 */
		/*
		 * some more notes on the offsets
		 * the first offset is a 12bit value and must point to a zero-count dictword to
		 * trigger getting a 20bit offset. The position of this new offset is derived from
		 * the current 20bit dictword plus a 4bit value.
		 * If this offset also has a zero count, this process starts over.
		 * 
		 * What can be gained by the second iteration ?
		 * 
		 * When all dict-words are stored, some reside in the 12bit accessible area and some will
		 * be beyond. For each block of 16 '20bitters' a 12bit dictword is needed as a pointer.
		 * I assume a high watermark approach was used to keep enough free 12bit slots.
		 * In the 20bit area, there is virtually no limit on the dictwords. Having some secondary tables
		 * in that area might simplify the approach for storing 12bit words.
		 * The combination of a 12- and 20bit offset results in 256 possible dictwords for one 12bit 
		 * offset.
		 * 
		 * hiben - 11.01.16
		 * 
		 * even more info:
		 * As predicted the 12 bit offsets are used most (some of them more than 3000 times in a
		 * dictionaries lifetime). The single 20bit offsets follow but are a lot less used.
		 * In some cases there are less than 4096 offsets when combining 12 and 20bit. I guess that using
		 * the 4bit selector is more efficient than using other 12bit offsets - maybe it would not fit when
		 * only using 12bit (without selector).
		 * double indirection is used very seldom and these dictwords are only accessed less than ten times 
		 * for the dictionary lifetime. (statistics are from the intro-sequence).
		 * Still some 12bit offsets are only used once at all. My suggestion: The dictionary is build online
		 * while compressing - keeping all dictwords in memory for reference counting was out of question
		 * in 1993 as it seems...
		 * 
		 * Unresolved: some single indirections could be made using a 12bit offset only. This might be 
		 * an artefact from the compression algorithm...
		 * 
		 * hiben - 11.01.17
		 * 
		 * Analyzing the dictionary reveals that the same word (without looking at count) can be stored many 
		 * times (more than the bits in the count field would suggest).
		 * I assume that the encoder used limited memory to encode the video stream and therefore only
		 * kept a certain amount of words. Maybe I need to trace the used offsets over the frames to see
		 * a pattern...
		 * 
		 * update: did this... confusing. frame 1 does not necessarily use the first offset which is
		 * okay with my theory of encoding.
		 * there are many one-level indirections to the upper and the lower section of the dictionary
		 * this seems independent of frame further fostering my belief in post-sequence compression.
		 * two-level indirection seem to happen mostly in the middle and seldom into the _lower_ area
		 *
		 * The dictionary is not grown from both sides as this would 'confuse' the count bits and make 
		 * offset calculations arkward.
		 * 
		 * In the first sequence of the intro one offset is accessed with a cumulative indirection count of
		 * 25 (but I do not remember, if this was 1 based)
		 * 
		 * hiben - 11.01.24
		 * 
		 */
		public void unpack_dframe_row(int [] rowbuffer)
		{
			int rowbufindex = 0;
			
			for(int x=0; x < width / 4; ++x)
			{
				int offs;
				int dictword;
				int count, type;

				// get 12bit offset for dictword
				offs = bits.getBits(12, false);
				
				dictword = dict_bb.getInt(offs * 3) & 0x00FFFFFF;
				count = (dictword &0x00F00000) >> 20;
				
				int indirections = 0;
			
				if(count != 0) {
					offsets.add(offs);
					addToIntMap(offsetCountMap12, offs);
					if(doDebugF04Unpack) {
						frameOffsets.add(offs);
						frameIndirections.add(0);
					}
				}
				
				// only mark up to 12bits read
				bits.setBitDelta(count);
				
				// initial skip value on zero count
				int bdelta = 12;
				
				/* this loop is used at most twice for all
				 * videos from system shock. 
				 * I'm not yet sure, why there is the need for
				 * more than one new offset since they are all 20 bit...
				 * Maybe the bitstream can be compressed better that way...
				 * hiben - 11.01.16 
				 */
				while(count==0) {
					indirections++;

					bits.setBitDelta(bdelta);
					
					// previous dictword was a pointer into
					// the data. 16 24bit values are reachable from
					// it
					int selector = bits.getBits(4, false) ;
					offs = dictword + selector;
					
					// fetch dictword at this offset
					dictword = dict_bb.getInt(offs * 3) & 0x00FFFFFF;
					count = (dictword &0x00F00000) >> 20;
					
					if(count!=0) {
						if(bdelta == 12) {
							addToIntMap(offsetCountMap20, offs - selector);
						} else {
							addToIntMap(offsetCountMap20i, offs - selector);
						}
						offsets.add(offs);
						if(doDebugF04Unpack) {
							frameOffsets.add(offs);
							frameIndirections.add(indirections);
						}
					}

					// mark up to 4 bits read
					bits.setBitDelta(count);
					
					bdelta = 4; // further skip value on zero count
					/* if we leave this loop here, count was positive and
					 * the correct number of bits was skipped 
					 */ 
				}
				
				type = (dictword & 0x000E0000) >> 17;
				
				if(type<5)
				{
					rowbuffer[rowbufindex++] = dictword;
				}
				else if(type==5)
				{
					int parm;
					parm = bits.getBits(5,true);
					
					// skip to end of line ?
					if(parm==31)
					{
						// this is the increment for x
						// and results in ((width / 4) - 1)
						// at the loop and, x will be further increased
						// and thus the loop will end
						parm = (width / 4 - x) - 1; 
					}
					// 0xA0000 gets shifted by 17 -> type 5 (skip)
					rowbuffer[rowbufindex++] = parm | 0x000A0000;
					x += parm;
				}
				else // this is always type 6
				{
					rowbuffer[rowbufindex] = rowbuffer[rowbufindex-1];
					rowbufindex++;
				}
			}
		}
		
		/* Frame decompression:
		 * The compression encodes changes to the previous frame and decompression starts with
		 * a black picture. Data is stored / read in little endian convention.
		 * Every picture is processed in chunks of 4x4 pixels, starting at the top-left
		 * position. Further each row (of 4 pixels) is processed individually.
		 * For each row a dictionary is decompressed that contains the operations
		 * to perform on each chunk (or skipping of chunks). There exists a dictionary word
		 * for each chunk that is not skipped.
		 * A dictionary word is a 20-bit value where the lower 17 bits contain data and the remaining
		 * 3 upper bits encode the operation. 
		 * There are six known operators, 5 pixel manipulation and one skip operation.
		 * Unless specified otherwise, color index 0 means that the previous color of the pixel is kept.
		 * Most of the modes used bitmasks to select colors. Here each chunk is processed row-wise, starting 
		 * top-left with the lowest bits of the bitmask. Each pixel of the row is processed before increasing
		 * the coloumn. This allows efficient (right) shifting of the bitmask to select the current color.
		 * 
		 * Operator semantics:
		 * 0:	data encodes two 8bit palette indices (c0 lower 8 bits, c1 following 8 bits)
		 * 		c0 is used for all pixels with x=0 and x=2
		 * 		c1 is used for all pixles with x=1 and x=3
		 * 1:	data encodes two 8bit palette indices (same as for case 0)
		 * 		a 16bit bitmask is read from the frame-data and used for each pixel
		 * 2:   data encodes an offset to an auxiliary palette
		 * 		a 32bit bitmask is read from the frame-data. The lowest two bits 
		 * 		are used to select the palette index from the aux-pal (four colors).
		 * 3:   data encodes an offset to an auxiliary palette
		 * 		for the first two rows a 32bit bitmask is read from the frame-data.
		 * 		the lowest 3 bits are used for color selection (8 colors)
		 * 		Before processing the third row, a 16bit bitmask is read from the frame-data.
		 * 		At this point, the old bitmask contains unprocessed 8bits. The new bitmask is therefore
		 * 		shifted by 8 bits to the left and merged with the remaining bitmask.
		 * 		(so it is a 48 bit bitmask - you can form this value at the start if it suits you)
		 * 4:   data encodes an offset to an auxiliary palette
		 * 		for the first two rows a 32bit bitmask is read from the frame-data.
		 * 		the lowest 4 bits are used for color selection (16 colors)
		 * 		Before processing the third row, a 32bit bitmask is read from the frame-data.
		 * 		At this point, the old bitmask is empty. The new bitmask is used.
		 * 		(so it is a 64 bit bitmask - you can form this value at the start if it suits you)
		 * 5:   data encodes a number of chunks to skip. Multiply by four to get the next x-pixel (in your row)
		 * 		Note: There is a special case in the dictionary decoding that forms a skipping to the
		 * 		end of the row. You may want to have a special case in the frame decoding if you feel like it.
		 * 6:   Repeat last operation. You can simplify decoding if doing this step in row-decoding.
		 * ?:   Other values for operators are not seen. I do not think there are more features.
		 */
		public byte [] decompressFrame(ByteBuffer frame_bb)
		{
			fbb = frame_bb;
			fbb.order(ByteOrder.LITTLE_ENDIAN);
			
			int [] rowbuf = new int [256];
			int sec2_offs;
			int pos;
			
			int s2;
			
			pos = fbb.position();
			sec2_offs = ((int)fbb.getShort())&0xFFFF;
			
			bits = new BitHelper(fbb.slice());

			fbb.position(pos + sec2_offs);
			
			for(int y = 0; y < height; y += 4)
			{
				//System.out.println("Unpacking row " + y);
				unpack_dframe_row(rowbuf);
	
				int rowbufindex = 0;
				
				for(int x = 0; x < width; x += 4)
				{
					int dictword = rowbuf[rowbufindex++];
					int type = (dictword & 0x000E0000) >> 17;
					int parm = (dictword & 0x0001FFFF);
					byte c0 = (byte)(parm & 0xFF);
					byte c1 = (byte)((parm >> 8) & 0xFF);
					
					// note: almost all types use color zero to keep the previous pixel
					// EDIT: no SS1 video ever sets color zero this way.
					// (color zero is never set, it is always used to not-change color)
					// - hiben 10.09.13
					switch(type)
					{
					// parm encodes two colors repeated horizontally
					// color zero overwrites previous pixel
					case 0:
						for(int yy=y; yy < y + 4; ++yy)
						{
							frameBuffer[x + width * yy] = c0;
							frameBuffer[x + 1 + width * yy] = c1;
							frameBuffer[x + 2 + width * yy] = c0;
							frameBuffer[x + 3 + width * yy] = c1;
						}
						break;
					// parm encodes two colors, s2 represents a 16 bit bitmask for selection 
					// color zero overwrites previous pixel when specified in high byte (?)
					// CORRECTION: color zero never overwrites anything! - hiben 10.09.13
					case 1:
						s2 = ((int)fbb.getShort())&0xFFFF;
						for(int yy = y; yy < y + 4; ++yy)
						{
							for(int xx = x; xx < x + 4; ++xx)
							{
								if(c1 != 0 && ((s2 & 1)!=0) ) {
									frameBuffer[xx + width * yy] = c1;
								}
								else if(c0 != 0) {
									frameBuffer[xx + width * yy] = c0;
								}
	
								s2 >>>= 1;
							}
						}
						break;
					// parm is an offset to 4 color auxpal, s2 represents a 32 bit bitmask for selecting colors (2 bit)
					case 2:
						s2 = fbb.getInt();
						for(int yy = y; yy < y + 4; ++yy)
						{
							for(int xx = x; xx < x + 4; ++xx)
							{
								byte auxval = aux_bb.get(aux_initial + parm + (s2 & 3));
								if(auxval!=0)
									frameBuffer[xx + width * yy] = auxval;
								s2 >>>= 2;
							}
						}
						break;
					// parm is an offset to 8 color auxpal, s2 represents a 48 bit bitmask for selecting colors (3bit)
					// first a 32 bit integer is read for s2
					// after processing the second row (24 bit read)
					// another short value is read, shifted by 8 bits and added to s2
					//
					// 08.09.10 finally understood that the next mask part is read after
					// the second row and not the first (yy is updated after the check)
					// so this really is a normal 48 bit bitmask...
					case 3:
						s2 = fbb.getInt();
						for(int yy = y; yy < y + 4; ++yy)
						{
							for(int xx = x; xx < x + 4; ++xx)
							{
								byte auxval = aux_bb.get(aux_initial + parm + (s2 & 7));
								if(auxval!=0)
									frameBuffer[xx + width * yy] = auxval;
								s2 >>>= 3;
							}
							
							// update bitmask after second row
							if(yy == y + 1)
								s2 |= (((int)fbb.getShort())&0xFFFF)<<8;
						}
						break;
					// parm is an offset to a 16 color auxpal, s2 represents a 64 bit bitmask for selecting colors (4bit)
					// first a 32 bit integer is read for s2
					// after processing the second row (32 bit) a second 32 bit int is read
					// 08.09.10. see above for enlightenment...
					case 4:
						s2 = fbb.getInt();
						for(int yy = y; yy < y + 4; ++yy)
						{
							for(int xx = x; xx < x + 4; ++xx)
							{
								byte auxval = aux_bb.get(aux_initial + parm + (s2 & 0xF));
								if(auxval!=0)
									frameBuffer[xx + width * yy] = auxval;
								s2 >>>= 4;
							}
	
							// get new bitmask after second row
							if(yy == y + 1)
								s2 = fbb.getInt();
						}
						break;
					// parm is a skip value, multiply by four
					case 5:
						x += 4 * parm;
						break;
					}
				}
			}

			if(doDebugF04Unpack) {
				if (!frameOffsets.isEmpty()) {
					try {
						FileOutputStream fos = new FileOutputStream("sequence.log", true);
						PrintStream ps = new PrintStream(fos);

						Iterator<Integer> indiI = frameIndirections.iterator();
						Iterator<Integer> offsI = frameOffsets.iterator();
						while (offsI.hasNext()) {
							ps.printf(Locale.US, "%d, %d, %d, %d\n", sequence, frame, offsI.next(), indiI.next());
						}

						ps.flush();
						fos.close();
					} catch (FileNotFoundException e) {
					} catch (IOException e) {
					}
					frameOffsets.clear();
					frameIndirections.clear();
				}
			}
			
			frame++;
			return frameBuffer;
		}
		
		public BufferedImage getCurrentFrame()
		{
			IndexColorModel icm = new IndexColorModel(8, 256, palette, 0, false);
			BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, icm);
			bi.getRaster().setDataElements(0, 0, width, height, frameBuffer);
			return bi;
		}

		public BufferedImage getCurrentFrame(BufferedImage target)
		{
			if(target==null)
				return getCurrentFrame();
			target.getRaster().setDataElements(0, 0, width, height, frameBuffer);
			return target;
		}
	}
	
	public static class Format0FEncoder {
		public static final int SKIP_MAX = 30;
		public static final int SKIP_TO_END = SKIP_MAX + 1;
		
		public static final int OP_2_C_SET = (0 << 17);
		public static final int OP_2_C = (1 << 17);
		public static final int OP_4_C = (2 << 17);
		public static final int OP_8_C = (3 << 17);
		public static final int OP_16_C = (4 << 17);
		public static final int OP_SKIP = (5 << 17);
		public static final int OP_REP = (6 << 17);

		private List<Integer> dictWordList = new ArrayList<Integer>();
		private List<Byte> auxPal = new ArrayList<Byte>();
		
		private byte [] frameBuffer;
		private int width, height;
		
		public Format0FEncoder(int width, int height, byte [] frameBuffer) {
			this.width = width;
			this.height = height;
			this.frameBuffer = new byte [width*height];
			if(frameBuffer != null) {
				int len = width * height;
				if(len > frameBuffer.length)
					len = frameBuffer.length;
				System.arraycopy(frameBuffer, 0, this.frameBuffer, 0, len);
			}
		}
		
		public void reset() {
			dictWordList.clear();
			auxPal.clear();
		}
		
		public void processFrame(byte [] fb, LinkedList<Byte> auxPal, OutputStream frameData) throws IOException {
			if(fb==null || fb.length < frameBuffer.length)
				return;
			List<Integer> rowDict = new LinkedList<Integer>();
			
			ArrayList<Byte> dictWordData = new ArrayList<Byte>();
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			BitWriter rowDictWriter = new BitWriter(baos);
			
			Set<Byte> usedColors = new TreeSet<Byte>();
			Set<Byte> tmpColors = new TreeSet<Byte>();
			
			/*
			 * Chunk:
			 * 	0 1 2 3
			 *  4 5 6 7
			 *  8 9 A B
			 *  C D E F
			 */
			int lastOp = 0;
			boolean inSkip = false;
			int skipCount = 0;
			byte [] oldChunk = new byte [16];
			byte [] newChunk = new byte [16];
			for(int cy=0; cy < height; cy+=4) {
				int yindex = cy * width;
				rowDict.clear();
				for(int cx=0; cx < width; cx+=4) {
					boolean noChange = true;
					boolean isCaseZero = true;
					byte c0 = 0, c1 = 0;
					
					// this loop fills the chunk-buffers and determines
					// if the new chunk differs from the old and if the 
					// new chunk is a candidate for case 0
					for(int sy=0; sy<4; sy++) {
						int syindex = yindex + sy * height;
						for(int sx=0; sx<4; sx++) {
							byte o = oldChunk[sy << 2 + sx] = frameBuffer[syindex + sx];
							byte n = newChunk[sy << 2 + sx] = fb[syindex + sx];
							if(o!=n) {
								noChange = false;
							}
						}
						// case 0 is vertically repeated alternating colors
						// determine initial state at first row
						if(sy == 0) {
							if( (newChunk[0] == newChunk[2]) && (newChunk[1] == newChunk[3]) ) {
								// this would be the repeating colors
								c0 = newChunk[0];
								c1 = newChunk[1];
							} else {
								isCaseZero = false;
							}
						} else {
							// check if other rows match but only if still possible
							if(isCaseZero) {
								int si = sy << 2;
								if(!(
									(newChunk[si+0] == c0) && (newChunk[si+1] == c1) && 
									(newChunk[si+0] == newChunk[si+2]) && (newChunk[si+1] == newChunk[si+3])
									)
								) {
									isCaseZero = false;
								}
							}
						}
					}
					// process skipping
					if(inSkip && noChange) {
						skipCount++;
						continue;
					}
					if(!inSkip && noChange) {
						inSkip = true;
						skipCount = 1;
						continue;
					}
					if(inSkip && !noChange) {
						while(skipCount > SKIP_MAX) {
							rowDict.add(OP_SKIP | SKIP_MAX);
							skipCount -= SKIP_MAX;
						}
						rowDict.add(OP_SKIP | skipCount);
						inSkip = false;
					}
					// check cases
					
					// special case
					if(isCaseZero) {
						rowDict.add(OP_2_C_SET | ((((int)c1)&0xFF) << 8) | (((int)c0)&0xFF));
						continue;
					}
					
					// now comes the fun...
					// the chunk buffers contain the pixels in the same
					// sequence as the bitmaps are read later
					
					// one note: color 0 cannot be set. all pictures are
					// implicitly 255 color pictures that do not use color 0
					// (unless it keeps the initial color zero screen which is black...)
					
					// step 1: determine needed colors
					
					for(int i=0; i<16; i++) {
						if(oldChunk[i] != newChunk[i]) {
							usedColors.add(Byte.valueOf(newChunk[i]));
						}
					}
					
					if(usedColors.contains(Byte.valueOf((byte)0))) {
						throw new InvalidParameterException("New framebuffer needs color zero set -> invalid!");
					}
					
					int numColors = usedColors.size();
					
					assert(numColors>0 && numColors<=16);
					
					// check for case one
					if(numColors<3) {
						Byte [] colorIndices = (Byte [])usedColors.toArray();
						
						if(colorIndices.length == 1) {
							colorIndices = new Byte [] { (byte)0, colorIndices[0] };
						}
						
						int bitmask = 0;

						for(int i=0; i<16; i++) {
							bitmask >>>= 1;
							if(newChunk[i] == colorIndices[1].byteValue()) {
								bitmask |= 32768;
							}
						}
						
						// write 16 bit bitmask
						frameData.write(bitmask);
						bitmask>>>=8;
						frameData.write(bitmask);
						
						rowDict.add(OP_2_C | ((((int)colorIndices[1])&0xFF) << 8) | (((int)colorIndices[0])&0xFF));
						
						continue;
					} // end of two color bitmask
					
					// for all other cases we need a suitable auxiliary palette
					
					int caseSize = numColors > 8 ? 16 : numColors > 4 ? 8 : 4;
					
					Byte [] currentAuxPal = auxPal.toArray(new Byte [auxPal.size()]);
					int auxoffset = 0;
					while(auxoffset < currentAuxPal.length - (numColors - 1)) {
						int maxLen = caseSize;
						if(auxoffset + maxLen > currentAuxPal.length) {
							maxLen = numColors;
						}
						tmpColors.addAll(usedColors);
						for(int i=0; i<maxLen; i++) {
							tmpColors.remove(currentAuxPal[auxoffset + i]);
						}
						if(tmpColors.isEmpty()) {
							// already there...
							break;
						}
						tmpColors.clear();
						auxoffset++;
					}
					
					// no suitable palette in auxpal
					if(auxoffset >= currentAuxPal.length) {
						tmpColors.addAll(usedColors);
						auxoffset = currentAuxPal.length;
						while(auxoffset > 0 && tmpColors.contains(currentAuxPal[auxoffset-1])) {
							auxoffset--;
							tmpColors.remove(currentAuxPal[auxoffset]);
						}

						for(Iterator<Byte> bi = tmpColors.iterator(); bi.hasNext();) {
							auxPal.add(bi.next());
						}
						tmpColors.clear();
					}

					// now we have all colors accessible from auxoffset
					currentAuxPal = auxPal.toArray(new Byte [auxPal.size()]);
					
					Map<Byte, Integer> colorMap = new TreeMap<Byte, Integer>();
					
					for(Byte paletteIndex : usedColors) {
						for(int i=0; i<caseSize; i++) {
							if(currentAuxPal[auxoffset + i] == paletteIndex) {
								colorMap.put(paletteIndex, i);
								break;
							}
						}
					}
					
					// now we have our colors remapped to indices in auxpal
					
					int bitmask = 0;
					int op = 0;
					switch(caseSize) {
					// 32 bit bitmask
					case 4:
							for(int i=0; i<16; i++) {
								bitmask >>>= 2;
								int idx = ((int)colorMap.get(newChunk[i]))&3;
								bitmask |= idx;
							}
							for(int i=0; i<4; i++) {
								frameData.write(bitmask); // write low 8 bits
								bitmask >>>= 8;
							}
							op = OP_4_C;
							break;
					case 8:
						for(int i=0; i<8; i++) {
							bitmask >>>= 3;
							int idx = ((int)colorMap.get(newChunk[i]))&7;
							bitmask |= idx;
						}
						// write 24 bit 
						for(int i=0; i<3; i++) {
							frameData.write(bitmask); // write low 8 bits
							bitmask >>>= 8;
						}
						for(int i=9; i<16; i++) {
							bitmask >>>= 3;
							int idx = ((int)colorMap.get(newChunk[i]))&7;
							bitmask |= idx;
						}
						// write 24 bit
						for(int i=0; i<3; i++) {
							frameData.write(bitmask); // write low 8 bits
							bitmask >>>= 8;
						}
						op = OP_8_C;
						break;
					case 16:
						for(int i=0; i<8; i++) {
							bitmask >>>= 4;
							int idx = ((int)colorMap.get(newChunk[i]))&0xF;
							bitmask |= idx;
						}
						// write 32 bit 
						for(int i=0; i<4; i++) {
							frameData.write(bitmask); // write low 8 bits
							bitmask >>>= 8;
						}
						for(int i=9; i<16; i++) {
							bitmask >>>= 4;
							int idx = ((int)colorMap.get(newChunk[i]))&0xF;
							bitmask |= idx;
						}
						// write 32 bit 
						for(int i=0; i<4; i++) {
							frameData.write(bitmask); // write low 8 bits
							bitmask >>>= 8;
						}
						op = OP_16_C;
						break;
					default:
							throw new RuntimeException("This can not happen!");
					}
					op |= auxoffset;
					
					// this makes compressing the dictionary stream easier
					if(lastOp == op) {
						op = OP_REP; 
					} else {
						lastOp = op;
					}
					
					rowDict.add(op);
				}
				// skipped to end of row ?
				if(inSkip) {
					rowDict.add(OP_SKIP | SKIP_TO_END);
					inSkip = false;
				}
				
				// store row dictionary
				processRowDictionary(rowDict, dictWordData, rowDictWriter);
			}
		}
		
		public void processRowDictionary(List<Integer> rowDict, ArrayList<Byte> dictWordData, BitWriter rowDictData) {
			
		}
	}
	
	public static class Format04Decoder
	{
		private byte [] initial_palette, palette;
		
		private byte [] frameBuffer;
		
		private int width, height;
		
		public Format04Decoder(int width, int height, byte [] palette)
		{
			assert(width>0);
			assert(height>0);
			assert(palette!=null);
			assert(palette.length==768);
			
			this.width = width;
			this.height = height;
			
			this.initial_palette = palette;

			frameBuffer = new byte [width*height];
		
			reset();
		}
		
		public void setPalette(byte [] palette)
		{
			assert(palette!=null);
			assert(palette.length==768);
			
			this.palette = palette;
			Arrays.fill(frameBuffer, (byte)0);
		}
		
		public byte [] getPalette() {
			return palette;
		}
		
		public void reset()
		{
			setPalette(initial_palette);
		}
		
		public BufferedImage getCurrentFrame()
		{
			IndexColorModel icm = new IndexColorModel(8, 256, palette, 0, false);
			BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, icm);
			bi.getRaster().setDataElements(0, 0, width, height, frameBuffer);
			return bi;
		}
		
		public BufferedImage getCurrentFrame(BufferedImage target)
		{
			if(target==null)
				return getCurrentFrame();
			target.getRaster().setDataElements(0, 0, width, height, frameBuffer);
			return target;
		}
		
		public byte [] decodeFrame(ByteBuffer frame_bb)
		{
			unpackFormat04Frame(frame_bb, frameBuffer, frameBuffer.length);
			
			return frameBuffer;
		}
	}
	
	public static byte [] unpackFormat04Frame(byte [] packed, int packedsize, byte [] unpacked, int unpacksize)
	{
		return unpackFormat04Frame(ByteBuffer.wrap(packed, 0, packedsize).order(ByteOrder.LITTLE_ENDIAN), unpacked, unpacksize);
	}

	public static byte [] unpackFormat04Frame(ByteBuffer pbb, byte [] unpacked, int unpacksize)
	{
		debugF04Unpack("Unpacking %d bytes...\n", pbb.limit());
		
		if(unpacked==null)
			unpacked = new byte [unpacksize];
		
		int uindex = 0;
		
		unpackloop: while(uindex<unpacksize && pbb.hasRemaining())
		{
			int val = ((int)pbb.get())&0xFF;
	
			debugF04Unpack("@%04X val = %02X\n", uindex, val);
			
			if(val==0)
			{
				int count = ((int)pbb.get())&0xFF;
				byte col = pbb.get();
				
				debugF04Unpack(" > small run of %d-times color %02X\n", count, col);
				
				for(int i=0; i<count && uindex<unpacksize; i++)
					unpacked[uindex++] = col;
				
				continue unpackloop;
			}
			else if(val < 0x81) // if bit 8 is set, a long operation is done, else normal copy operation
			{
				int copycount = val;
				
				if(val==0x80)
				{
					debugF04Unpack(" > long operation : ");
					copycount = ((int)pbb.get())&0xFF;
					
					int cb2 = ((int)pbb.get())&0xFF;
					
					if(copycount==0 && cb2==0) {
						debugF04Unpack("its a break...\n");
						break unpackloop;
					}
	
					if(cb2 < 0x80) // long skip
					{
						uindex += (cb2<<8) + copycount;
						debugF04Unpack(" skip %d bytes\n", (cb2<<8) + copycount);
						copycount = 0;
					}
					else if(cb2<0xC0) // long copy
					{
						debugF04Unpack("copy\n");
						copycount += ((cb2&0x3F)<<8);
					}
					else // long run
					{
						copycount += (cb2&0x3F)<<8;
						byte col = pbb.get();
						debugF04Unpack("run of %d times color %02X\n", copycount, col);
						for(int i=0; i<copycount && uindex<unpacksize; i++)
							unpacked[uindex++] = col;
						continue unpackloop;
					}
				}
				
				if(copycount > 0) {
					debugF04Unpack(" > copying %d bytes\n",copycount);
				}
				
				for(int i=0; i<copycount && uindex<unpacksize && pbb.hasRemaining(); i++)
					unpacked[uindex++] = pbb.get();
	
				continue unpackloop;
			}
			else
			{
				debugF04Unpack("small skip of %d bytes\n", val & 0x7F);
				uindex += (val & 0x7F);
			}
		}
		
		return unpacked;
	}
	
	public static byte [] packFormat04Frame(byte [] frame) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

		int copyStart = 0;
		int runStart = 0;
		byte runColor = 0;
		int runLength = 0;
		int copyLength = 0;

		debugF04Pack("Packing %d bytes...\n", frame.length);
		
		int frameIndex = 1;
		runColor = frame[0];
		runLength = 1;
		debugF04Pack("Starting with runColor=%02X...\n", runColor);
		while(frameIndex < frame.length+1) { // +1 to catch run over end of data
			if( (frameIndex < frame.length) && (runColor == frame[frameIndex]) && (runLength < 0x3FFF) && (copyLength < 0x3FFF) ) {
				runLength++;
				frameIndex++;
				debugF04Pack("@%04X Runlength for %02X is now %d bytes...\n", frameIndex, runColor, runLength);
			} else {
				debugF04Pack("@%04X Run stopped at %d bytes at color %02X, runStart=%04X, copyStart=%04X\n", frameIndex, frameIndex, frameIndex < frame.length ? frame[frameIndex] : -1, runStart, copyStart);
				boolean endOfData = frameIndex == frame.length;
				
				if(endOfData) {
					debugF04Pack("Last processing at end of data...\n");
				}
				
				if(runLength == 0x3FFF) {
					debugF04Pack("Large run...\n");
				}
				
				// handly large copy
				if(copyLength == 0x3FFF) {
					debugF04Pack("Writing large copy from %04X to %04X (%d bytes)\n", copyStart, runStart-1, copyLength);
					baos.write((byte)0x80);
					baos.write((byte)(copyLength&0xFF));
					baos.write((byte)(0x80 | ((copyLength>>8)&0x3F)));
					for(int i=0; i<copyLength; i++) {
						baos.write(frame[copyStart+i]);
					}
					copyStart = runStart-1;
					copyLength = 0;
				}
				
				// color 0 can be skipped cheaper
				if((runLength > 2) || (runColor == 0 && runLength > 1)) {
					// write copy if any
					if(copyLength > 0) {
						debugF04Pack("Writing copy from %04X to %04X (%d bytes)\n", copyStart, runStart-1, copyLength);
						if(copyLength < 0x80) {
							baos.write((byte)copyLength);
						} else {
							baos.write((byte)0x80);
							baos.write((byte)(copyLength&0xFF));
							baos.write((byte)(0x80 | ((copyLength>>8)&0x3F)));
						}
						debugF04Pack("[");
						for(int i=0; i<copyLength; i++) {
							debugF04Pack("%02X ", frame[copyStart+i]);
							baos.write(frame[copyStart+i]);
						}
						debugF04Pack("]\n");
					}
					// write run or skip
					debugF04Pack("runColor = %02X, runLength = %d\n", runColor, runLength);
					if(runColor == 0 && runLength < 0x80) {
						debugF04Pack(" > small skip\n");
						baos.write((byte)(0x80 | (runLength&0x7F)));
					} else if(runLength < 0xFF) {
						debugF04Pack(" > long run\n");
						baos.write((byte)0);
						baos.write((byte)runLength&0xFF);
						baos.write(runColor);
					} else {
						debugF04Pack(" > long run or skip\n");
						baos.write((byte)0x80);
						baos.write((byte)(runLength&0xFF));
						baos.write((byte) ( (runColor==0 ? 0 : 0xC0) | ((runLength>>8)&0x7F) ) );
						if(runColor!=0) {
							baos.write(runColor);
						}
					}
					copyStart = frameIndex;
					copyLength = (runStart-1) - copyStart;
				} else {
					if(endOfData) {
						debugF04Pack("Copying at end of data... runStart=%04X, copyStart=%04X\n", runStart, copyStart);
						// I think I ruled out any occasion where this could be larger than 0x3FFF...
						copyLength = frame.length - copyStart;
						if(copyLength > 0) {
							debugF04Pack("Writing copy from %04X to %04X (%d bytes)\n", copyStart, frame.length-1, copyLength);
							if(copyLength < 0x80) {
								baos.write((byte)copyLength);
							} else {
								baos.write((byte)0x80);
								baos.write((byte)(copyLength&0xFF));
								baos.write((byte)(0x80 | ((copyLength>>8)&0x3F)));
							}
							debugF04Pack("[");
							for(int i=0; i<copyLength; i++) {
								debugF04Pack("%02X ", frame[copyStart+i]);
								baos.write(frame[copyStart+i]);
							}
							debugF04Pack("]\n");
						}
					} else {
						debugF04Pack("This is not a run to be packed...\n");
					}
				}
				if(frameIndex < frame.length)
					runColor = frame[frameIndex];
				debugF04Pack("@%04X new run color = %02X, copyStart = %04X\n", frameIndex, runColor, copyStart);
				runLength = 1;
				copyLength = frameIndex - copyStart; // this mostly increased copyLength by 1
				frameIndex++;
				runStart = frameIndex; // maybe
			}
		}
		
		return baos.toByteArray();
	}
	
	public static boolean equalStart(byte [] template, byte [] data)
	{
		if(data.length < template.length)
			return false;
		
		for(int i=0; i<template.length; i++)
			if(template[i] != data[i])
				return false;
		
		return true;
	}
	
	public static void main(String...args) {
		File infile = null;
		File outfile = null;
		
		boolean compress = true;
		
		for(int i=0; i<args.length; i++) {
			if(args[i].equals("-d")) {
				compress = false;
				continue;
			}
			if(args[i].equals("-c")) {
				compress = true;
				continue;
			}
			if(infile == null) {
				infile = new File(args[i]);
				continue;
			}
			if(outfile == null) {
				outfile = new File(args[i]);
				continue;
			}
			System.err.println("Error!");
			System.exit(1);
		}
		
		if(infile == null || outfile == null) {
			System.err.println("Need in- and outfile!");
			System.exit(1);
		}
		
		byte [] data = Util.readFileFully(infile);
		
		if(data == null) {
			System.err.println("Error while reading input!");
			System.exit(1);
		}
		
		byte [] outdata;
		
		if(compress) {
			byte [] packed = packFormat04Frame(data);
			outdata = new byte [packed.length + 4];
			ByteBuffer bb = ByteBuffer.wrap(outdata);
			bb.order(ByteOrder.BIG_ENDIAN);
			bb.putInt(data.length);
			bb.put(packed);
		} else {
			ByteBuffer bb = ByteBuffer.wrap(data);
			bb.order(ByteOrder.BIG_ENDIAN);
			int length = bb.getInt();
			byte [] packed = new byte [data.length - 4];
			bb.get(packed);
			
			outdata = unpackFormat04Frame(packed, packed.length, null, length);
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(outfile);
			fos.write(outdata);
		} catch (FileNotFoundException e) {
			System.err.println("Error while opening output file!");
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Error while writing output file!");
			System.exit(1);
		}
	}
	
}
