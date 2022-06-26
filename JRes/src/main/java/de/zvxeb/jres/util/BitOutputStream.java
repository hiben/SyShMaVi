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
import java.io.OutputStream;
import java.nio.ByteOrder;

public class BitOutputStream extends OutputStream {
	
	private OutputStream os;
	
	private byte currentBits;
	private byte currentBitCount;
	
	private static final ByteOrder swapOrder = ByteOrder.LITTLE_ENDIAN;
	
	private ByteOrder byteOrder;
	
	public BitOutputStream(OutputStream os, ByteOrder byteOrder) {
		this.os = os;
		setByteOrder(byteOrder);
	}

	public BitOutputStream(OutputStream os) {
		this(os, null);
	}
	
	public BitOutputStream setByteOrder(ByteOrder byteOrder) {
		this.byteOrder = byteOrder;
		
		if(this.byteOrder == null) {
			this.byteOrder = ByteOrder.nativeOrder();
		}
		
		return this;
	}
	
	public int unwrittenBits() {
		return currentBitCount;
	}
	
	public void putLongBits(long value, int b) throws IOException {
		while(b > 0) {
			int put = b;
			
			if( (currentBitCount + put) > 8 ) {
				put = 8 - currentBitCount;
			}
			
			currentBits |= ((value & ( 0xFF >>> (8-put))) << currentBitCount);
			currentBitCount += put;
			
			value >>>= put;
			b -= put;
			
			if(currentBitCount == 8) {
				os.write(currentBits);
				currentBits = 0;
				currentBitCount = 0;
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
	
	@Override
	public void write(int b) throws IOException {
		os.write(b);
	}

	@Override
	public void close() throws IOException {
		if(currentBitCount>0) {
			os.write(currentBits);
			currentBits = 0;
			currentBitCount = 0;
		}
		os.close();
	}

	@Override
	public void flush() throws IOException {
		if(currentBitCount>0) {
			os.write(currentBits);
			currentBits = 0;
			currentBitCount = 0;
		}
		os.flush();
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		os.write(b, off, len);
	}

	@Override
	public void write(byte[] b) throws IOException {
		os.write(b);
	}
	
	

}
