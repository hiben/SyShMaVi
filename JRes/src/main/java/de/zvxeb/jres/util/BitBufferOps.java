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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BitBufferOps {
	
	private ByteBuffer bb;
	
	private byte currentReadBits = 0;
	private byte currentReadBitCount = 0;
	
	private byte currentWriteBits;
	private byte currentWriteBitCount;
	
	private static final ByteOrder swapOrder = ByteOrder.LITTLE_ENDIAN;

	private ByteOrder byteOrder;
	
	public BitBufferOps(ByteBuffer bb, ByteOrder byteOrder) {
		this.bb = bb;
		setByteOrder(byteOrder);
	}

	public BitBufferOps(ByteBuffer bb) {
		this(bb, bb.order());
	}
	
	public BitBufferOps setByteOrder(ByteOrder byteOrder) {
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

	public void clearReadBits() {
		currentReadBits = 0;
		currentReadBitCount = 0;
	}
	
	public void clearWriteBits() {
		currentWriteBits = 0;
		currentWriteBitCount = 0;
	}
	
	public void clearBits() {
		currentReadBits = 0;
		currentReadBitCount = 0;
		currentWriteBits = 0;
		currentWriteBitCount = 0;
	}
	
	public int remainingBits() {
		return currentReadBitCount;
	}
	
	public BitBufferOps position(int position) {
		bb.position(position);
		flush();
		clearReadBits();
		return this;
	}
	
	public long getLongBits(int b) throws IOException, OutOfBitsException {
		long r = 0;
		
		int rbits = b;
		int shiftBits = 0;
		
		while(rbits > 0) {
			if(currentReadBitCount == 0) {
				int nextRead = bb.hasRemaining() ? bb.get() : -1;
				
				if(nextRead == -1) {
					throw new OutOfBitsException("Unable to get next byte to gather " + b + " bits. Missing " + rbits + " more bits...");
				}
				
				currentReadBits = (byte)nextRead;
				currentReadBitCount = 8;
			}
			
			if(currentReadBitCount <= rbits) {
				r |= (((((long)currentReadBits)&(0xFF >>> (8-currentReadBitCount)))&0xFF) << shiftBits);
				currentReadBits = 0;
				rbits -= currentReadBitCount;
				shiftBits += currentReadBitCount;
				currentReadBitCount = 0;
			} else {
				r |= (((long)(currentReadBits & (0xFF >>> (8-rbits))))&0xFF) << shiftBits;
				currentReadBits >>>= rbits;
				shiftBits += rbits;
				currentReadBitCount -= rbits;
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
	
	public int unwrittenBits() {
		return currentWriteBitCount;
	}
	
	public void putLongBits(long value, int b) throws IOException {
		while(b > 0) {
			int put = b;
			
			if( (currentWriteBitCount + put) > 8 ) {
				put = 8 - currentWriteBitCount;
			}
			
			currentWriteBits |= ((value & ( 0xFF >>> (8-put))) << currentWriteBitCount);
			currentWriteBitCount += put;
			
			value >>>= put;
			b -= put;
			
			if(currentWriteBitCount == 8) {
				bb.put(currentWriteBits);
				currentWriteBits = 0;
				currentWriteBitCount = 0;
			}
		}
	}
	
	public void putByte(int b) throws IOException {
		putLongBits(b, 8);
	}
	
	public void put16BitSwap(long l) throws IOException {
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}

	public void put24BitSwap(long l) throws IOException {
		putLongBits((l >>> 16) & 0xFF, 8);
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}
	
	public void put32BitSwap(long l) throws IOException {
		putLongBits((l >>> 24) & 0xFF, 8);
		putLongBits((l >>> 16) & 0xFF, 8);
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}
	
	public void put40BitSwap(long l) throws IOException {
		putLongBits((l >>> 32) & 0xFF, 8);
		putLongBits((l >>> 24) & 0xFF, 8);
		putLongBits((l >>> 16) & 0xFF, 8);
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}
	
	public void put48BitSwap(long l) throws IOException {
		putLongBits((l >>> 40) & 0xFF, 8);
		putLongBits((l >>> 32) & 0xFF, 8);
		putLongBits((l >>> 24) & 0xFF, 8);
		putLongBits((l >>> 16) & 0xFF, 8);
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}
	
	public void put56BitSwap(long l) throws IOException {
		putLongBits((l >>> 48) & 0xFF, 8);
		putLongBits((l >>> 40) & 0xFF, 8);
		putLongBits((l >>> 32) & 0xFF, 8);
		putLongBits((l >>> 24) & 0xFF, 8);
		putLongBits((l >>> 16) & 0xFF, 8);
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}
	
	public void put64BitSwap(long l) throws IOException {
		putLongBits((l >>> 56) & 0xFF, 8);
		putLongBits((l >>> 48) & 0xFF, 8);
		putLongBits((l >>> 40) & 0xFF, 8);
		putLongBits((l >>> 32) & 0xFF, 8);
		putLongBits((l >>> 24) & 0xFF, 8);
		putLongBits((l >>> 16) & 0xFF, 8);
		putLongBits((l >>> 8) & 0xFF, 8);
		putLongBits((l >>> 0) & 0xFF, 8);
	}

	public void putShort(short s) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(s, 16);
		} else {
			put16BitSwap(s);
		}
	}

	public void put24BitInt(int i) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(i, 24);
		} else {
			put24BitSwap(i);
		}
	}
	
	public void putInt(int i) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(i, 32);
		} else {
			put32BitSwap(i);
		}
	}

	public void put40BitLong(long l) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(l, 40);
		} else {
			put40BitSwap(l);
		}
	}

	public void put48BitLong(long l) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(l, 48);
		} else {
			put48BitSwap(l);
		}
	}

	public void put56BitLong(long l) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(l, 56);
		} else {
			put56BitSwap(l);
		}
	}
	
	public void putLong(long l) throws IOException {
		if(byteOrder == swapOrder) {
			putLongBits(l, 64);
		} else {
			put64BitSwap(l);
		}
	}
	
	public void flush() {
		if(currentWriteBits > 0) {
			int pos = bb.position();
			byte val = bb.get();
			val &= ~(0xFF >>> (8-currentWriteBitCount));
			val |= currentWriteBits;
			bb.put(pos, val);
			currentWriteBits = 0;
			currentWriteBitCount = 0;
		}
	}

}
