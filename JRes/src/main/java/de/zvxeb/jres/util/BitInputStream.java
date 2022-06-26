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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

public class BitInputStream extends InputStream {
	private InputStream is = null;
	
	private byte markBits = 0;
	private byte markBitCount = 0;
	
	private byte currentBits = 0;
	private byte currentBitCount = 0;
	
	private static final ByteOrder swapOrder = ByteOrder.LITTLE_ENDIAN;

	private ByteOrder byteOrder;
	
	public BitInputStream(InputStream is, ByteOrder byteOrder) {
		this.is = is;
		this.byteOrder = byteOrder;
	}
	
	public BitInputStream(InputStream is) {
		this(is, ByteOrder.nativeOrder());
	}
	
	public InputStream getInternalStream() {
		return is;
	}
	
	public BitInputStream setByteOrder(ByteOrder byteOrder) {
		if(byteOrder == null) {
			this.byteOrder = ByteOrder.nativeOrder();
		} else {
			this.byteOrder = byteOrder;
		}
		return this;
	}
	
	public ByteOrder getByteOrder() {
		return this.byteOrder;
	}
	
	public void clearBits() {
		currentBits = 0;
		currentBitCount = 0;
	}
	
	public int remainingBits() {
		return currentBitCount;
	}
	
	public long getLongBits(int b) throws IOException, OutOfBitsException {
		long r = 0;
		
		int rbits = b;
		int shiftBits = 0;
		
		while(rbits > 0) {
			if(currentBitCount == 0) {
				int nextRead = is.read();
				
				if(nextRead == -1) {
					throw new OutOfBitsException("Unable to get next byte to gather " + b + " bits. Missing " + rbits + " more bits...");
				}
				
				currentBits = (byte)nextRead;
				currentBitCount = 8;
			}
			
			if(currentBitCount <= rbits) {
				r |= (((((long)currentBits)&(0xFF >>> (8-currentBitCount)))&0xFF) << shiftBits);
				currentBits = 0;
				rbits -= currentBitCount;
				shiftBits += currentBitCount;
				currentBitCount = 0;
			} else {
				r |= (((long)(currentBits & (0xFF >>> (8-rbits))))&0xFF) << shiftBits;
				currentBits >>>= rbits;
				shiftBits += rbits;
				currentBitCount -= rbits;
				rbits = 0;
			}
		}

		return r;
	}
	
	public int getBits(int b) throws IOException, OutOfBitsException {
		return (int)getLongBits(b);
	}

	public int getByte() throws OutOfBitsException, IOException {
		return getBits(8);
	}
	
	public int get16BitSwap() throws OutOfBitsException, IOException {
		return 
			  getBits(8) 
			| (getBits(8) << 8);
	}

	public int get24BitSwap() throws OutOfBitsException, IOException {
		return
			  getBits(8) 
			| (getBits(8) << 8)
			| (getBits(8) << 16); 
	}

	public int get32BitSwap() throws OutOfBitsException, IOException {
		return
			  getBits(8)
			| (getBits(8) << 8) 
			| (getBits(8) << 16) 
			| (getBits(8) << 24); 
	}

	public long get40BitSwap() throws OutOfBitsException, IOException {
		return
			  getLongBits(8)
			| (getLongBits(8) << 8)
			| (getLongBits(8) << 16)
			| (getLongBits(8) << 24)
			| (getLongBits(8) << 32)
			;
	}
	
	public long get48BitSwap() throws OutOfBitsException, IOException {
		return
			  getLongBits(8)
			| (getLongBits(8) << 8)
			| (getLongBits(8) << 16)
			| (getLongBits(8) << 24)
			| (getLongBits(8) << 32)
			| (getLongBits(8) << 40)
			;
	}
	
	public long get56BitSwap() throws OutOfBitsException, IOException {
		return
			  getLongBits(8)
			| (getLongBits(8) << 8)
			| (getLongBits(8) << 16)
			| (getLongBits(8) << 24)
			| (getLongBits(8) << 32)
			| (getLongBits(8) << 40)
			| (getLongBits(8) << 48)
			;
	}

	public long get64BitSwap() throws OutOfBitsException, IOException {
		return
			  (getLongBits(8) << 56)
			| (getLongBits(8) << 48)
			| (getLongBits(8) << 40)
			| (getLongBits(8) << 32)
			| (getLongBits(8) << 24)
			| (getLongBits(8) << 16)
			| (getLongBits(8) << 8)
			| (getLongBits(8) << 0)
			; 
	}
	
	public short getShort() throws OutOfBitsException, IOException {
		if(byteOrder == swapOrder) {
			return (short)getBits(16);
		}
		return (short)get16BitSwap();
	}

	public int get24BitInt() throws OutOfBitsException, IOException {
		if(byteOrder == swapOrder) {
			return getBits(24);
		}
		return get24BitSwap();
	}

	public int getInt() throws OutOfBitsException, IOException {
		if(byteOrder == swapOrder) {
			return getBits(32);
		}
		return get32BitSwap();
	}
	
	public long get48BitLong() throws OutOfBitsException, IOException {
		if(byteOrder == swapOrder) {
			return getLongBits(48);
		}
		return get48BitSwap();
	}

	public long get56BitLong() throws OutOfBitsException, IOException {
		if(byteOrder == swapOrder) {
			return getLongBits(56);
		}
		return get56BitSwap();
	}

	public long getLong() throws OutOfBitsException, IOException {
		if(byteOrder == swapOrder) {
			return getLongBits(64);
		}
		return get64BitSwap();
	}

	@Override
	public int read() throws IOException {
		return is.read();
	}

	@Override
	public int available() throws IOException {
		return is.available();
	}

	@Override
	public void close() throws IOException {
		is.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		is.mark(readlimit);
		markBits = currentBits;
		markBitCount = currentBitCount;
	}

	@Override
	public boolean markSupported() {
		return is.markSupported();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return is.read(b, off, len);
	}

	@Override
	public int read(byte[] b) throws IOException {
		return is.read(b);
	}

	@Override
	public synchronized void reset() throws IOException {
		is.reset();
		currentBits = markBits;
		currentBitCount = markBitCount;
	}

	@Override
	public long skip(long n) throws IOException {
		return is.skip(n);
	}
	
}
