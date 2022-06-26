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
package de.zvxeb.jres.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import de.zvxeb.jres.Utils;

public class UtilTest {
	
	public static void showArray(byte [] a, int w) {
		int i = 0;
		int cw = 0;
		
		while(i < a.length) {
			if(cw == w) {
				System.out.println();
				cw = 0;
			}
			
			System.out.print(String.format("%02X", a[i++]));
			cw++;
			
			if(i < a.length) {
				System.out.print(", ");
			}
		}
		System.out.println();
	}
	
	public static String showBits(long value, int bits) {
		String r = "";
		
		long maxBit = 0;
		if(bits < 0) {
			maxBit = Long.highestOneBit(value);
		} else {
			if(bits > 0) {
				maxBit = (long)1 << (bits - 1);
			}
		}
		
		if(maxBit == 0) {
			r = "0";
		} else {
			long i = 1;
			while(i <= maxBit) {
				r = ((value & i) != 0 ? "1" : "0") + r;
				i<<=1;
			}
		}
				
		return r;
	}
	
	public static String showBits(long value) {
		return showBits(value, -1);
	}
	
	public static String intersep(String s, String i) {
		StringBuilder sb = new StringBuilder();
		
		for(int idx = 0; idx < s.length(); idx++) {
			sb.append(s.charAt(idx));
			if(idx < s.length()-1)
				sb.append(i);
		}
		
		return sb.toString();
	}
	
	public static void main(String...args) {
		int testsize = 8;
		
		byte [] testdata = new byte [testsize];
		
		Random rnd = new Random(42L);
		
		for(int i=0; i<testsize; i++) {
			testdata[i] = (byte)rnd.nextInt();
		}
		
		ByteArrayInputStream bais = new ByteArrayInputStream(testdata);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		BitInputStream bis = new BitInputStream(bais);
		BitOutputStream bos = new BitOutputStream(baos);
		
		String inString = "";
		String outString = "";
		
		try {
			while(bis.available() > 0 || bis.remainingBits() > 0) {
				int avail = bis.available();
				int maxBits = (avail > 7) ? 64 : (avail * 8);
				
				int bitsToGet = (maxBits == 0) ? bis.remainingBits() : (1 + rnd.nextInt(maxBits));
				
				long value = bis.getLongBits(bitsToGet);
				
				System.out.println(String.format("Read %d bits : %s %08X", bitsToGet, showBits(value, bitsToGet), value));
				inString = intersep(showBits(value, bitsToGet), "|") + "+" + inString;
				
				while(bitsToGet > 0) {
					int bitsToWrite = 1 + rnd.nextInt(bitsToGet);
					
					long wvalue = (value & (-1L >>> (64 - bitsToWrite)));
					
					System.out.println(String.format("Writing %d bits : %s %08X", bitsToWrite, showBits(wvalue, bitsToWrite), wvalue));
					outString = intersep(showBits(wvalue, bitsToWrite), "|") + "+" + outString;
					
					bos.putLongBits(wvalue, bitsToWrite);
					value >>>= bitsToWrite;
					bitsToGet -= bitsToWrite;

					System.out.println(String.format("...Left %d bits : %s %08X", bitsToGet, showBits(value), value));
				}
			}
			bos.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		byte [] testresult = baos.toByteArray();
		
		System.out.println("Input:");
		showArray(testdata, 8);
		System.out.println("Output:");
		showArray(testresult, 8);
		
		System.out.println(inString);
		System.out.println(outString);
		
		baos = new ByteArrayOutputStream();
		bos = new BitOutputStream(baos);
		
		try {
			System.out.println("longtest");
			long l = 0x0123456789ABCDEFL;
			bos.setByteOrder(ByteOrder.BIG_ENDIAN).putLong(l);
			bos.flush();
			byte [] longdata = baos.toByteArray();
			bais = new ByteArrayInputStream(longdata);
			
			bis = new BitInputStream(bais);
			
			long ltest = bis.setByteOrder(ByteOrder.LITTLE_ENDIAN).getLong();
			
			System.out.println(String.format("LongTest: %016X =?= %016X", l, ltest));
			
			showArray(longdata, 8);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("BufferOp-Test");
		
		ByteBuffer bbin = ByteBuffer.wrap(testdata);
		testresult = new byte [testsize];
		ByteBuffer bbout = ByteBuffer.wrap(testresult);
		
		BitBufferOps bitbbin = new BitBufferOps(bbin);
		BitBufferOps bitbbout = new BitBufferOps(bbout);
		
		try {
			inString = "";
			outString = "";
			
			while(bbin.hasRemaining() || bitbbin.remainingBits() > 0) {
				int avail = bbin.remaining();
				int maxBits = (avail > 7) ? 64 : (avail * 8);

				int bitsToGet = (maxBits == 0) ? bitbbin.remainingBits() : (1 + rnd.nextInt(maxBits));

				long value = bitbbin.getLongBits(bitsToGet);

				System.out.println(String.format("Read %d bits : %s %08X", bitsToGet, showBits(value), value));
				inString = intersep(showBits(value, bitsToGet), "|") + "+" + inString;

				while(bitsToGet > 0) {
					int bitsToWrite = 1 + rnd.nextInt(bitsToGet);

					long wvalue = (value & (-1L >>> (64 - bitsToWrite)));

					System.out.println(String.format("Writing %d bits : %s %08X", bitsToWrite, showBits(wvalue), wvalue));
					outString = intersep(showBits(wvalue, bitsToWrite), "|") + "+" + outString;

					bitbbout.putLongBits(wvalue, bitsToWrite);
					value >>>= bitsToWrite;
					bitsToGet -= bitsToWrite;

					System.out.println(String.format("...Left %d bits : %s %08X", bitsToGet, showBits(value), value));
				}
			}
			bitbbout.flush();
			
			System.out.println("Input:");
			showArray(testdata, 8);
			System.out.println("Output:");
			showArray(testresult, 8);
			
			System.out.println(inString);
			System.out.println(outString);
		} catch (OutOfBitsException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Testing BitHelper...");
		
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		Utils.BitHelper bits= new Utils.BitHelper(bb);
		Utils.BitBufferWriter bbw = new Utils.BitBufferWriter(bb);
		
		List<Integer> pushedBits = new LinkedList<Integer>();
		List<Integer> pushedVals = new LinkedList<Integer>();
		
		int remBits = 16 * 8;
		int writtenBits = 0;
		while(remBits > 0) {
			int maxBits = remBits;
			if(maxBits > 16)
				maxBits = 16;
			
			int pushBits = 1 + rnd.nextInt(maxBits);
			int pushVal = (rnd.nextInt() & 0xFFFF) % (1<<pushBits);
			
			System.out.println(String.format("Pushing %X, %d bits (%d bits left, %d bits written)", pushVal, pushBits, remBits, writtenBits));
			bbw.putBits(pushVal, pushBits);
			remBits -= pushBits;
			writtenBits += pushBits;
			
			pushedBits.add(pushBits);
			pushedVals.add(pushVal);
		}
		bbw.flush();
		
		bb.position(0);

		Iterator<Integer> pushedValI = pushedVals.iterator();
		for(int getBits : pushedBits) {
			System.out.println(String.format("Getting %X, %d bits (=?= %X)", bits.getBits(getBits, true), getBits, pushedValI.next()));
		}
		
		
		System.out.println("Comparing stream-based writer...");
		baos = new ByteArrayOutputStream();
		Utils.BitWriter bw = new Utils.BitWriter(baos);
		
		pushedValI = pushedVals.iterator();
		try {
			for(int pushBits : pushedBits) {
				bw.putBits(pushedValI.next(), pushBits);
			}
			bw.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		ByteBuffer testbb = ByteBuffer.wrap(baos.toByteArray());
		bits = new Utils.BitHelper(testbb);
		
		pushedValI = pushedVals.iterator();
		for(int getBits : pushedBits) {
			System.out.println(String.format("Getting %X, %d bits (=?= %X)", bits.getBits(getBits, true), getBits, pushedValI.next()));
		}

	}
}
