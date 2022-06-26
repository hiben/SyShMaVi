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
 * Created on 18.09.2008
 */
package de.zvxeb.jres;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import de.zvxeb.jres.util.Util;

// LG Res File version 2.0 (biased to system shock)
public class ResFile 
{	
	private static final boolean doDebugResRead =
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.debugResRead", "false"));
	
	private static final int debugResReadLevel =
		Integer.parseInt(System.getProperty("org.hiben.jres.ResFile.debugResReadLevel", "0"));
	
	private static final boolean doDebugUnpack =
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.unpackDebug", "false"));
	
	private static final boolean doDebugPack =
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.packDebug", "false"));
	
	private static final boolean packEmitReset =
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.packEmitReset", "true"));
	
	private static final boolean createCompressionGraph = 
		Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.unpackCreateGraph", "false"));
		
	private static final String compressionGraphFile = System.getProperty("usr.home",".") + File.pathSeparator + "compgraph.dot";
	
	public static void printf(String fmt, Object...args) {
		System.out.print(String.format(Locale.US, fmt, args));
	}

	public static void fprintf(PrintStream ps, String fmt, Object...args) {
		ps.print(String.format(Locale.US, fmt, args));
	}
	
	private static void debugUnpack(String fmt, Object...args) {
		if(!doDebugUnpack)
			return;
		
		printf(fmt+"\n", args);
	}

	private static void debugPack(String fmt, Object...args) {
		if(!doDebugPack)
			return;
		
		printf(fmt+"\n", args);
	}
	
	private static void debugResRead(int lvl, String fmt, Object...args) {
		if(!doDebugResRead)
			return;
		
		if(lvl > debugResReadLevel)
			return;
		
		printf(fmt+"\n", args);
	}
	
	// full header size (bytes)
	public static final int header_size = 128;

	// magic identifier
	public static final byte [] header_id =
	{
		  'L', 'G', ' ', 'R', 'e', 's', ' ', 'F', 'i','l','e', ' ', 'v', '2'
		, 0x0D, 0x0A, 0x1A
	};
	
	public static final String header_id_str = asciiString(header_id);
	
	public static String asciiString(byte [] bytes) {
		try {
			return new String(bytes, "ASCII");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.exit(1);
			return null; // make compiler happpy
		}
	}
	
	// size of magic
	public static final int header_id_size = header_id.length;
	
	// start of comment
	public static final int comment_offset = header_id.length;
	
	// position of directory offset
	public static final int directory_offset = 0x7C;

	public static final int comment_length = directory_offset - comment_offset;
	
	public static final int CT_FLAT = 0;
	public static final int CT_FLAT_COMPRESSED = 1;
	public static final int CT_SUBDIR = 2;
	public static final int CT_SUBDIR_COMPRESSED = 3;
	
	public static final int CCT_PALETTE = 0x00;
	public static final int CCT_TEXT = 0x01;
	public static final int CCT_BITMAP = 0x02;
	public static final int CCT_FONT = 0x03;
	public static final int CCT_VIDEO = 0x04;
	public static final int CCT_SOUND = 0x07;
	public static final int CCT_3DMODEL = 0x0F;
	public static final int CCT_AUDIO_CUTSCENE = 0x11;
	public static final int CCT_MAP = 0x30;
	
	public static final int COMPRESSION_MASK = 0x3FFF;
	public static final int COMPRESSION_EOS = 0x3FFF;
	public static final int COMPRESSION_RESET = 0x3FFE;
	public static final int COMPRESSION_TOKEN_OFFSET = 0x100;
	public static final int COMPRESSION_MAXTOKEN = (COMPRESSION_RESET - 1) - COMPRESSION_TOKEN_OFFSET;
	// maxtokens is 18384 in the C code but this can not be packed since the resulting token values 
	// would have more than 14 bits... (this took a while to debug...)
	// System Shock does not seem to use higher values either...
	public static final int COMPRESSION_MAXTOKENS = COMPRESSION_MAXTOKEN + 1;
	public static final int COMPRESSION_BITSIZE = 14;
	
	public static String chunkContentExt(int cct) {
		switch(cct) {
		case CCT_PALETTE:
			return "pal";
		case CCT_TEXT:
			return "txt";
		case CCT_BITMAP:
			return "sbm";
		case CCT_FONT:
			return "fnt";
		case CCT_VIDEO:
			return "vid";
		case CCT_SOUND:
			return "voc";
		case CCT_3DMODEL:
			return "m3f";
		case CCT_AUDIO_CUTSCENE:
			return "cut";
		case CCT_MAP:
			return "map";
		default:
			return String.format("u%02x", cct);
		}
	}
	
	public static boolean isString(String s1, String s2) {
		return s1.equalsIgnoreCase(s2);
	}
	
	public static boolean isChar(char c1, char c2) {
		return c1 == c2 || Character.toLowerCase(c1) == Character.toLowerCase(c2);
	}
	
	public static boolean isHexDigit(char c) {
		if(Character.isDigit(c))
			return true;
		
		c = Character.toLowerCase(c);
		
		return c == 'a' || c == 'b' || c == 'c' || c == 'd' || c == 'e' || c == 'f';
	}
	
	public static int chunkContentFromExt(String ext) {
		if(isString(ext, "pal")) return CCT_PALETTE;
		if(isString(ext, "txt")) return CCT_TEXT;
		if(isString(ext, "sbm")) return CCT_BITMAP;
		if(isString(ext, "fnt")) return CCT_FONT;
		if(isString(ext, "vid")) return CCT_VIDEO;
		if(isString(ext, "voc")) return CCT_SOUND;
		if(isString(ext, "m3f")) return CCT_3DMODEL;
		if(isString(ext, "cut")) return CCT_AUDIO_CUTSCENE;
		if(isString(ext, "map")) return CCT_MAP;
		
		if(ext.length() == 3 && isChar(ext.charAt(0), 'u') && isHexDigit(ext.charAt(1)) && isHexDigit(ext.charAt(2))) {
			return Integer.parseInt(ext.substring(1), 16);
		}
		
		return -1;
	}
	
	public static int chunkIdFromName(String name) {
		int breakIndex;
		for(breakIndex=0; breakIndex<name.length(); breakIndex++) {
			char c = name.charAt(breakIndex);
			
			if(c=='.')
				break;
			
			if(!Character.isDigit(c))
				return -1;
		}
		
		if(breakIndex==0)
			return -1;
		
		int id = Integer.parseInt(name.substring(0, breakIndex));
		
		if(id > Short.MAX_VALUE)
			return -1;
		
		return id;
	}
	
	public static class ChunkFilenameFilter implements FilenameFilter {

		@Override
		public boolean accept(File dir, String name) {
			String ext = Util.getFileExt(name);
			return chunkContentFromExt(ext) != -1;
		}
		
	}
	
	private File file;
	
	private MappedByteBuffer buffer;
	
	private byte [] comment = new byte [comment_length];
	
	private int buffer_size;
	
	private short lowest_chunk, highest_chunk;
	
	private short number_of_chunks;
	private int first_chunk_offset;
	
	private DirEntry [] entry;
	private Map<Short, DirEntry> entrymap;
	private Map<Short, SubDirectory> subdirmap;
	
	protected ResFile(int numChunks) {
		if(numChunks < 0 || numChunks > Short.MAX_VALUE)
			throw new IllegalArgumentException("Invalid number of chunks: " + numChunks);
		
		this.number_of_chunks = (short)numChunks;
		entry = new DirEntry [numChunks];
		entrymap = new TreeMap<Short, DirEntry>();
		subdirmap = new TreeMap<Short, SubDirectory>();
	}
	
	public ResFile(File f) throws FileNotFoundException, IOException, ResFileException
	{
		this.file = f;
		
		RandomAccessFile raf = new RandomAccessFile(f, "r");
		
		long llen = raf.length();

		debugResRead(0, "Opening ResFile %s, size = %2$d (%2$X)", f.toString(), llen);
		
		if(llen > Integer.MAX_VALUE)
		{
			raf.close();
			throw new ResFileException("Sorry, file to large for me...");
		}
		
		if(llen < 134) // header + minimum dir
		{
			raf.close();
			throw new ResFileException("Invalid Res file (too small)...");
		}
		
		buffer_size = (int)llen;
		
		buffer = raf.getChannel().map(MapMode.READ_ONLY, 0, llen);
		
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		
		if(!checkHeader())
		{
			raf.close();
			throw new ResFileException("Invalid Res file Header!");
		}
		
		lowest_chunk = Short.MAX_VALUE;
		highest_chunk = Short.MIN_VALUE;
		
		buffer.position(comment_offset);
		buffer.get(comment);
		
		debugResRead(1, "Comment: \"%s\"", asciiString(comment).trim());
		
		readDirectory();
		
		raf.close();
	}
	
	public File getFile()
	{
		return file;
	}
	
	private boolean checkHeader() throws UnsupportedEncodingException
	{
		byte [] head_buf = new byte [header_id_size];
		
		buffer.get(head_buf, 0, header_id_size);
		
		String tmp_header = new String(head_buf, "ASCII");
		
		return (header_id_str.compareToIgnoreCase(tmp_header)==0);
	}
	
	private void readDirectory() throws ResFileException
	{
		buffer.position(directory_offset);
		int diroffset = buffer.getInt();
		
		debugResRead(0, "DirOffset: %1$d (%1$04X)", diroffset);
		
		if(diroffset>=buffer_size)
			throw new ResFileException(String.format("Invalid Directory Offset 0x%x", diroffset));
		
		buffer.position(diroffset);
		
		number_of_chunks = buffer.getShort();
		first_chunk_offset = buffer.getInt();
		
		debugResRead(0, "#Chunks: %1$d, first offset: %2$d (%2$04X)", number_of_chunks, first_chunk_offset);
		
		if(number_of_chunks<0)
			throw new ResFileException("Negative number of chunks!");
		
		entry = new DirEntry [number_of_chunks];
		entrymap = new TreeMap<Short, DirEntry>();
		subdirmap = new TreeMap<Short, SubDirectory>();
		
		readEntries();
		
		readSubDirs();
	}
	
	private void readEntries() throws ResFileException
	{
		int chunk_offset = first_chunk_offset;
		for(int ci=0; ci<number_of_chunks; ci++)
		{
			DirEntry e = new DirEntry(this);
			
			e.chunk_id = buffer.getShort();
			//cd.chunk_length = Util.read_LES24(buffer);
			e.chunk_length = buffer.getInt()&0x00FFFFFF;
			buffer.position(buffer.position()-1);
			e.chunk_type = buffer.get();
			//cd.packed_length = Util.read_LES24(buffer);
			e.packed_length = buffer.getInt()&0x00FFFFFF;
			buffer.position(buffer.position()-1);
			e.content_type = buffer.get();
			
			e.chunk_fileoffset = chunk_offset;
			
			entry[ci] = e;
			entrymap.put(Short.valueOf(e.chunk_id), e);
			
			chunk_offset += ((e.packed_length + 3)&(~3)); // four byte boundaries
			
			if(highest_chunk<e.chunk_id)
				highest_chunk = e.chunk_id;

			if(lowest_chunk>e.chunk_id)
				lowest_chunk = e.chunk_id;
			debugResRead(1, "Chunk #%1$d, Id: %2$d (%2$X), offset: %3$d (%3$04X)", ci, e.chunk_id, e.chunk_fileoffset);
		}
	}
	
	private void readSubDirs() throws ResFileException
	{
		for(int ei=0; ei<number_of_chunks; ei++)
		{
			DirEntry e = entry[ei];
			
			if(e.isSubDirectory())
			{
				buffer.position(e.chunk_fileoffset);
				SubDirectory csd = new SubDirectory(e);
				
				csd.number_of_subchunks = buffer.getShort();
				
				debugResRead(2, "Reading subdir for Chunk #%d with %d entries... compressed : %s\n", e.chunk_id, csd.number_of_subchunks, e.isCompressed() ? "yes" : "no");
				
				if(csd.number_of_subchunks<0)
					throw new ResFileException("Negative number of SubChunks! #" + e.chunk_id + ": " + csd.number_of_subchunks);
				
				csd.subchunk_offset = new int [csd.number_of_subchunks];
				
				for(int sci=0; sci<csd.number_of_subchunks; sci++) {
					csd.subchunk_offset[sci] = buffer.getInt();
					debugResRead(2, "... subchunk %d has offset %d\n", sci, csd.subchunk_offset[sci]);
				}
				
				csd.total_chunk_length = buffer.getInt();
				
				subdirmap.put(Short.valueOf(e.chunk_id), csd);
			}
		}
	}
	
	/*
	 * The compression used in system shock is a simple run-length encoding algorithm.
	 * Compressed data is a stream of 14bit words (terminated by a byte with value 0).
	 * There a two special words 0x3FFF = 'end of compressed stream' and 0x3FFE =
	 * 'dictionary reset'. Every other word contains either a direct byte value to write
	 * or an index into the dictionary (a 'token').
	 * The dictionary is used to repeat a piece of uncompressed data at a later point and
	 * each entry in the dictionary consists of an offset into the uncompressed data, a length
	 * and optionally a link to a previous token.
	 * Initially the dictionary is initialized to contain 16126 entries where each has a length of
	 * 1, an offset of -1 and no previous token. The next token id is set to 0.
	 * When a word is read the algorithm determines whether it is a direct value or a token.
	 * Tokens have values above 0xFF (255). A direct value is directly written as uncompressed
	 * data. For a token (subtract 0x100 from value), its length is determined.
	 * If the token has never been used (length is 1) set its length to 1 + the length of the previous
	 * token (if there is one) or to 2 (if there is none).
	 * Regardless of direct value of token, a new token is made (if possible) and its offset is set
	 * to the current position in the unpacked data (e.g. the index of the first _unwritten_ byte).
	 * The previous token is set to the current token (if it is a token). The length is not set (1)
	 * Writing uncompressed data for tokens is done by repeating data from the tokens offset with the 
	 * given length (length will be at least 2 bytes). Note that it is possible (and common) that the
	 * source and destination areas overlap therefore initialize the unpacked data to zero. Note also
	 * that a token length might result in larger data than you wanted to unpack; just stop unpacking in
	 * that case.
	 * After the data there will be a 'end of compressed stream' symbol followed by a single byte of value zero.
	 * When encountering a dictionary reset, initialize dictionary (and token count) to starting condition. 
	 */
	 /** 
	 * @param packed compressed data
	 * @param packsize number of bytes in packed (only used in debugging, can be set to zero)
	 * @param unpacked can be zero
	 * @param unpacksize expected size of uncompressed data
	 * @return
	 */
	public static byte [] unpack(byte [] packed, int packsize, byte [] unpacked, int unpacksize)
	{
		if(unpacked==null)
			unpacked = new byte [unpacksize];
		else
			Arrays.fill(unpacked, 0, unpacksize, (byte)0);

		int ntokens = 0;
		int [] offs_token = new int [COMPRESSION_MAXTOKENS]; 
		int [] len_token = new int [COMPRESSION_MAXTOKENS]; 
		int [] org_token = new int [COMPRESSION_MAXTOKENS];

		Arrays.fill(len_token, 0, COMPRESSION_MAXTOKENS, 1);
		Arrays.fill(org_token, 0, COMPRESSION_MAXTOKENS, -1);

		int bytesdone = 0;
		int nbits = 0;
		int word = 0;
		int unpackindex = 0;
		
		boolean hasEnd = false;
		
		debugUnpack("Unpack %d bytes from %d bytes", unpacksize, packsize);

		while(bytesdone<unpacksize)
		{
			while(nbits < COMPRESSION_BITSIZE)
			{
				word = (word << 8) | (((int)packed[unpackindex++])&0xFF);
				nbits += 8;
			}

			nbits -= COMPRESSION_BITSIZE;

			int val = (word >>> nbits)&COMPRESSION_MASK;

			if(val==COMPRESSION_EOS)
			{
				debugUnpack("Found End-Of-Compressed-Stream symbol...");
				hasEnd = true;
				break;
			}

			if(val==COMPRESSION_RESET)
			{
				debugUnpack("Dictionary reset done %d, unpackindex (+1) %d...", bytesdone, unpackindex);
				Arrays.fill(len_token, 0, COMPRESSION_MAXTOKENS, 1);
				Arrays.fill(org_token, 0, COMPRESSION_MAXTOKENS, -1);
				ntokens = 0;
				continue;
			}

			if(ntokens < COMPRESSION_MAXTOKENS)
			{
				offs_token [ntokens] = bytesdone;
				if(val >= COMPRESSION_TOKEN_OFFSET)
					org_token[ntokens] = val - COMPRESSION_TOKEN_OFFSET;
				debugUnpack("Adding token #%d at %04X, org-token %d", ntokens, bytesdone, org_token[ntokens]);
				ntokens++;
			} else {
				debugUnpack("No space left for new token at %04X", bytesdone);
			}
			
			int writeLen = 1;
			int token = -1;
			int tokenOffs = 0;
			
			if(val < COMPRESSION_TOKEN_OFFSET)
			{
				debugUnpack("Writing direct value %02X at %d", val, bytesdone);
				unpacked[bytesdone++] = (byte)val;
			}
			else
			{
				token = val - COMPRESSION_TOKEN_OFFSET;

				if(len_token[token] == 1)
				{
					if(org_token[token] != -1)
					{
						len_token[token] += len_token[org_token[token]];
					}
					else
					{
						len_token[token] += 1;
					}
				}
				
				writeLen = len_token[token];
				tokenOffs = offs_token[token];

				debugUnpack("Writing %d bytes from %d at %d", len_token[token], tokenOffs, bytesdone);
				for(int i = 0; i < writeLen; ++i)
				{
					if(bytesdone>=unpacksize)
						break;
					debugUnpack("...unpacked[%d] <- unpacked[%d] = %02X", bytesdone, i + tokenOffs, unpacked[i + tokenOffs]);
					unpacked[bytesdone++] = unpacked[i + tokenOffs];
				}
			}
		}
		
		if(!hasEnd) {
			while(nbits < COMPRESSION_BITSIZE)
			{
				word = (word << 8) | (((int)packed[unpackindex++])&0xFF);
				nbits += 8;
			}

			debugUnpack("End-Of-Stream check: word=%04X, bits = %d", word, nbits);

			nbits -= COMPRESSION_BITSIZE;

			int val = (word >>> nbits)&COMPRESSION_MASK;
			
			if(val == COMPRESSION_EOS) {
				hasEnd = true;
			}
		}
		
		if(!hasEnd) {
			debugUnpack("Warning: No end-of-stream marker found!");
		} else {
			if( ! (unpackindex < packsize && packed[unpackindex++] == 0) ) {
				debugUnpack("Warning: No trailing zero byte found after compressed data!");
			}
		}
		
		debugUnpack("Used %d of %d bytes of packed data, remaining bits in word %d, word=%04X", unpackindex, packsize, nbits, word);
		while(unpackindex < packsize) {
			debugUnpack("...packedbyte[%d] = %02X", unpackindex, packed[unpackindex]);
			unpackindex++;
		}

		if(createCompressionGraph) {
			try {
				FileOutputStream fos = new FileOutputStream(compressionGraphFile);
				
				PrintStream ps = new PrintStream(fos);
				
				ps.format("digraph compression {\n");
				for(int i=0; i<ntokens; i++) {
					if(org_token[i] == -1) {
						ps.format("n%d [label=\"n%d : %02X\"];\n", i, i, unpacked[offs_token[i]]);
					} else {
						ps.format("n%d [label=\"n%d\"];\n", i, i);
						ps.format("n%d -> n%d;\n", i, org_token[i]);
					}
					if(i > 0)
						ps.format("n%d -> n%d [color=blue];\n", i-1, i);
				}
				ps.format("}\n");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		

		return unpacked;
	}
	
	public static byte [] pack(byte [] data) {
		int ntokens = 0;
		int [] offs_token = new int [COMPRESSION_MAXTOKENS]; 
		int [] len_token = new int [COMPRESSION_MAXTOKENS]; 
		int [] org_token = new int [COMPRESSION_MAXTOKENS];

		Arrays.fill(len_token, 0, COMPRESSION_MAXTOKENS, 1);
		Arrays.fill(org_token, 0, COMPRESSION_MAXTOKENS, -1);

		int bytesdone = 0;
		int nbits = 0;
		int word = 0;
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
		
		while(bytesdone < data.length) {
			
			int bestToken = -1;
			int bestLen = 1;
			
			for(int t = 0; t<ntokens; t++) {
				int toffs = offs_token[t];
				int tlen = len_token[t] + 1;
				
				if(tlen < bestLen)
					continue;
				
				boolean match = true;
				for(int i=0; i<tlen; i++) {

					// accept even longer repeat as decompressor will also stop
					if(bytesdone+i >= data.length) {
						match = false;
						break;
					}
					
					if(data[bytesdone+i] != data[toffs+i])
					{
						match = false;
						break;
					}
				}
				
				if(match) {
					bestToken = t;
					bestLen = tlen;
				}
			}
			
			if(packEmitReset && ntokens >= COMPRESSION_MAXTOKENS) {
				debugPack("Emitting dictionary reset at %04X", bytesdone);
				
				word <<= COMPRESSION_BITSIZE;
				word |= COMPRESSION_RESET;
				nbits += COMPRESSION_BITSIZE;
				
				while(nbits >= 8) {
					nbits -= 8;
					baos.write( (word >>> nbits) & 0xFF  );
				}
				
				Arrays.fill(len_token, 0, COMPRESSION_MAXTOKENS, 1);
				Arrays.fill(org_token, 0, COMPRESSION_MAXTOKENS, -1);
				ntokens = 0;
				bestToken = -1;
				bestLen = 1;
			}
			
			if(ntokens < COMPRESSION_MAXTOKENS) {
				offs_token[ntokens] = bytesdone;
				org_token[ntokens] = bestToken;
				len_token[ntokens] = bestLen;
				ntokens++;
			}
			
			word <<= COMPRESSION_BITSIZE;
			if(bestToken == -1) {
				word |= (((int)data[bytesdone])&0xFF);
			} else {
				word |= (bestToken + 0x100);
			}
			
			bytesdone += bestLen;
			
			nbits += COMPRESSION_BITSIZE;
			
			while(nbits >= 8) {
				nbits -= 8;
				baos.write( (word >>> nbits) & 0xFF  );
			}
			
		}
		
		debugPack("Writing end of stream, current word = %04X, bits = %d", word, nbits);
		
		nbits += COMPRESSION_BITSIZE;
		word <<= COMPRESSION_BITSIZE;
		word |= COMPRESSION_EOS;
		
		while(nbits >= 8) {
			nbits -= 8;
			baos.write( (word >>> nbits) & 0xFF  );
		}
		
		if(nbits > 0) {
			baos.write( (word  << (8-nbits)) & 0xFF );
		}
		
		baos.write(0);
		
		return baos.toByteArray();
	}
	

	
	public static class DirEntry
	{
		public static final String [] CHUNK_TYPE_STRING =
		{
			  "flat (0x00)"
			, "flat compressed (0x01)"
			, "subdir (0x02)"
			, "subdir compressed (0x03)"
		};
		
		public static final String [] CONTENT_TYPE_STRING =
		{
			  "palette (0x00)"
			, "text (0x01)"
			, "bitmap (0x02)"
			, "font (0x03)" 
			, "video clip (0x04)"
			, "sound effect (0x07)"
			, "3D model (0x0F)"
			, "audio log / cutscene (0x11)"
			, "map (0x30)"
		};
		
		public static final int [] CONTENT_TYPE_STRING_VAL =
		{
			0, 1, 2, 3, 4, 7, 0x0F, 0x11, 0x30
		};
		
		private short chunk_id;
		private int chunk_length;
		private byte chunk_type;
		private int packed_length;
		private byte content_type;
		
		private int chunk_fileoffset;
		
		private ResFile cde_resfile;
		
		private byte [] injected_data = null;
		private ByteBuffer injectedBuffer = null;
		
		public DirEntry(ResFile rf)
		{
			this.cde_resfile = rf;
		}
		
		public ResFile getResFile()
		{
			return cde_resfile;
		}
		
		public byte getChunkType()
		{
			return chunk_type;
		}
		
		public boolean isSubDirectory()
		{
			return (chunk_type==CT_SUBDIR || chunk_type==CT_SUBDIR_COMPRESSED);
		}

		public boolean isCompressed()
		{
			return (chunk_type==CT_FLAT_COMPRESSED || chunk_type==CT_SUBDIR_COMPRESSED);
		}
		
		public boolean hasInjectedData() {
			return injected_data != null;
		}
		
		public short getChunkId()
		{
			return chunk_id;
		}
		
		public byte getContentType()
		{
			return content_type;
		}
		
		public int getFileOffset()
		{
			return chunk_fileoffset;
		}
		
		public int getPackedLength()
		{
			return packed_length;
		}

		public int getLength()
		{
			return chunk_length;
		}
		
		public ByteBuffer getDataBuffer()
		{
			if(injected_data != null)
				return injectedBuffer;
			
			return cde_resfile.getChunkDataBuffer(this);
		}
		
		public ByteBuffer getFileDataBuffer()
		{
			if(injected_data != null)
				return injectedBuffer;

			return cde_resfile.getChunkFileDataBuffer(this);
		}
		
		public byte [] getData()
		{
			if(injected_data != null)
				return injected_data;
			
			return cde_resfile.getChunkData(this);
		}

		public byte [] getFileData()
		{
			if(injected_data != null)
				return injected_data;
			
			return cde_resfile.getChunkFileData(this);
		}
		
		public void setInjectedData(byte [] data) {
			this.injected_data = data;
			
			if(injected_data == null) {
				chunk_length = cde_resfile.getChunkData(this).length;
				packed_length = cde_resfile.getChunkFileData(this).length;
				injectedBuffer = null;
			} else {
				chunk_length = injected_data.length;
				packed_length = chunk_length;
				injectedBuffer = ByteBuffer.wrap(injected_data).order(ByteOrder.LITTLE_ENDIAN);
			}
		}
		
		public String toString()
		{
			return String.format
			(
				  "Chunk # %d: %s %d (%d packed): %s @%d (0x%X)"
				, chunk_id
				, getChunkTypeString(chunk_type)
				, chunk_length
				, packed_length
				, getContentTypeString(content_type)
				, chunk_fileoffset
				, chunk_fileoffset
			);
		}
		
		public String getChunkTypeString()
		{
			return getChunkTypeString(chunk_type);
		}

		public String getContentTypeString()
		{
			return getContentTypeString(content_type);
		}
		
		public static String getChunkTypeString(int ct)
		{
			if(ct < 0 || ct>=CHUNK_TYPE_STRING.length)
				return String.format("unknown (0x%X)", ct);
					
			return CHUNK_TYPE_STRING[ct];
		}
		
		public static String getContentTypeString(int ct)
		{
			for(int i=0; i<CONTENT_TYPE_STRING_VAL.length; i++)
			{
				if(CONTENT_TYPE_STRING_VAL[i]==ct)
					return CONTENT_TYPE_STRING[i];
			}
			
			return String.format("unknown (0x%X)", ct);
		}
	}
	
	public static class SubDirectory
	{
		private DirEntry entry;
		
		private short number_of_subchunks;
		private int [] subchunk_offset;
		private int total_chunk_length;
		
		public SubDirectory(DirEntry entry)
		{
			this.entry = entry;
		}

		public DirEntry getEntry()
		{
			return entry;
		}
		
		public short getNumberOfSubChunks() {
			return number_of_subchunks;
		}
		public int[] getSubChunkOffsets() {
			return subchunk_offset;
		}
		public int getTotalChunkLength() {
			return total_chunk_length;
		}
		
		public SubChunk getSubChunk(short number)
		{
			if(number>=0 && number<subchunk_offset.length)
				return new SubChunk(this, number, subchunk_offset[number]);
			
			return null;
		}
	}
	
	public static class SubChunk
	{
		private SubDirectory subdir;
		private short number;
		private int soffs;
		
		public SubChunk(SubDirectory subdir, short number, int soffs)
		{
			this.subdir = subdir;
			this.number = number;
			this.soffs = soffs;
		}
		
		public short getNumber()
		{
			return number;
		}
		
		public int getType()
		{
			return subdir.getEntry().getChunkType();
		}

		public int getContentType()
		{
			return subdir.getEntry().getContentType();
		}
		
		public SubDirectory getSubDirectory()
		{
			return subdir;
		}
		
		public int getLength()
		{
			return subdir.getEntry().getResFile().getSubChunkLength(subdir, number);
		}
		
		public int getOffset()
		{
			return subdir.subchunk_offset[number];
		}
		
		public byte [] getData()
		{
			return subdir.getEntry().getResFile().getSubChunkData(subdir.entry, number);
		}
		
		public String toString()
		{
			return String.format("#%d @%d (0x%X)", number+1, soffs, soffs);
		}
	}
	
	public boolean hasChunk(short cno)
	{
		if(cno < lowest_chunk || cno > highest_chunk)
			return false;
		
		return entrymap.containsKey(Short.valueOf(cno));
	}
	
	public DirEntry getChunkEntry(short cno)
	{
		return entrymap.get(Short.valueOf(cno));
	}
	
	public short getHighestChunkId()
	{
		return highest_chunk;
	}
	
	public short getLowestChunkId()
	{
		return lowest_chunk;
	}
	
	public DirEntry [] getEntries()
	{
		return entry;
	}
	
	public SubDirectory getSubDirectory(DirEntry e)
	{
		return subdirmap.get(Short.valueOf(e.chunk_id));
	}
	
	public byte [] getChunkFileData(DirEntry e)
	{
		byte [] buf = new byte [e.packed_length];
		
		buffer.position(e.chunk_fileoffset);
		
		buffer.get(buf);
		
		return buf;
	}
	
	public ByteBuffer getChunkFileDataBuffer(DirEntry e)
	{
		buffer.position(e.chunk_fileoffset);
		
		return buffer;
	}
	
	public ByteBuffer getChunkDataBuffer(DirEntry e)
	{
		if(e.hasInjectedData()) {
			ByteBuffer bb = e.getDataBuffer();
			bb.position(0);
			return bb;
		}
		
		if(e.isCompressed())
		{
			byte [] buf = new byte [e.packed_length];
			
			buffer.position(e.chunk_fileoffset);
			
			buffer.get(buf);

			if(e.chunk_type == CT_FLAT_COMPRESSED)
			{	
				byte [] unpacked = unpack(buf, e.packed_length, null, e.chunk_length);
				return ByteBuffer.wrap(unpacked).order(ByteOrder.LITTLE_ENDIAN);
			}
			else
			{
				assert(e.chunk_type==CT_SUBDIR_COMPRESSED);
				
				SubDirectory csd = subdirmap.get(Short.valueOf(e.chunk_id));
				
				if(csd==null)
					return null;
				
				if(csd.number_of_subchunks>0)
				{
					// first short is number of subchunks
					// each offset is an int
					// last int is size of chunk (unpacked data)
					// packed length is computed 
					int sdirlen = (2 + (csd.number_of_subchunks * 4) + 4); 
					int plen = e.packed_length - sdirlen;

					byte [] packed = new byte [plen];

					buffer.position(e.chunk_fileoffset + csd.subchunk_offset[0]);
					buffer.get(packed);

					byte [] unpacked = unpack(packed, plen, null, csd.total_chunk_length);
					
					byte [] redata = new byte [sdirlen + csd.total_chunk_length];
					
					System.arraycopy(buf, 0, redata, 0, sdirlen);
					System.arraycopy(unpacked, 0, redata, sdirlen, csd.total_chunk_length);
					
					return ByteBuffer.wrap(redata).order(ByteOrder.LITTLE_ENDIAN);
				}
			}
			
			return null;
		}
		else
		{
			buffer.position(e.chunk_fileoffset);
			return buffer;
		}
	}
	
	public byte [] getChunkData(DirEntry e)
	{
		if(e.hasInjectedData()) {
			return e.getData();
		}

		if(e.chunk_type != CT_SUBDIR_COMPRESSED)
		{
			byte [] buf = new byte [e.packed_length];

			buffer.position(e.chunk_fileoffset);

			buffer.get(buf);

			if(e.chunk_type == CT_FLAT_COMPRESSED)
				return unpack(buf, e.packed_length, null, e.chunk_length);

			return buf;
		}
		else // CT_SUBDIR_COMPRESSED
		{
			SubDirectory csd = subdirmap.get(Short.valueOf(e.chunk_id));

			if(csd==null)
				return null;

			if(csd.number_of_subchunks>0)
			{
				int sdirlen = (2 + (csd.number_of_subchunks * 4) + 4); 
				int plen = e.packed_length - sdirlen;

				byte [] packed = new byte [plen];

				buffer.position(e.chunk_fileoffset + csd.subchunk_offset[0]);
				buffer.get(packed);

				byte [] unpacked = unpack(packed, plen, null, csd.total_chunk_length);

				byte [] redata = new byte [sdirlen + csd.total_chunk_length];

				buffer.position(e.chunk_fileoffset);
				buffer.get(redata, 0, sdirlen);
				System.arraycopy(unpacked, 0, redata, sdirlen, csd.total_chunk_length);

				return redata;
			}

			return null;
		}
	}
	
	public int getSubChunkLength(SubDirectory csd, int s)
	{
		if(s<0 || s>=csd.number_of_subchunks)
		{
			System.err.println("No such subchunk " + s);
			return -1;
		}

		int len;
		
		int offs_s = csd.subchunk_offset[s];

		if(s==(csd.number_of_subchunks-1))
			len = csd.getEntry().chunk_length - offs_s;
		else
			len = csd.subchunk_offset[s+1] - offs_s;
		
		return len;
	}
	
	public byte []  getSubChunkData(DirEntry e, int s)
	{
		if(!e.isSubDirectory())
			return null;
		
		SubDirectory csd = subdirmap.get(Short.valueOf(e.chunk_id));
		
		if(csd==null)
		{
			System.err.println("No Sub-Entry for " + e.chunk_id);
			return null;
		}
		
		if(s<0 || s>=csd.number_of_subchunks)
		{
			System.err.println("No such subchunk " + s);
			return null;
		}
		
		ByteBuffer bb = getChunkDataBuffer(e);
		
		if(bb==null)
		{
			System.err.println("Error getting data for " + e.chunk_id + "." + s);
			return null;
		}
		
		int offs_s = csd.subchunk_offset[s];
		
		int len = 0;
		
		if(s==(csd.number_of_subchunks-1))
			len = e.chunk_length - offs_s;
		else
			len = csd.subchunk_offset[s+1] - offs_s;
		
		byte [] subdata = new byte[len];
		
		bb.position(bb.position()+offs_s);
		bb.get(subdata, 0, len);
		
		return subdata;
	}
	
	private static class ExtractOptions {
		private boolean generatePNG;
		private boolean transparentPNG;
	}
	
	public void extractTo(File dir, ExtractOptions eo) {
		if(dir.isDirectory() || dir.mkdirs()) {
			for(DirEntry de : entry) {
				int contentType = de.getContentType();
				String ext = chunkContentExt(contentType);
				String name = String.format("%d.%s", de.chunk_id, ext);
				if(de.isSubDirectory()) {
					File subDir = new File(dir, name);
					if(subDir.isDirectory() || subDir.mkdir()) {
						SubDirectory sd = getSubDirectory(de);
						assert(sd != null);
						int count = sd.getNumberOfSubChunks();
						int digits = 1 + (int)Math.log10(count);
						for(int i=0; i<count; i++) {
							String subName = String.format("%0" + digits + "d.%s", i+1, ext);
							File subfile = new File(subDir, subName);
							try {
								FileOutputStream fos = new FileOutputStream(subfile);
								fos.write(getSubChunkData(de, i));
								fos.close();
								
								if(eo.generatePNG && contentType == CCT_BITMAP) {
									ResBitmap rb = new ResBitmap(getSubChunkData(de, i), de.getChunkId(), i);
									if(!rb.hasPrivatePalette())
										rb.setPalette(Util.system_shock_palette);
									BufferedImage bi = eo.transparentPNG ? rb.getARGBImage() : rb.getImage();
									ImageIO.write(bi, "png", new File(subDir, subName + ".png"));
								}
							} catch(BufferUnderflowException bue) {
								System.err.println("Buffer underflow at sub-chunk " + i);
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								System.err.println(String.format("Error while writing subchunk %d/%d for chunk %d", i+1, count, de.chunk_id));
							}
						}
					} else {
						System.err.println(String.format("Error while creating subdir for chunk #%d = %s", de.chunk_id, name));
					}
				} else {
					try {
						FileOutputStream fos = new FileOutputStream(new File(dir, name));
						fos.write(de.getData());
						fos.close();
						
						if(eo.generatePNG && contentType == CCT_BITMAP) {
							ResBitmap rb = new ResBitmap(de.getData(), de.getChunkId(), 0);
							if(!rb.hasPrivatePalette())
								rb.setPalette(Util.system_shock_palette);
							BufferedImage bi = rb.getARGBImage();
							ImageIO.write(bi, "png", new File(dir, name + ".png"));
						}
					} catch (FileNotFoundException e) { // never thrown
						e.printStackTrace();
					} catch (IOException e) {
						System.err.println(String.format("Error while writing chunk #%d to %s", de.chunk_id, name));
					}
				}
			}
		}
	}
	
	private static class ChunkFileSorter implements Comparator<File> {

		@Override
		public int compare(File o1, File o2) {
			String n1 = o1.getName();
			String n2 = o2.getName();
			
			return chunkIdFromName(n1) - chunkIdFromName(n2);
		}
		
	}
	
	public static ResFile compileFrom(File dir) {
		if(!dir.isDirectory())
			throw new IllegalArgumentException("Not a directory: " + dir);
		
		ChunkFilenameFilter cff = new ChunkFilenameFilter();
		ChunkFileSorter cfs = new ChunkFileSorter();
		
		File [] chunkFiles = dir.listFiles(cff);
		Arrays.sort(chunkFiles, cfs);
		
		int [] chunkIds = new int [chunkFiles.length];
		
		for(int i=0; i<chunkFiles.length; i++) {
			int id = chunkIdFromName(chunkFiles[i].getName());
			if(id == -1) {
				throw new IllegalArgumentException("Invalid Chunk-File: " + chunkFiles[i]);
			}
			
			if(i > 0 && chunkIds[i-1] == id) {
				throw new IllegalArgumentException("Duplicate Id: " + chunkFiles[i-1] + " == " + chunkFiles[i]);
			}
			
			chunkIds[i] = id;
		}
		
		ResFile output = new ResFile(chunkFiles.length);
		
		for(int i=0; i<chunkFiles.length; i++) {
			File chunkFile = chunkFiles[i];
			String chunkFileName = chunkFile.getName();
			String ext = Util.getFileExt(chunkFileName);
			byte contentType = (byte)chunkContentFromExt(ext);
			
			if(chunkFile.isDirectory()) {
				File [] subChunkFiles = chunkFile.listFiles(cff);
				Arrays.sort(subChunkFiles, cfs);
				ByteArrayOutputStream baos_subdir = new ByteArrayOutputStream(1024);
				int [] subLengths = new int [subChunkFiles.length];
				for(int si=0; si<subChunkFiles.length; si++) {
					File subFile = subChunkFiles[si];
					byte [] subData = Util.readFileFully(subFile);
					
					if(subData==null) {
						fprintf(System.err, "Unable to read file contents for subfile \"%s\"\n", subFile);
						subLengths[si] = 0;
					} else {
						subLengths[si] = subData.length;
						try {
							baos_subdir.write(subData);
						} catch (IOException e) {
						}
					}
				}
				byte [] subData = baos_subdir.toByteArray();
				byte [] dirData = new byte [2 + 4*subChunkFiles.length + 4];

				DirEntry de = new DirEntry(output);
				SubDirectory sd = new SubDirectory(de);
				
				sd.number_of_subchunks = (short)subChunkFiles.length;
				sd.total_chunk_length = subData.length;
				sd.subchunk_offset = new int [sd.number_of_subchunks];
				
				ByteBuffer bb = ByteBuffer.wrap(dirData).order(ByteOrder.LITTLE_ENDIAN);
				bb.putShort((short)subChunkFiles.length);
				int offset = dirData.length;
				for(int si=0; si<subChunkFiles.length; si++) {
					bb.putInt(offset);
					sd.subchunk_offset[si] = offset;
					offset += subLengths[si];
				}
				bb.putInt(subData.length);
				printf("Wrote number of subchunks: %d\n", bb.getShort(0));
				byte [] chunkData = new byte [dirData.length + subData.length];
				System.arraycopy(dirData, 0, chunkData, 0, dirData.length);
				System.arraycopy(subData, 0, chunkData, dirData.length, subData.length);
				
				de.chunk_id = (short)chunkIds[i];
				de.chunk_type = CT_SUBDIR;
				de.content_type = contentType;
				de.setInjectedData(chunkData);
				de.chunk_fileoffset = -1;
				output.entry[i] = de;
				output.entrymap.put(de.chunk_id, de);
				output.subdirmap.put(de.chunk_id, sd);
			} else {
				byte [] data = Util.readFileFully(chunkFile);
				
				if(data == null) {
					fprintf(System.err, "Unable to read file contents for \"%s\"\n", chunkFileName);
				} else {
					DirEntry de = new DirEntry(output);
					de.chunk_id = (short)chunkIds[i];
					de.chunk_type = CT_FLAT;
					de.content_type = contentType;
					de.setInjectedData(data);
					de.chunk_fileoffset = -1;
					output.entry[i] = de;
					output.entrymap.put(de.chunk_id, de);
				}
			}
		}
		
		return output;
	}
	
	public void write(OutputStream os) throws IOException {
		// compressing SubDirs is mostly inefficient...
		boolean compressSubDirs = Boolean.parseBoolean(System.getProperty("org.hiben.hres.ResFile.compressSubDirs", "false"));
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream(64*1024);
		ByteArrayOutputStream baos_dir = new ByteArrayOutputStream(1024);
		
		baos.write(header_id);
		baos.write(comment);
		
		// write placeholder for dir-offset
		baos.write(0);
		baos.write(0);
		baos.write(0);
		baos.write(0);
		
		Util.writeLE16(baos_dir, number_of_chunks);
		Util.writeLE32(baos_dir, header_size);
		
		for(DirEntry de : entry) {
			byte [] unpacked = de.getData();
			byte [] packed = null;

			if(de.isSubDirectory()){
				if(compressSubDirs) {
					// re-pack subdir
					ByteBuffer bb = ByteBuffer.wrap(unpacked).order(ByteOrder.LITTLE_ENDIAN);
					short numChunks = bb.getShort();
					byte [] dirData = new byte [2 + 4*numChunks + 4];
					bb.position(0);
					bb.get(dirData);
					byte [] chunkData = new byte [unpacked.length - dirData.length];
					bb.position(2 + 4 * numChunks + 4);
					bb.get(chunkData);
					byte [] packedChunkData = pack(chunkData);
					packed = new byte [dirData.length + packedChunkData.length];
					System.arraycopy(dirData, 0, packed, 0, dirData.length);
					System.arraycopy(packedChunkData, 0, packed, dirData.length, packedChunkData.length);
				}
			}
			else {
				packed = pack(unpacked);
			}
			
			byte [] toWrite = packed == null ? unpacked : (packed.length <= unpacked.length ? packed : unpacked);
			int pad = toWrite.length & 0x3;
			baos.write(toWrite);
			if(pad != 0) {
				pad = 4 - pad;
				while(pad > 0) {
					baos.write(0);
					pad--;
				}
			}
			
			int chunk_type = (toWrite == packed) ? (de.isSubDirectory() ? CT_SUBDIR_COMPRESSED : CT_FLAT_COMPRESSED) : (de.isSubDirectory() ? CT_SUBDIR : CT_FLAT);
			
			if(de.isCompressed() && packed.length < de.packed_length) {
				printf("Packed Chunk #%d better: from %d packed bytes, to %d\n", de.chunk_id, de.packed_length, packed.length);
			}
			
			if(!de.isCompressed() && toWrite == packed) {
				printf("Original chunk #%d was uncompressed. Now packed %d packed to %d\n", de.chunk_id, de.chunk_length, packed.length);
			}
			
			if(de.isCompressed() && toWrite == unpacked) {
				printf("Chunk #%d was packed; %d packed to %d, now unpacked.\n", de.chunk_id, unpacked.length, de.packed_length);
			}
			
			Util.writeLE16(baos_dir, de.chunk_id);
			Util.writeLE24(baos_dir, unpacked.length);
			baos_dir.write(chunk_type);
			Util.writeLE24(baos_dir, toWrite.length);
			baos_dir.write(de.content_type);
		}
		
		byte [] data = baos.toByteArray();
		
		ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		bb.position(directory_offset);
		bb.putInt(data.length);
		
		os.write(data);
		os.write(baos_dir.toByteArray());
	}
	
	public static boolean isParam(String longparam, String shortparam, String arg) {
		return (longparam != null && arg.equals("--" + longparam)) || (shortparam != null && arg.equals("-" + shortparam));
	}
	
	public static void main(String...args) {
		if(args.length == 0) {
			System.out.println
			(
				  "Commands\n"
				+ "extract infile [outdir=current directory]\t- extract a res-file\n"
				+ "rewrite infile outfile\t- read in and write out a res-file\n"
				+ "compile indir outfile\t- create a res-file from directory contents\n"
				+ "pack infile outfile\t- pack a file using the res-file algorithm\n"
				+ "unpack infile outfile\t- unpack a file using the res-file algorithm\n"
				+ "bitmap infile [outfile]\t- create bitmap from system-shock-bitmap\n"
				+ "\tif no outfile is given, it is generated from infile (png-format) (e.g. image.sbm -> image.png)\n"
				+ "sbm (options|infile|outfile)\t- create system-shock bitmaps\n"
				+ "\tinfile is any image java can process (png, jpeg, gif, ...)\n"
				+ "\tnon-byte indexed images are converted to byte indexed images using the system-shock palette\n"
				+ "\tif no outfile is given, it is generated from infile (e.g. image.png -> image.sbm)\n"
				+ "\n\tOptions:\n"
				+ "\t--privpal|-p use private palette (false) (currently not working, always system-shock palette)\n"
				+ "\t--writepal|-w use private palette (false) (write palette data to sbm)\n"
				+ "\t--compress|-c use private palette (false) (compress image)\n"
				+ "\t--hotN|-hN hot-coordinate N (1-4) (0)\n"
				+ "\t--magicN|-mN-p magic (1-3) (0)\n"
				+ "palimage [outfile]\t- create a 128x128 byte-indexed bitmap of the system-shock palette (color debugging)\n"
			);
			return;
		}
		
		String command = args[0];
		
		if(command.equalsIgnoreCase("extract")) {

			if(args.length>1) {
				File inFile = new File(args[1]);

				File outDir = new File(".");

				if(args.length > 2) {
					outDir = new File(args[2]);
				}

				try {
					ResFile rs = new ResFile(inFile);
					ExtractOptions eo = new ExtractOptions();
					eo.generatePNG = Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.generatePNG", "false"));
					eo.transparentPNG = Boolean.parseBoolean(System.getProperty("org.hiben.jres.ResFile.transparentPNG", "false"));
					rs.extractTo(outDir, eo);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ResFileException e) {
					e.printStackTrace();
				}
			}

			return;
		}
		
		if(command.equalsIgnoreCase("rewrite")) {
			
			if(args.length > 2) {
				File inFile = new File(args[1]);

				File outFile = new File(args[2]);
				
				try {
					ResFile rs = new ResFile(inFile);
					
					rs.write(new FileOutputStream(outFile));
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ResFileException e) {
					e.printStackTrace();
				}
				
				
			}
			
			return;
		}		
		
		if(command.equalsIgnoreCase("compile")) {
			if(args.length > 1) {
				File inDir = new File(args[1]);

				File outFile = new File(args[2]);
				
				ResFile rs = compileFrom(inDir);
				
				if(rs != null) {
					try {
						rs.write(new FileOutputStream(outFile));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
			return;
		}
		
		if(command.equalsIgnoreCase("pack")) {
			
			if(args.length > 2) {
				File inFile = new File(args[1]);

				File outFile = new File(args[2]);
				
				try {
					byte [] data = Util.readFileFully(inFile);
					
					if(data != null) {
						byte [] packed = pack(data);
						
						
						if(Boolean.parseBoolean(System.getProperty("test.pack", "false"))) {
							byte [] unpacked = unpack(packed, packed.length, null, data.length);
							
							for(int i=0; i<unpacked.length; i++) {
								if(unpacked[i] != data[i]) {
									fprintf(System.err, "UnPack-Error at %d (%04X)\n", i, i);
									break;
								}
							}
						}
						
						byte [] outdata = new byte [packed.length + 4];
						ByteBuffer bb = ByteBuffer.wrap(outdata);
						bb.order(ByteOrder.BIG_ENDIAN);
						bb.putInt(data.length);
						bb.put(packed);
						
						FileOutputStream fos = new FileOutputStream(outFile);
						fos.write(outdata);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return;
		}		

		if(command.equalsIgnoreCase("unpack")) {
			
			if(args.length > 2) {
				File inFile = new File(args[1]);

				File outFile = new File(args[2]);
				
				try {
					byte [] data = Util.readFileFully(inFile);
					
					if(data != null) {
						ByteBuffer bb = ByteBuffer.wrap(data);
						bb.order(ByteOrder.BIG_ENDIAN);
						int length = bb.getInt();
						byte [] packed = new byte [data.length - 4];
						bb.get(packed);
						
						byte [] outdata = unpack(packed, packed.length, null, length);
						
						FileOutputStream fos = new FileOutputStream(outFile);
						fos.write(outdata);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return;
		}
		
		if(command.equalsIgnoreCase("sbm")) {
			int argIndex = 1;
			File inFile = null;
			File outFile = null;
			boolean sspalette = true;
			boolean compress = false;
			boolean writepalette = false;
			
			boolean error = false;
			
			short h1 = 0;
			short h2 = 0;
			short h3 = 0;
			short h4 = 0;
			
			short m1 = 0;
			short m2 = 0;
			short m3 = 0;
			
			while(argIndex < args.length) {
				String arg = args[argIndex++];
				
				if(isParam("privpal", "p", arg)) {
					sspalette = false;
					continue;
				}

				if(isParam("compress", "c", arg)) {
					compress = true;
					continue;
				}

				if(isParam("writepal", "w", arg)) {
					writepalette = true;
					continue;
				}

				if(isParam("hot1", "h1", arg)) {
					h1 = Short.parseShort(args[argIndex++]);
					continue;
				}
				if(isParam("hot2", "h2", arg)) {
					h2 = Short.parseShort(args[argIndex++]);
					continue;
				}
				if(isParam("hot3", "h3", arg)) {
					h3 = Short.parseShort(args[argIndex++]);
					continue;
				}
				if(isParam("hot4", "h4", arg)) {
					h4 = Short.parseShort(args[argIndex++]);
					continue;
				}

				if(isParam("magic1", "m1", arg)) {
					m1 = Short.parseShort(args[argIndex++]);
					continue;
				}
				if(isParam("magic2", "m2", arg)) {
					m2 = Short.parseShort(args[argIndex++]);
					continue;
				}
				if(isParam("magic3", "m3", arg)) {
					m3 = Short.parseShort(args[argIndex++]);
					continue;
				}
				
				if(inFile == null) {
					inFile = new File(arg);
					continue;
				}
				
				if(outFile == null) {
					outFile = new File(arg);
					continue;
				}
				
				error = false;
				
				System.err.println("Invalid parameter: " + arg);
				
				break;
			}
			
			if(inFile == null)
				error = true;
			
			if(error) {
				System.out.println("Usage: sbm [--privpal|-p] [-h[1234] H] [-m[123] M] <infile> [<outfile>]");
				System.out.println("\t-h[1234] hotspot definition (default 0)");
				System.out.println("\t-m[123] magic definition (default 0)");
				System.out.println("\tif no outfile is given, it is generated from infile-name");
			} else {
				if(outFile == null) {
					String ext = Util.getFileExt(inFile.getName());
					int len = 0;
					
					if(ext != null)
						len = 1 + ext.length();
					
					outFile = new File(inFile.getName().substring(0, inFile.getName().length() - len) + ".sbm");
					
					System.out.println("Generating outfile: " + outFile.getName());
				}
				
				try {
					BufferedImage bimg = ImageIO.read(inFile);
					ResBitmap rb = null;
					
					if(bimg.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
						rb = new ResBitmap(bimg, sspalette, m1, m2, m3, h1, h2, h3, h4, (short)0, (short)0);
					} else {
						System.out.println("Converting non-byte-indexed image to system-shock-colors...");
						IndexColorModel icm = new IndexColorModel(8, 256, Util.system_shock_palette, 0, false);
						BufferedImage convert = new BufferedImage(bimg.getWidth(), bimg.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, icm);
						Graphics2D g2d = convert.createGraphics();
						g2d.drawImage(bimg, null, 0, 0);
						rb = new ResBitmap(convert, true, m1, m2, m3, h1, h2, h3, h4, (short)0, (short)0);
					}
					
					rb.saveImage(new FileOutputStream(outFile), true, compress, !sspalette, writepalette);
					
				} catch(IllegalArgumentException iae) {
					System.err.println("Could not create ResBitmap: " + iae.getMessage());
				} catch (IOException e) {
					System.err.println("Image could not be read or written: " + e.getMessage());
				}
			}
			
			
			return;
		}
		
		if(command.equalsIgnoreCase("bitmap")) {
			if(args.length > 1) {
				File inFile = new File(args[1]);
				File outFile = null;
				String format = null;
				if(args.length > 2) {
					outFile = new File(args[2]);
					format = Util.getFileExt(outFile.getName());
				} else {
					String ext = Util.getFileExt(inFile.getName());
					int len = 0;
					
					if(ext != null)
						len = 1 + ext.length();
					
					outFile = new File(inFile.getName().substring(0, inFile.getName().length() - len) + ".png");
					format = "png";
				}
				
				try {
					FileInputStream fis = new FileInputStream(inFile);
					ByteArrayOutputStream baos = new ByteArrayOutputStream((int)inFile.length());
					byte [] buffer = new byte [1024];
					int r;
					while ( (r = fis.read(buffer)) >= 0 ) {
						baos.write(buffer, 0, r);
					}
					
					ResBitmap rb = new ResBitmap(baos.toByteArray(), (short) 0, 0);
					
					if(!rb.hasPrivatePalette())
						rb.setPalette(Util.system_shock_palette);
					
					BufferedImage bi = rb.getImage();
					
					ImageIO.write(bi, format, outFile);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
		}
		
		if(command.equalsIgnoreCase("palimage")) {
			if(args.length>1) {
				File outFile = new File(args[1]);
				
				BufferedImage bi = new BufferedImage(128, 128, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, 256, Util.system_shock_palette, 0, false));
				DataBufferByte dbb = (DataBufferByte)bi.getRaster().getDataBuffer();
				byte [] data = dbb.getData();
				byte color=0;
				int offs;
				for(int row=0; row<128; row++) {
					offs = row * 128;
					color = (byte)((row >> 3) << 4);
					for(int col=0; col<128; col++) {
						if(col>0 && col%8 == 0)
							color++;
						data[offs + col] = color;
					}
				}
				String format = Util.getFileExt(outFile.getName());
				try {
					ImageIO.write(bi, format, outFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
}
