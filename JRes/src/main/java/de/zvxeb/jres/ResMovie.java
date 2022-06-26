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
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/*
 * Note:
 * Movies have separate audio and video streams which are interleaved.
 * Audio is delivered in chunks of 8192 byte until it ends. Video is
 * delivered frame-wise were one chunk contains one (compressed) frame.
 * Movies seem to always begin with the audio-data. After the audio chunk
 * follow video chunks until the audio buffer is almost empty. After inserting 
 * a video chunk the encoder must determine whether the audio buffer has reached
 * a low watermark and insert an audio chunk next. Audio chunks therefore can have 
 * timestamps that further in the future than following chunks.
 * 
 * Audio chunks are inserted at timestamps before the buffer gets empty.
 * The lowest observed value was 3554 ticks; corresponding to 1205 samples
 * left in the buffer.
 * I assume that this aims at leaving 1024 samples in the buffer which would be
 * 3020 ticks.
 * 
 * Palettes always get set two times at the same timestamp. The entries point to the
 * same location in the data. My guess is that this is a workaround for a bug in the 
 * player.
 *  
 * - hiben 10.09.11
 *  
 */

public class ResMovie {
	public static final boolean debug = Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResMovie.debug", "false"));
	
	public int dbgLastEntryType = -1;
	public int dbgLastEntryCount;
	public long dbgLastEntryTime;
	
	public static final byte [] movie_header = { 'M', 'O', 'V', 'I' };
	
	public static final byte CT_EOM = 0;
	public static final byte CT_VIDEO_FRAME = 1;
	public static final byte CT_AUDIO_FRAME = 2;
	public static final byte CT_SUBTITLE_CONTROL = 3;
	public static final byte CT_PALETTE = 4;
	public static final byte CT_DICTIONARY = 5;
	
	public static String [] CT_STRINGS = {
		  "End of Movie"
		, "Video-Frame"
		, "Audio-Frame"
		, "SubTitle-Control"
		, "Palette"
		, "Dictionary"
	};
	
	public static final byte DICT_INFO_AUXPAL = 0;
	public static final byte DICT_INFO_CONTROL = 1;
	
	public static final int INITIAL_PALETTE_OFFSET = 0x100;
	
	public static enum SubTitleType { Area, Standard, French, German };
	
	public static final byte [] STT_AREA = { 'A', 'R', 'E', 'A' };
	public static final byte [] STT_STD = { 'S', 'T', 'D', ' ' };
	public static final byte [] STT_FRN = { 'F', 'R', 'N', ' ' };
	public static final byte [] STT_GER = { 'G', 'E', 'R', ' ' };
	
	private int num_dir_entries;
	private int index_table_size;
	private int content_size;
	private int length;
	@SuppressWarnings("unused")
	private int dummy;
	private short width;
	private short height;
	private short sample_rate;
	
	private int movie_time = 0;
	
	private ByteBuffer bb;
	
	private byte [] initial_palette, palette;
	
	private byte [] oldPalette = debug ? new byte [768] : null;
	private int oldSetTime = 0;
	private int oldPalOffset;
	
	private long paletteSetTime = 0L;
	
	private Utils.Format04Decoder lrd;
	private Utils.Format0FDecoder hrd;

	private LinkedList<Entry> entryList = new LinkedList<Entry>();
	private int entryIndex;
	private int lastDictionary;
	
	private Map<Entry, Integer> frameDictionaryMap = new HashMap<Entry, Integer>();
	
	private int bb_initial;
	
	private int index_offset;
	
	private int current_entry;
	
	private BufferedImage firstPicture;
	
	private List<String> movieInfo = new LinkedList<String>();
	
	public static String entryTypeString(int et) {
		if(et < 0 || et > 5)
			return "Unknown";
		
		return CT_STRINGS[et];
	}
	
	public String entryString(Entry e) {
		return "Entry type: " + entryTypeString(e.type) + " TS: " + e.timestamp + " (" + (((long)e.timestamp * 1000) / 65536) + "ms) Size: " + e.length;
	}
	
	public ResMovie(ByteBuffer bb)
	{
		bb_initial = bb.position();
		
		this.bb = bb;
		bb.order(ByteOrder.LITTLE_ENDIAN);

		initial_palette = new byte [768];
		palette = new byte [768];
		
		readHeader();
		
		readPalette();
		
		lrd = new Utils.Format04Decoder(width, height, initial_palette);
		hrd = new Utils.Format0FDecoder(width, height, initial_palette);
		
		buildEntryList();
		
		VideoFrame vf = null;
		
		if(vf!=null)
		firstPicture = vf.video_data;
		
		if(vf==null)
		{
			System.out.println("This seems to be an audio-log...");
		}
	}
	
	public ResMovie(byte [] data)
	{
		this(ByteBuffer.wrap(data));
	}
	
	public int getWidth()
	{
		return width;
	}
	
	public int getHeight()
	{
		return height;
	}
	
	private void readHeader()
	{
		
		byte [] hdr = new byte[4];
		
		bb.get(hdr);
		
		for(int i=0; i<movie_header.length; i++)
			if(hdr[i]!=movie_header[i])
			{
				System.err.println("Not a MOVI! " + new String(hdr));
				return;
			}
		
		num_dir_entries = bb.getInt();
		index_table_size = bb.getInt();
		content_size = bb.getInt();
		length = bb.getInt();
		dummy = bb.getInt();
		width = bb.getShort();
		height = bb.getShort();
		sample_rate = bb.getShort();
		
		index_offset = bb_initial + INITIAL_PALETTE_OFFSET + 768;
		
		System.out.println
		(
			String.format
			(
				  "MOVI: Entries: %d, IdxSize: %d, ContentSize %d, length %d (%.2fs), W: %d H: %d, SRate: %d"
				, num_dir_entries, index_table_size, content_size, length, length * (1.0f / 65536.0f), width, height, sample_rate
			)
		);
	}
	
	public void resetMovie()
	{
		resetEntryRead();
		paletteSetTime = 0L;
		entryIndex = 0;
		movieInfo.clear();
	}
	
	private void readPalette()
	{
		bb.position(bb_initial+INITIAL_PALETTE_OFFSET);
		bb.get(initial_palette);
		System.arraycopy(initial_palette, 0, palette, 0, 768);
	}
	
	@SuppressWarnings("unused")
	private class Entry
	{
		private int entry_offset;
		private byte type;
		private byte info;
		private int timestamp;
		private int offset;
		private int number;
		private int length;
	}
	
	private void readEntry(ByteBuffer bb, Entry e)
	{
		e.entry_offset = bb.position();
		int tmp = bb.getInt();
		int type = (tmp>>24)&0xFF;
		e.type = (byte)(type&0x07);
		e.info = (byte)(((type&0x78)>>3)&0x0F);
		e.timestamp = tmp&0x00FFFFFF;
		e.offset = bb_initial + bb.getInt();
	}
	
	private void resetEntryRead()
	{
		current_entry = 0;

		lrd.reset();
		hrd.reset();

		bb.position(index_offset);
		
		if(debug) {
			System.out.println("Reset");
			dbgLastEntryType = -1;
		}
	}
	
	private Entry readNextEntry(Entry e)
	{
		if(current_entry>=num_dir_entries)
			return null;
		
		if(e == null)
			e = new Entry();
		
		readEntry(bb, e);
		
		e.number = current_entry;

		e.length = 0;
		
		if(e.type!=CT_EOM)
		{
			int pos = bb.position();
			Entry next = new Entry();
			readEntry(bb, next);
			e.length = next.offset - e.offset;
			bb.position(pos);
		}
		
		if(debug)
		{
			if(dbgLastEntryType == e.type) {
				dbgLastEntryCount++;
				if(dbgLastEntryTime > e.timestamp) {
					System.out.println("^ Entry " + dbgLastEntryCount + " is before first entry in row!");
				}
			} else {
				if(dbgLastEntryCount>1)
					System.out.println("^ Occured " + dbgLastEntryCount + " times.");
				dbgLastEntryCount = 1;
				dbgLastEntryType = e.type;
				dbgLastEntryTime = e.timestamp;
				System.out.println(entryString(e));
			}
		}
		
		current_entry++;
		
		return e;
	}
	
	private void buildEntryList() {
		resetEntryRead();
		entryList.clear();
		movieInfo.clear();
		int entnum = 0;
		int lastAudioOffset = 0;
		int lastVideoOffset = 0;
		int lastVideoAudioDiff = 0;
		int biggestNegativeOffset = Integer.MIN_VALUE;
		int bNOS = 0;
		for(Entry e=null; (e = readNextEntry(null)) != null; entryList.add(e) ) {
			if(e.type == CT_DICTIONARY && !(entryList.size()>0 && entryList.getLast().type == CT_DICTIONARY) ) {
				lastDictionary = entryList.size();
			}
			if(e.type == CT_VIDEO_FRAME) {
				frameDictionaryMap.put(e, Integer.valueOf(lastDictionary));
			}
			if(e.type == CT_EOM)
				movie_time = e.timestamp;

			if(debug) {
				if(e.type == CT_AUDIO_FRAME) {
					movieInfo.add(String.format("Entry %d, AudioFrame @%d, length %d, offset %d", entnum, e.timestamp, e.length, e.timestamp - lastAudioOffset));
					lastAudioOffset = e.timestamp;
				}
				if(e.type == CT_VIDEO_FRAME) {
					movieInfo.add(String.format("Entry %d, VideoFrame @%d, offset %d, audio-offset %d", entnum, e.timestamp, e.timestamp - lastVideoOffset, e.timestamp - lastAudioOffset));
					if( (e.timestamp - lastAudioOffset) > 0) {
						if(lastVideoAudioDiff < 0) {
							if(lastVideoAudioDiff > biggestNegativeOffset) {
								biggestNegativeOffset = lastVideoAudioDiff;
								bNOS = entnum;
							}
						}
					}
					lastVideoOffset = e.timestamp;
					lastVideoAudioDiff = e.timestamp - lastAudioOffset;
				}
				if(e.type == CT_DICTIONARY) {
					movieInfo.add(String.format("Entry %d, Dictionary @%d (%s)", entnum, e.timestamp, e.info == DICT_INFO_AUXPAL ? "AuxPal" : "Control"));
				}
				if(e.type == CT_PALETTE) {
					movieInfo.add(String.format("Entry %d, Palette @%d", entnum, e.timestamp));
				}
				if(e.type == CT_SUBTITLE_CONTROL) {
					movieInfo.add(String.format("Entry %d, Subtitle @%d", entnum, e.timestamp));
				}
				if(e.type == CT_EOM) {
					movieInfo.add(String.format("Entry %d, EOM @%d", entnum, e.timestamp));
				}
				entnum++;
			}
		}
		if(debug) {
			for(String is : movieInfo) {
				System.out.println(is);
			}
			System.out.println("Biggest negative offset: " + biggestNegativeOffset + " at " + bNOS);
		}
	}
	
	public List<Entry> getEntryList() {
		return entryList;
	}
	
	public int getMovieTime() {
		return movie_time;
	}
	
	public int getMoviewFrames() {
		return frameDictionaryMap.size();
	}

	public Entry getNextEntryFromList() {
		if(entryList.size()<=entryIndex)
			return null;
		
		return entryList.get(entryIndex++);
	}
	
	public int gotoTime(int time) {
		int i;
		Entry matchingAudioFrame = null;
		int audioIndex = 0;
		
		i=0;
		for(Entry e : entryList) {
			if(e.type == CT_AUDIO_FRAME) {
				if(e.timestamp <= time) {
					matchingAudioFrame = e;
					audioIndex = i;
				} else {
					break;
				}
			}
			i++;
		}
		
		if(matchingAudioFrame == null)
			return movie_time;
		
		int audiotime = matchingAudioFrame.timestamp;
		Entry matchingVideoFrame = null;
		int videoIndex = 0;
		
		i=0;
		for(Entry e : entryList) {
			if(e.type == CT_VIDEO_FRAME) {
				if(e.timestamp <= audiotime) {
					matchingVideoFrame = e;
					videoIndex = i;
				} else {
					break;
				}
			}
			i++;
		}

		if(matchingVideoFrame == null)
			return movie_time;
		
		Integer dictIndex = frameDictionaryMap.get(matchingVideoFrame);
		
		if(dictIndex == null)
			return movie_time;
		
		int maxIndex = Math.min(audioIndex, videoIndex);
		
		entryIndex = dictIndex;
		for(i=dictIndex; i < maxIndex; i++)
			processEntry(getNextEntryFromList(), null);
		
		return 0;
	}
	
	public static class MovieData {
		private VideoFrame videoFrame = null;
		private long paletteTime = 0;
		private AudioFrame audioFrame = null;
		private SubTitleFrame subTitleFrame = null;
		private Frame updatedFrame = null;
		
		public VideoFrame getVideoFrame() {
			return videoFrame;
		}
		
		public AudioFrame getAudioFrame() {
			return audioFrame;
		}

		public SubTitleFrame getSubTitleFrame() {
			return subTitleFrame;
		}
		
		public Frame getUpdatedFrame() {
			return updatedFrame;
		}

		public void updateVideoFrame() {
			this.updatedFrame = videoFrame;
		}

		public void updateAudioFrame() {
			this.updatedFrame = audioFrame;
		}

		public void updateSubTitleFrame() {
			this.updatedFrame = subTitleFrame;
		}
		
		public void updateVideoFrame(VideoFrame videoFrame) {
			this.videoFrame = videoFrame;
			this.updatedFrame = videoFrame;
		}

		public void updateAudioFrame(AudioFrame audioFrame) {
			this.audioFrame = audioFrame;
			this.updatedFrame = audioFrame;
		}

		public void updateSubTitleFrame(SubTitleFrame subTitleFrame) {
			this.subTitleFrame = subTitleFrame;
			this.updatedFrame = subTitleFrame;
		}
		
		public void setUnupdated() {
			updatedFrame = null;
		}
	}
	
	private void processEntry(Entry e, MovieData md)
	{
		if(e.type==CT_AUDIO_FRAME)
		{
			byte [] adata = null;
			
			if(md!=null) {
				if(md.audioFrame==null) {
					adata = new byte [e.length];
					md.audioFrame = new AudioFrame(e.timestamp, adata);
				} else {
					if(md.audioFrame.audio_data.length!=e.length) {
						adata = new byte [e.length];
						md.audioFrame.audio_data = adata;
					}
					md.audioFrame.timestamp = e.timestamp;
					adata = md.audioFrame.audio_data;
				}
				md.updateAudioFrame();
			} else {
				adata = new byte [e.length];
			}
			
			int pos = bb.position();
			bb.position(e.offset);
			bb.get(adata);
			bb.position(pos);
			
			return;
		}
		
		if(e.type==CT_PALETTE)
		{
			if(debug) {
				oldSetTime = -1;
				if(paletteSetTime == e.timestamp) {
					oldSetTime = e.timestamp;
					System.arraycopy(palette, 0, oldPalette, 0, 768);
				} else {
					oldPalOffset = e.offset;
				}
			}
			int pos = bb.position();
			bb.position(e.offset);
			bb.get(palette);
			bb.position(pos);
			
			hrd.setPalette(palette);
			lrd.setPalette(palette);

			paletteSetTime = e.timestamp;
			
			if(debug && oldSetTime != -1) {
				boolean different = false;
				for(int i=0; i<768; i++) {
					if(palette[i] != oldPalette[i]) {
						different = true;
						break;
					}
				}
				System.out.println(String.format("Double set palette: different = %s oldOffs %d newOffs %d", Boolean.toString(different), oldPalOffset, e.offset));
			}
			
			return;
		}
		
		if(e.type==CT_DICTIONARY)
		{
			int pos = bb.position();
			bb.position(e.offset);
			if(e.info==DICT_INFO_AUXPAL) // auxpal
			{
				//System.out.println("Setting auxpal...");
				hrd.setAuxPal(bb);
			}
			else if(e.info==DICT_INFO_CONTROL) // dictionary
			{
				//System.out.println("Setting dictionary...");
				hrd.setPackedDictionary(bb);
			}
			bb.position(pos);
			
			return;
		}
		
		if(e.type==CT_VIDEO_FRAME)
		{
			if(e.info == 0x0f)
			{
				int pos = bb.position();
				bb.position(e.offset);
				hrd.decompressFrame(bb);
				
				if(md!=null) {
					// changing the color model is not possible...
					if(md.videoFrame == null || md.paletteTime < paletteSetTime) {
						md.videoFrame = new VideoFrame(e.timestamp, hrd.getCurrentFrame());
						md.paletteTime = e.timestamp;
					} else {
						hrd.getCurrentFrame(md.videoFrame.video_data);
						md.videoFrame.timestamp = e.timestamp;
					}
					md.updateVideoFrame();
				}
				
				bb.position(pos);
			}
			else if(e.info == 0x04)
			{
				int pos = bb.position();
				bb.position(e.offset + 8);
				
				lrd.decodeFrame(bb);

				if(md!=null) {
					// changing the color model is not possible...
					if(md.videoFrame == null || md.paletteTime < paletteSetTime) {
						md.videoFrame = new VideoFrame(e.timestamp, lrd.getCurrentFrame());
						md.paletteTime = e.timestamp;
					} else {
						lrd.getCurrentFrame(md.videoFrame.video_data);
						md.videoFrame.timestamp = e.timestamp;
					}
					md.updateVideoFrame();
				}
				
				bb.position(pos);
			}
			
			return;
		}
		
		if(e.type==CT_SUBTITLE_CONTROL)
		{
			int pos = bb.position();
			bb.position(e.offset);
			if(md!=null)
				md.updateSubTitleFrame(new SubTitleFrame(e.timestamp, bb));
			bb.position(pos);
		}
	}
	
	public Frame getNextFrame(MovieData md) {
		Entry e = null;
		while( (e = getNextEntryFromList()) != null) {
			processEntry(e, md);
			
			if(md!=null && md.getUpdatedFrame()!=null)
				return md.getUpdatedFrame();
		}
		
		return null;
	}
	
	public boolean entriesLeft() {
		return entryIndex < num_dir_entries;
	}
	
	public BufferedImage getFirstPicture()
	{
		return firstPicture;
	}
	
	// F R A M E S
	
	public class Frame
	{
		protected long timestamp;
		
		private Frame(long timestamp)
		{
			this.timestamp = timestamp;
		}
		
		public long getTimestamp()
		{
			return this.timestamp;
		}		
	}
	
	public class AudioFrame extends Frame
	{
		private byte [] audio_data;
		
		private AudioFrame(long timestamp, byte [] data)
		{
			super(timestamp);
			this.audio_data = data;
		}
				
		public byte [] getAudioData()
		{
			return this.audio_data;
		}
	}
	
	public class VideoFrame extends Frame
	{
		private BufferedImage video_data;
		
		private VideoFrame(long timestamp, BufferedImage data)
		{
			super(timestamp);
			this.video_data = data;
		}
				
		public BufferedImage getVideoData()
		{
			return this.video_data;
		}
	}
	
	public class SubTitleFrame extends Frame
	{
		private SubTitleType type;
		
		private short headerSize;
		
		private byte [] ss_text;
		private String text;
		
		public SubTitleFrame(long timestamp, ByteBuffer bb)
		{
			super(timestamp);
			
			byte [] tmp = new byte[4];
			
			int pos = bb.position();
			
			bb.get(tmp, 0, 4);

			type = null;
			
			if(Utils.equalStart(STT_AREA, tmp))
			{
				type = SubTitleType.Area;
			}
			else if(Utils.equalStart(STT_STD, tmp))
			{
				type = SubTitleType.Standard;
			}
			else if(Utils.equalStart(STT_FRN, tmp))
			{
				type = SubTitleType.French;
			}
			else if(Utils.equalStart(STT_GER, tmp))
			{
				type = SubTitleType.German;
			}
			
			if(type==null)
			{
				System.err.println(String.format("Unknown subtitle-control: \"%c%c%c%c\"", tmp[0], tmp[1], tmp[2], tmp[3]));
				type = SubTitleType.Standard;
			}
			
			headerSize = bb.getShort();
			
			bb.position(pos+headerSize);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
			
			while(bb.hasRemaining())
			{
				int b = ((int)bb.get())&0xFF;

				if(b==0)
					break;
				
				baos.write(b);
			}
			
			ss_text = baos.toByteArray();

			text = Font.decodeSSString(ss_text, 0, ss_text.length, false);
		}
		
		public String getText()
		{
			return text;
		}
		
		public byte [] getSSText()
		{
			return ss_text;
		}
		
		public SubTitleType getType()
		{
			return type;
		}
		
		public String toString()
		{
			return 
				String.format
				(
						  "SubTitle (%s): %s (%d)"
						, (type==SubTitleType.Area)
							?"Area"
							:(type==SubTitleType.Standard)
								?"Std"
								:(type==SubTitleType.French)
									?"Frn"
									:"Ger"
						, text
						, headerSize
				);
		}
	}
}
