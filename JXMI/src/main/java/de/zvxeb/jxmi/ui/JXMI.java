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
package de.zvxeb.jxmi.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

import de.zvxeb.jxmi.XMIException;
import de.zvxeb.jxmi.XMI;

public class JXMI {
	
	static class SequencerFinalizer extends Thread {
		private Sequencer s;
		
		public SequencerFinalizer(Sequencer s) {
			this.s = s;
		}
		
		public void run() {
			if(s.isRunning()) {
				System.out.println("Stopping sequencer...");
				s.stop();
			}
			if(s.isOpen()) {
				System.out.println("Closing sequencer...");
				s.close();
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		byte [] buffer = new byte [1024];
		int r;
		for(String filename : args)
		{
			File f = new File(filename);
			
			if(f.canRead())
			{
				try {
					FileInputStream fis = new FileInputStream(f);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					
					while((r = fis.read(buffer))!=-1)
					{
						baos.write(buffer, 0, r);
					}
					
					if(baos.size()>0)
					{
						byte [] data = baos.toByteArray();
						ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
						XMI xmi = new XMI(bb);
						
						System.out.println(String.format("File %s has %d tracks...", filename, xmi.getTrackCount()));
						
						int midilen = xmi.writeMidi(null);
						
						byte [] mididata = new byte [midilen];
						
						ByteBuffer midibb = ByteBuffer.wrap(mididata);
						
						xmi.writeMidi(midibb);
						
						System.out.println("Writing MIDI");
						FileOutputStream fos = new FileOutputStream(filename+".mid");
						
						fos.write(mididata);
						
						fos.close();
						
						List<Sequence> seqlist = xmi.getSequences();
						Sequencer s = null;

						MidiDevice.Info [] midiinfo = MidiSystem.getMidiDeviceInfo();
						MidiDevice bestDevice = null;
						for(MidiDevice.Info mi : midiinfo) {
							System.out.println(mi.getDescription());
							MidiDevice midev = MidiSystem.getMidiDevice(mi);
							if(!(midev instanceof Sequencer) && !(midev instanceof Synthesizer)) {
								System.out.println("Hardware!");
								if(midev instanceof Sequencer)
									System.out.printf("Is sequencer");
								if(midev instanceof Synthesizer)
									System.out.printf("Is synthesizer");
								if(bestDevice==null)
									bestDevice = midev;
							}
						}

						Synthesizer syn = null;
						try {
							syn = MidiSystem.getSynthesizer();
						} catch(MidiUnavailableException mue) {
							System.out.println("No default synthesizer available... " + mue.getMessage());
						}
						
						if(syn != null) {
							System.out.println("Synthesizer aquired: " + syn.getLatency() + "ms latency");
							int mr = syn.getMaxReceivers();

							if(mr > 0) {
								System.out.println("Max receivers: " + mr);
								Receiver rec = syn.getReceiver();
							}
						}
						
						if(bestDevice == null) {
							s = MidiSystem.getSequencer(true);
						} else {
							s = MidiSystem.getSequencer(true);
						}
						
						if(s instanceof Synthesizer) {
							System.out.println("Sequencer is also Synthesizer!");
						}
						
						System.out.println("Sequencer class: " + s.getClass());
						

						s.open();
						Runtime.getRuntime().addShutdownHook(new SequencerFinalizer(s));
						
						Transmitter trn = s.getTransmitter();
						if(trn != null) {
							System.out.println("Trn: " + trn.getClass());
							Receiver rec = trn.getReceiver();
							if(rec != null) {
								System.out.println("Trn->Rec: " + rec.getClass());
							}
						}
						
						int sequence_number = 1;
						for(Sequence seq : seqlist)
						{
							if(!s.isOpen())
								break;
							
							System.out.println("Trying to play sequence " + sequence_number++ + "...");
							//Sequence seq = seqlist.get(0);
							System.out.println("Length: " + seq.getTickLength());
							
							if(seq.getTickLength()<1)
								continue;
							
							s.setSequence(seq);
							if(s.isOpen() && !s.isRunning())
								s.start();
							
							long startTime = System.currentTimeMillis();
							int lastsec = -1;
							
							while(s.isRunning())
							{
								long elapsed = System.currentTimeMillis() - startTime;
								if((elapsed/1000)!=lastsec) {
									System.out.println("...playing...");
									lastsec = (int)(elapsed/1000);
								}
								try {
									Thread.sleep(50);
								} catch (InterruptedException e) {
								}
							}
							
						}
						if(s.isRunning()) {
							s.stop();
						}
						if(s.isOpen()) {
							s.close();
						}
					}
					
				} catch (FileNotFoundException e) {
					
				} catch (IOException e) {
				} catch (XMIException e) {
					e.printStackTrace();
				} catch (InvalidMidiDataException e) {
					e.printStackTrace();
				} catch (MidiUnavailableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

}
