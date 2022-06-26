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
 * Created on 29.09.2008
 */
package de.zvxeb.jres;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;

public class VocFile {
	public static final byte [] VOC_HEADER =
		{ 'C', 'r', 'e', 'a', 't', 'i', 'v', 'e', ' ', 'V', 'o', 'i', 'c', 'e', ' ', 'F', 'i', 'l', 'e', 0x1A };
	
	public static final byte BT_TERMINATOR = 0;
	public static final byte BT_SOUND_DATA = 1;
	public static final byte BT_SOUND_CONTINUE = 2;
	public static final byte BT_SILENCE = 3;
	public static final byte BT_MARKER = 4;
	public static final byte BT_ASCII = 5;
	public static final byte BT_REPEAT = 6;
	public static final byte BT_END_REPEAT = 7;
	public static final byte BT_EXTENDED = 8;
	public static final byte BT_NEW_SOUND_DATA = 9;
	
	public static final byte DT_8BITS_U = 0; 
	public static final byte DT_4_TO_8BITS_ADPCM = 1; 
	public static final byte DT_3_TO_8BITS_ADPCM = 2; 
	public static final byte DT_2_TO_8BITS_ADPCM = 3; 
	public static final byte DT_16BITS_S = 4; 
	public static final byte DT_ULAW = 6; 
	public static final byte DT_ALAW = 7; 
	public static final byte DT_4_BITS_TO_16BITS_ADPCM = (byte)0x200; // only for new sound data
	
	private short first_datablock_offset;
	private short version_number;
	private short version_check;
	
	private int v_major, v_minor;
	
	private List<SoundDataI> sound_data;
	
	public static interface SoundDataI
	{
		public int getSampleRate();
		public int getBitsPerSample();
		public int getChannels();
		public boolean isIntegerData();
		
		public byte [] getData();
		
		public AudioFormat getAudioFormat();
	}
	
	private static class SoundData implements SoundDataI
	{
		private int sample_rate;
		private byte data_type;
		
		private ExtendedInfo info;
		
		private byte [] sound_data;
		
		public SoundData(ExtendedInfo ei, int sample_rate, byte data_type, byte [] sound_data)
		{
			this.sample_rate = sample_rate;
			this.data_type = data_type;
			this.sound_data = sound_data;
			
			if(sample_rate<1)
			{
				System.err.println("sample_rate of " + sample_rate + " specified, adjusting to 211 (22222.22Hz)...");
				sample_rate = 211;
			}
			
			info = ei;
		}	
		
		public int getSampleRate()
		{
			return 1000000 / (256 - sample_rate);
		}
		
		public int getChannels()
		{
			if(info==null)
				return 1;
			
			return info.isStereo()?2:1;
		}
		
		public int getBitsPerSample()
		{
			switch(data_type)
			{
			case DT_8BITS_U:
			case DT_ULAW:
			case DT_ALAW:
				return 8;
			case DT_16BITS_S:
				return 16;
			default:
				return -1;
			}
		}

		public boolean isIntegerData()
		{
			switch(data_type)
			{
			case DT_8BITS_U:
			case DT_16BITS_S:
			case DT_ULAW:
			case DT_ALAW:
				return true;
			default:
				return false;
			}
		}
		
		public AudioFormat getAudioFormat()
		{
			AudioFormat af = null;
			
			int sample_rate = getSampleRate();
			int bps = getBitsPerSample();
			int byteps = (bps+7) >> 3;
			int channels = getChannels();

			Encoding e = null;
			
			switch(data_type)
			{
			case DT_8BITS_U:
				e = Encoding.PCM_UNSIGNED;
				break;
			case DT_16BITS_S:
				e = Encoding.PCM_SIGNED;
				break;
			case DT_ULAW:
				e = Encoding.ULAW;
				break;
			case DT_ALAW:
				e = Encoding.ALAW;
				break;
			}
			
			if(e!=null)
			{
				af = new AudioFormat(e, sample_rate, bps, channels, byteps*channels, sample_rate, false);
			}
			
			return af;
		}
		
		public byte [] getData()
		{
			return sound_data;
		}
	}
	
	public static class ExtendedInfo
	{
		private int time_constant;
		private boolean stereo;
		
		public ExtendedInfo(int time_constant, boolean stereo)
		{
			this.time_constant = time_constant;
			this.stereo = stereo;
		}
		
		public boolean isStereo()
		{
			return stereo;
		}
		
		public int getTimeConstant()
		{
			return time_constant;
		}
	}
	
	private static class NewSoundData implements SoundDataI
	{
		private int sample_rate;
		private byte sample_bits;
		private byte number_of_channels;
		
		private byte [] sound_data;
		
		public NewSoundData(int sample_rate, byte sample_bits, byte number_of_channels, byte [] data)
		{
			this.sample_rate = sample_rate;
			this.sample_bits = sample_bits;
			this.number_of_channels = number_of_channels;
			this.sound_data = data;
		}
		
		public int getSampleRate()
		{
			return sample_rate;
		}
		
		public int getBitsPerSample()
		{
			return sample_bits;
		}
		
		public int getChannels()
		{
			return number_of_channels;
		}
		
		public byte [] getData()
		{
			return sound_data;
		}
		
		public boolean isIntegerData()
		{
			return true;
		}
		
		public AudioFormat getAudioFormat()
		{
			AudioFormat af = null;
			
			int sample_rate = getSampleRate();
			int bps = getBitsPerSample();
			int byteps = (bps+7) >> 3;
			int channels = getChannels();

			Encoding e = null;
			
			switch(bps)
			{
			case 8:
				e = Encoding.PCM_UNSIGNED;
				break;
			case 16:
				e = Encoding.PCM_SIGNED;
				break;
			}
			
			if(e!=null)
			{
				af = new AudioFormat(e, sample_rate, bps, channels, byteps*channels, sample_rate, false);
			}
			
			return af;
		}
	}
	
	public VocFile(ByteBuffer bb) throws InvalidVocFileException
	{
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		byte [] tmpbuffer = new byte [VOC_HEADER.length];
		
		bb.get(tmpbuffer);
		
		if(!Utils.equalStart(VOC_HEADER, tmpbuffer))
			throw new InvalidVocFileException("Invalid Header!");
		
		first_datablock_offset = bb.getShort();
		
		version_number = bb.getShort();
		version_check = bb.getShort();
		
		short tmp = (short)((~(((int)version_number)&0xFFFF)+0x1234)&0xFFFF);
		
		if(tmp!=version_check)
			throw new InvalidVocFileException(String.format("Checksum Mismatch... needed: %04X got: %04X", tmp, version_check));
		
		v_major = (version_number>>8)&0xFF;
		v_minor = version_number&0xFF;
		
		sound_data = new LinkedList<SoundDataI>();
		
		processDataBlocks(bb);
	}
	
	public int getMajorVersion()
	{
		return v_major;
	}
	
	public int getMinorVersion()
	{
		return v_minor;
	}
	
	public short firstDataBlockOffset()
	{
		return first_datablock_offset;
	}
	
	public String toString()
	{
		return String.format("VocFile v%1$d.%2$d, Data at %3$d (%3$04X)", v_major, v_minor, first_datablock_offset);
	}
	
	public List<SoundDataI> getSounds()
	{
		return sound_data;
	}
	
	private void processDataBlocks(ByteBuffer bb)
	{
		boolean term = false;
		
		ExtendedInfo ei = null;
		
		int current_sr = 211; // 22222.0 Hz
		byte current_type = DT_8BITS_U;
		
		byte [] tmpd;
		
		while(!term && bb.hasRemaining())
		{
			byte type = bb.get();
			
			if(type==BT_TERMINATOR)
			{
				//System.out.println("Terminator block reached...");
				term = true;
				continue;
			}
			
			int len =
				(((int)bb.get())&0xFF)
				| ((((int)bb.get())&0xFF)<<8)
				| ((((int)bb.get())&0xFF)<<16);
			
			switch (type) {
			case BT_EXTENDED:
				int tc = ((int)bb.getShort())&0xFFFF;
				bb.get();
				boolean st = bb.get()==1;
				ei = new ExtendedInfo(tc, st);
				System.out.println("Extended Info read...");
				break;
			case BT_SOUND_DATA:
				current_sr = ((int)bb.get())&0xFF;
				current_type = bb.get();
				tmpd = new byte [len-2];
				bb.get(tmpd);
				sound_data.add(new SoundData(ei, current_sr, current_type, tmpd));
				System.out.println("Sound Data read");
				break;
			case BT_SOUND_CONTINUE:
				tmpd = new byte [len];
				bb.get(tmpd);
				sound_data.add(new SoundData(ei, current_sr, current_type, tmpd));
				System.out.println("Sound continue read");
				break;
			case BT_NEW_SOUND_DATA:
				int pos = bb.position();
				int sr = bb.getInt();
				byte sb = bb.get();
				byte ch = bb.get();
				bb.position(pos+12);
				tmpd = new byte [len-12];
				bb.get(tmpd);
				sound_data.add(new NewSoundData(sr, sb, ch, tmpd));
				System.out.println("New Sound Data read");
				break;
			case BT_SILENCE:
				bb.position(bb.position()+3);
				System.out.println("Skipping silence...");
				break;
			case BT_MARKER:
				bb.position(bb.position()+2);
				System.out.println("Skipping marker...");
				break;
			case BT_ASCII:
				bb.position(bb.position()+len);
				System.out.println("Skipping ASCII...");
				break;
			case BT_REPEAT:
				bb.position(bb.position()+2);
				System.out.println("Skipping Repeat...");
				break;
			case BT_END_REPEAT:
				System.out.println("Skipping End-Repeat...");
				break;
			default:
				System.out.println(String.format("Unknown block %1$d (%1$X)... skipping by length %2$d...", type, len));
				bb.position(bb.position()+len);
			}
		}
		
		if(!term)
		{
			System.out.println("VocData ended without terminator block...");
		}
	}
}
