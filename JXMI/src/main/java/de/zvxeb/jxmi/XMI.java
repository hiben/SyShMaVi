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
package de.zvxeb.jxmi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;

/**
 * 
 * @author hiben
 *
 */
public class XMI {
	public static final boolean debug = Boolean.parseBoolean(System.getProperty("org.hiben.jxmi.debug", "false"));
	
	public static final byte [] BA_FORM =
	{
		'F', 'O', 'R', 'M' 
	};
	public static final byte [] BA_XMID =
	{
		'X', 'M', 'I', 'D' 
	};
	public static final byte [] BA_XDIR =
	{
		'X', 'D', 'I', 'R' 
	};
	public static final byte [] BA_INFO =
	{
		'I', 'N', 'F', 'O' 
	};
	public static final byte [] BA_CAT =
	{
		'C', 'A', 'T', ' ' 
	};
	public static final byte [] BA_EVNT =
	{
		'E', 'V', 'N', 'T' 
	};
	
	public static final byte XMI_NOTE_OFF = 0x8; 
	public static final byte XMI_NOTE_ON = 0x9; 
	public static final byte XMI_AFTERTOUCH = 0xA; 
	public static final byte XMI_CONTROLLER = 0xB; 
	public static final byte XMI_PITCH_WHEEL = 0xE; 
	public static final byte XMI_PROGRAM_CHANGE = 0xC; 
	public static final byte XMI_CHANNEL_PRESSURE = 0xD; 
	public static final byte XMI_SYSEX = 0xF; 
	
	private int tracks;
	
	private byte [] fourb;
	private ByteBuffer buffer;
	
	private List<Sequence> sequences;
	
	public int getTrackCount()
	{
		return tracks;
	}
	
	public List<Sequence> getSequences()
	{
		return sequences;
	}
	
	public XMI(ByteBuffer bb) throws XMIException, InvalidMidiDataException
	{
		fourb = new byte [4];
		buffer = bb;
		
		processBuffer();
	}
	
	static private void printf(String format, Object...args)
	{
		System.out.println(String.format(format, args));
	}

	private boolean isChunk(byte [] chunk)
	{
		return Util.startsWith(fourb, chunk);
	}
	
	private void processBuffer() throws XMIException, InvalidMidiDataException
	{
		int i, chunk_len;
		
		buffer.order(ByteOrder.BIG_ENDIAN);
		
		buffer.get(fourb);
		
		if(isChunk(BA_FORM))
		{
			int len = buffer.getInt();
			
			if(debug)
			{
				printf("Len is " + len);
			}
			
			int start = buffer.position();
			
			buffer.get(fourb);
			
			if(isChunk(BA_XMID))
			{
				if(debug)
				{
					printf("No XDIR!");
				}
				
				tracks = 1;
				buffer.getInt();
			}
			else
			{
				if(!isChunk(BA_XDIR))
				{
					throw new XMIException("Invalid XMI! No XDIR!");
				}
				else
				{
					for(i = 4; i < len; i++)
					{
						buffer.get(fourb);
						chunk_len = buffer.getInt();
						
						i += 8;
						
						if(!isChunk(BA_INFO))
						{
							// align position
							// is this valid with the ++ in the loop?
							chunk_len = (chunk_len+1)&~1;
							buffer.position(buffer.position() + chunk_len);
							i += chunk_len;
							continue;
						}
						
						if(chunk_len < 2)
							break;
						
						// track count is in little endian...
						buffer.order(ByteOrder.LITTLE_ENDIAN);
						
						tracks = buffer.getShort();

						buffer.order(ByteOrder.BIG_ENDIAN);
						
						break;
					}
					
					if(tracks==0)
						throw new XMIException("Invalid XMI! No header!");
					
					buffer.position(start + ((len+1)&~1));
					
					buffer.get(fourb);
					
					if(!isChunk(BA_CAT))
					{
						throw new XMIException("Invalid XMI! No CAT");
					}
		
					// track length (unused)
					len = buffer.getInt();
					
					buffer.get(fourb);
					
					if(!isChunk(BA_XMID))
					{
						throw new XMIException("Invalid XMI! No XMID");
					}
				}
			}
			
			sequences = new Vector<Sequence>();
			
			int count = extractTracks();
			
			if(count!=tracks)
			{
				printf("Only read %d of %d tracks...", count, tracks);
			}
		}
		else
		{
			throw new XMIException("Invalid XMI-File!");
		}
	}
	
	private int getVLQ()
	{
		int quant = 0;
		
		for(int i=0; i<4; i++)
		{
			int data = ((int)buffer.get())&0xFF;
			quant <<= 7;
			quant |= (data&0x7F);
			
			if((data&0x80)==0)
			{
				break;
			}
		}
		
		return quant;
	}
	
	private int getVLQ2()
	{
		int quant = 0;
		
		for(int i=0; i<4 && buffer.remaining()>0; i++)
		{
			int data = ((int)buffer.get())&0xFF;
			
			if((data&0x80)!=0)
			{
				buffer.position(buffer.position()-1);
				break;
			}
			
			quant += data;
		}
		
		return quant;
	}
	
	private int putVLQ(int value, ByteBuffer bb) {
		int buffer;
		int i=1;
		buffer = value & 0x7F;
		while( (value>>>=7) != 0) {
			buffer <<= 8;
			buffer |= ((value & 0x7F) | 0x80);
			i++;
		}
		if(bb!=null) {
			for(int j=0; j<i; j++)  {
				bb.put((byte)(buffer&0xFF));
				buffer >>>= 8;
			}
		}
		return i;
	}
	
	private int extractTracks() throws InvalidMidiDataException
	{
		int num = 0;
		int len;
		
		buffer.order(ByteOrder.BIG_ENDIAN);
		
		int sequence_number = 1;
		while(buffer.remaining() > 0 && num < tracks)
		{
			//printf("Reading track %d, remaining bytes %d", (num+1), buffer.remaining());

			buffer.get(fourb);
			
			len = buffer.getInt();
			
			if(isChunk(BA_FORM))
			{
				buffer.getInt(); // skip
				buffer.get(fourb);
				len = buffer.getInt();
			}
			
			if(!isChunk(BA_EVNT))
			{
				buffer.position(buffer.position() + (len+1)&~1);
				continue;
			}
			
			int begin = buffer.position();
			
			int [] channels_used = { 0 };
			
			System.out.println("Reading sequence " + sequence_number);
			Sequence seq = readSequence(channels_used);
			
			if(seq==null)
			{
				if(debug)
				{
					System.err.println("Unable to convert data...");
				}
				break;
			}
			else
			{
				sequence_number++;
				StringBuilder sb = new StringBuilder();
				
				if(channels_used[0]==0) {
					sb.append("Sequence uses no channels...");
				} else {
					sb.append("Sequence used channels ");
				}
				
				int num_channels = 0;
				for(int channel = 0; channel<16; channel++) {
					if( (channels_used[0] & (1<<channel)) != 0)
						num_channels++;
				}

				for(int channel = 0; channel<16; channel++) {
					if( (channels_used[0] & (1<<channel)) != 0) {
						sb.append(Integer.toString(channel+1));
						num_channels--;
						if(num_channels==1)
							sb.append(" and ");
						else if(num_channels>0)
							sb.append(", ");
					}
				}
				
				System.out.println(sb.toString());
				
				sequences.add(seq);
			}
			
			buffer.position(begin + (len+1)&~1);
			
			num++;
		}
		
		return num;
	}
	
	private void processEvent(int time, int status, int dlen, List<MidiEvent> eventlist) throws InvalidMidiDataException
	{
		ShortMessage sm = new ShortMessage();
		int data1 = ((int)buffer.get())&0xFF;
		int data2 = 0;
		
		if(dlen>1)
		{
			data2 = ((int)buffer.get())&0xFF;
		}
		
		switch(dlen)
		{
		case 1:
			sm.setMessage(status, data1, 0);
			break;
		case 2:
			sm.setMessage(status, data1, data2);
			break;
		case 3:
			sm.setMessage(status, data1, data2);
			eventlist.add(new MidiEvent(sm, time));
			int delta = getVLQ();
			time += delta;
			sm = new ShortMessage();
			sm.setMessage(status, data1, 0);
			break;
		default:
			throw new RuntimeException("Invalid dlen! " + dlen);
		}
		
		eventlist.add(new MidiEvent(sm, time));
	}
	
	private void processSystemMessage(int time, int status, List<MidiEvent> eventlist) throws InvalidMidiDataException
	{
		int data = ((int)buffer.get())&0xFF;
		int len = getVLQ();
		
		byte [] messagedata = new byte [0];
		
		if(len>0)
		{
			messagedata = new byte [len];
			buffer.get(messagedata);
		}
		
		if(status==0xFF)
		{
			MetaMessage mm = new MetaMessage();
			mm.setMessage(data, messagedata, len);
			eventlist.add(new MidiEvent(mm, time));
			
			if(debug)
			{
				printf("S: Adding Meta Event");
			}
		} else {
			SysexMessage sm = new SysexMessage();
			sm.setMessage(status, messagedata, len);
			eventlist.add(new MidiEvent(sm, time));
			if(debug)
			{
				printf("S: Adding SysexMessage");
			}
		}
	}
	
	private Sequence readSequence(int [] channels_used) throws InvalidMidiDataException
	{
		boolean end = false;
		int status;
		int time = 0;

		int data;
		
		int tempo = 500000;
		boolean tempo_set = false;
		
		List<MidiEvent> eventlist = new Vector<MidiEvent>();
		
		if(channels_used!=null)
			channels_used[0] = 0;
		
		while(!end && buffer.remaining()>3)
		{
			data = getVLQ2();
			
			if(data<0) {
				System.err.println("Negative delta " + data);
			}
			
			time += data;
			
			status = ((int)buffer.get())&0xFF;

			//printf(String.format("Status: %X", status));
			
			switch((status>>4)&0xF)
			{
			
			// Note Off
			case XMI_NOTE_OFF:
				if(debug)
				{
					System.err.println("Note off not valid in XMIDI!");
				}
				processEvent(time, status, 2, eventlist);
				break;
			// Note On
			case XMI_NOTE_ON:
				if(channels_used!=null)
					channels_used[0] |= (1 << (status&0x0F));
				processEvent(time, status, 3, eventlist);
				break;
			case XMI_AFTERTOUCH:
			case XMI_CONTROLLER:
			case XMI_PITCH_WHEEL:
				processEvent(time, status, 2, eventlist);
				break;
			case XMI_PROGRAM_CHANGE:
			case XMI_CHANNEL_PRESSURE:
				processEvent(time, status, 1, eventlist);
				break;
			case XMI_SYSEX:
				if(status==0xFF)
				{
					int pos = buffer.position();
					data = ((int)buffer.get())&0xFF;
					
					if(data == 0x2F)
					{
						if(debug)
						{
							printf("Track ends...");
						}
						end = true;
					}
					else if(data == 0x51)
					{
						if(debug)
						{
							printf("Setting tempo...");
						}
						tempo = getVLQ();
						tempo_set = true; // ?
						buffer.position(buffer.position()+tempo);
						break;
					}
					
					buffer.position(pos);
				}
				processSystemMessage(time, status, eventlist);
				break;
			default:
				break;
			}
		}
		if(tempo_set)
			System.out.println("Tempo was set.");
		System.out.println("Tempo is " + tempo + " conversion " + (tempo*9)/25000);
		// XMIDIs do not use tempo. it is fixed at 60 pulses per quarter note
		// seems to match system shock xmis quite well.
		Sequence seq = new Sequence(Sequence.PPQ, 60);//(tempo*9)/25000);
		
		Track t = seq.createTrack();
		
		MetaMessage tempomsg = new MetaMessage();
		tempomsg.setMessage(0x51, new byte [] {0x03, 0x07, (byte)0xA1, 0x20}, 4);
		
		t.add(new MidiEvent(tempomsg, 0));
		
		for(MidiEvent me : eventlist)
		{
			t.add(me);
		}
		
		printf("There are " + eventlist.size() + " events...");
		
		return seq;
	}
	
	public int writeMidi(ByteBuffer bb) {
		if(bb!=null) {
			bb.order(ByteOrder.BIG_ENDIAN);
			bb.put((byte)'M');
			bb.put((byte)'T');
			bb.put((byte)'h');
			bb.put((byte)'d');
			bb.putInt(6);
			bb.putShort((short)0);
			bb.putShort((short)1);
			bb.putShort((short)60);
		}

		int len = writeSequences(bb);
		
		return len + 14;
	}
	
	public int writeSequences(ByteBuffer bb) {
		int size_pos = 0;
		if(bb!=null) {
			bb.put((byte)'M');
			bb.put((byte)'T');
			bb.put((byte)'r');
			bb.put((byte)'k');
			size_pos = bb.position();
			bb.putInt(0);
		}
		
		long lasttime = 0;
		long time = 0;
		int i=8;
		int last_status = -1;
		
		for(Sequence s : sequences) {
			time = 0; // needs reset
			Track [] tracks = s.getTracks();
			if(tracks.length==0)
				continue;
			int events = tracks[0].size();
			for(int ei=0; ei<events; ei++) {
				MidiEvent me = tracks[0].get(ei);
				long mt = me.getTick();
				MidiMessage mm = me.getMessage();
				int status = mm.getStatus();
				int len = mm.getLength();
				byte [] msg = mm.getMessage();
				
				if(status == 0xFF || msg[1] == 0x2F) {
					lasttime = mt;
					continue;
				}
				
				long delta = mt - time;
				time = mt;
				
				int iadd = putVLQ((int)delta, bb);
				
				if(iadd>4) {
					System.out.println("VLQ Error for " + delta + " at " + ei);
				}
				
				i += iadd;
				
				if(last_status!=status || status>=0xF0) {
					if(bb!=null)
						bb.put((byte)status);
					i++;
				}

				last_status = status;
				
				switch(status>>>4) {
				case 0x8: case 0x9: case 0xA: case 0xB: case 0xE:
					if (bb!=null)
					{
						bb.put(msg[1]);
						bb.put(msg[2]);
					}
					i += 2;
					break;
					

					// 1 bytes data
					// Program Change and Channel Pressure
					case 0xC: case 0xD:
					if(bb!=null) {
						bb.put(msg[1]);
					}
					i++;
					break;
					

					// Variable length
					// SysEx
					case 0xF:
					int plen = len - 1;
					int pindex = 1;
					if (status == 0xFF)
					{
						if(bb!=null) {
							bb.put(msg[1]);
						}
						i++;
						plen--;
						pindex++;
					}
			
					i += putVLQ (plen, bb);
					
					if (plen>0)
					{
						for (int j = 0; j < plen; j++)
						{
							if(bb!=null) {
								bb.put(msg[pindex+j]);
							}
							i++;
						}
					}

					break;
					

					// Never occur
					default:
						System.err.println("!!!");
					break;

				}
			}
		}
		
		if(lasttime>time) {
			i += putVLQ((int)(lasttime-time), bb);
		} else {
			i += putVLQ(0, bb);
		}
		if(bb!=null) {
			bb.put((byte)0xFF);
			bb.put((byte)0x2F);
		}
		i+= 2 + putVLQ(0, bb);
		
		if(bb!=null) {
			int current = bb.position();
			bb.position(size_pos);
			bb.putInt(i-8);
			bb.position(current);
		}
		
		return i;
	}
}
