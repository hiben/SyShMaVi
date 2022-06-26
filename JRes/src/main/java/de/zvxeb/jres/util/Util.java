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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

public class Util {
	public static byte [] system_shock_palette = {
		(byte)0x00, (byte)0x00, (byte)0x00 // index 0
		, (byte)0x00, (byte)0x00, (byte)0x07 // index 1
		, (byte)0xFF, (byte)0xFF, (byte)0xFF // index 2
		, (byte)0xFF, (byte)0x8F, (byte)0x00 // index 3
		, (byte)0xFF, (byte)0xDB, (byte)0x00 // index 4
		, (byte)0xFF, (byte)0x8F, (byte)0x00 // index 5
		, (byte)0xEF, (byte)0x3F, (byte)0x00 // index 6
		, (byte)0xD3, (byte)0x00, (byte)0x00 // index 7
		, (byte)0x00, (byte)0x33, (byte)0x33 // index 8
		, (byte)0x00, (byte)0x1B, (byte)0x1B // index 9
		, (byte)0x00, (byte)0x0F, (byte)0x0F // index 10
		, (byte)0x00, (byte)0x73, (byte)0x00 // index 11
		, (byte)0x00, (byte)0xC3, (byte)0x00 // index 12
		, (byte)0x00, (byte)0x87, (byte)0x00 // index 13
		, (byte)0x00, (byte)0x4B, (byte)0x00 // index 14
		, (byte)0x00, (byte)0x0F, (byte)0x00 // index 15
		, (byte)0x83, (byte)0x00, (byte)0xFF // index 16
		, (byte)0x3F, (byte)0x00, (byte)0x7F // index 17
		, (byte)0x00, (byte)0x00, (byte)0x00 // index 18
		, (byte)0x00, (byte)0x00, (byte)0x00 // index 19
		, (byte)0x00, (byte)0x00, (byte)0x00 // index 20
		, (byte)0x00, (byte)0x53, (byte)0x07 // index 21
		, (byte)0x00, (byte)0xA7, (byte)0x13 // index 22
		, (byte)0x00, (byte)0xFF, (byte)0x1F // index 23
		, (byte)0x8B, (byte)0x43, (byte)0x00 // index 24
		, (byte)0xFF, (byte)0x8F, (byte)0x00 // index 25
		, (byte)0x8B, (byte)0x43, (byte)0x00 // index 26
		, (byte)0x00, (byte)0x00, (byte)0x00 // index 27
		, (byte)0xFF, (byte)0x00, (byte)0x00 // index 28
		, (byte)0xA7, (byte)0x00, (byte)0x00 // index 29
		, (byte)0x53, (byte)0x00, (byte)0x00 // index 30
		, (byte)0x00, (byte)0x00, (byte)0x00 // index 31
		, (byte)0xE3, (byte)0x8B, (byte)0xFB // index 32
		, (byte)0xC7, (byte)0x5F, (byte)0xDF // index 33
		, (byte)0xAF, (byte)0x3B, (byte)0xC7 // index 34
		, (byte)0x9B, (byte)0x1F, (byte)0xAF // index 35
		, (byte)0x7B, (byte)0x17, (byte)0x93 // index 36
		, (byte)0x5F, (byte)0x0F, (byte)0x77 // index 37
		, (byte)0x47, (byte)0x0B, (byte)0x5B // index 38
		, (byte)0x2F, (byte)0x07, (byte)0x3F // index 39
		, (byte)0xFF, (byte)0xC3, (byte)0x00 // index 40
		, (byte)0xE3, (byte)0xA7, (byte)0x00 // index 41
		, (byte)0xC7, (byte)0x8F, (byte)0x00 // index 42
		, (byte)0xAF, (byte)0x7B, (byte)0x00 // index 43
		, (byte)0x93, (byte)0x63, (byte)0x00 // index 44
		, (byte)0x7B, (byte)0x4F, (byte)0x00 // index 45
		, (byte)0x5F, (byte)0x3B, (byte)0x00 // index 46
		, (byte)0x47, (byte)0x2B, (byte)0x00 // index 47
		, (byte)0xFF, (byte)0x97, (byte)0x93 // index 48
		, (byte)0xFB, (byte)0x7B, (byte)0x7B // index 49
		, (byte)0xF7, (byte)0x63, (byte)0x63 // index 50
		, (byte)0xF3, (byte)0x4B, (byte)0x4F // index 51
		, (byte)0xEF, (byte)0x37, (byte)0x3B // index 52
		, (byte)0xEB, (byte)0x23, (byte)0x2B // index 53
		, (byte)0xCB, (byte)0x17, (byte)0x1F // index 54
		, (byte)0xAF, (byte)0x13, (byte)0x17 // index 55
		, (byte)0x8F, (byte)0x0B, (byte)0x0F // index 56
		, (byte)0x73, (byte)0x07, (byte)0x07 // index 57
		, (byte)0x53, (byte)0x00, (byte)0x00 // index 58
		, (byte)0x37, (byte)0x00, (byte)0x00 // index 59
		, (byte)0x1B, (byte)0x00, (byte)0x00 // index 60
		, (byte)0xFF, (byte)0xDB, (byte)0xAF // index 61
		, (byte)0xFF, (byte)0xC3, (byte)0x83 // index 62
		, (byte)0xFF, (byte)0xAF, (byte)0x57 // index 63
		, (byte)0xFF, (byte)0x97, (byte)0x2B // index 64
		, (byte)0xFF, (byte)0x7F, (byte)0x00 // index 65
		, (byte)0xE3, (byte)0x6F, (byte)0x00 // index 66
		, (byte)0xC7, (byte)0x5F, (byte)0x00 // index 67
		, (byte)0xAB, (byte)0x4F, (byte)0x00 // index 68
		, (byte)0x93, (byte)0x3F, (byte)0x00 // index 69
		, (byte)0x77, (byte)0x33, (byte)0x00 // index 70
		, (byte)0x5B, (byte)0x27, (byte)0x00 // index 71
		, (byte)0x3F, (byte)0x17, (byte)0x00 // index 72
		, (byte)0x27, (byte)0x0F, (byte)0x00 // index 73
		, (byte)0xFF, (byte)0xFB, (byte)0x7F // index 74
		, (byte)0xEF, (byte)0xEB, (byte)0x3B // index 75
		, (byte)0xE3, (byte)0xDF, (byte)0x00 // index 76
		, (byte)0xCF, (byte)0xC3, (byte)0x00 // index 77
		, (byte)0xBB, (byte)0xAB, (byte)0x00 // index 78
		, (byte)0xAB, (byte)0x97, (byte)0x00 // index 79
		, (byte)0x97, (byte)0x7F, (byte)0x00 // index 80
		, (byte)0x87, (byte)0x6B, (byte)0x00 // index 81
		, (byte)0x73, (byte)0x5B, (byte)0x00 // index 82
		, (byte)0x5F, (byte)0x47, (byte)0x00 // index 83
		, (byte)0x4F, (byte)0x37, (byte)0x00 // index 84
		, (byte)0x3B, (byte)0x27, (byte)0x00 // index 85
		, (byte)0x2B, (byte)0x1B, (byte)0x00 // index 86
		, (byte)0xC7, (byte)0xEB, (byte)0x33 // index 87
		, (byte)0x9F, (byte)0xD3, (byte)0x2F // index 88
		, (byte)0x7B, (byte)0xBB, (byte)0x2F // index 89
		, (byte)0x5F, (byte)0xA7, (byte)0x2B // index 90
		, (byte)0x47, (byte)0x8F, (byte)0x27 // index 91
		, (byte)0x33, (byte)0x7B, (byte)0x23 // index 92
		, (byte)0x27, (byte)0x6B, (byte)0x23 // index 93
		, (byte)0x1F, (byte)0x5F, (byte)0x1F // index 94
		, (byte)0x1B, (byte)0x53, (byte)0x1F // index 95
		, (byte)0x1B, (byte)0x47, (byte)0x1B // index 96
		, (byte)0x17, (byte)0x3B, (byte)0x1B // index 97
		, (byte)0x13, (byte)0x2F, (byte)0x17 // index 98
		, (byte)0x0F, (byte)0x23, (byte)0x13 // index 99
		, (byte)0xCB, (byte)0xFF, (byte)0xFF // index 100
		, (byte)0x63, (byte)0xFF, (byte)0xFF // index 101
		, (byte)0x00, (byte)0xFB, (byte)0xFB // index 102
		, (byte)0x00, (byte)0xE3, (byte)0xE3 // index 103
		, (byte)0x00, (byte)0xCF, (byte)0xCF // index 104
		, (byte)0x00, (byte)0xBB, (byte)0xBB // index 105
		, (byte)0x00, (byte)0xA3, (byte)0xA3 // index 106
		, (byte)0x00, (byte)0x8F, (byte)0x8F // index 107
		, (byte)0x00, (byte)0x7B, (byte)0x7B // index 108
		, (byte)0x00, (byte)0x67, (byte)0x67 // index 109
		, (byte)0x00, (byte)0x4F, (byte)0x4F // index 110
		, (byte)0x00, (byte)0x3B, (byte)0x3B // index 111
		, (byte)0x00, (byte)0x27, (byte)0x27 // index 112
		, (byte)0xDB, (byte)0xDB, (byte)0xFF // index 113
		, (byte)0xB7, (byte)0xB7, (byte)0xF7 // index 114
		, (byte)0x93, (byte)0x93, (byte)0xF3 // index 115
		, (byte)0x73, (byte)0x73, (byte)0xEF // index 116
		, (byte)0x53, (byte)0x53, (byte)0xEB // index 117
		, (byte)0x37, (byte)0x37, (byte)0xE7 // index 118
		, (byte)0x1B, (byte)0x1B, (byte)0xE3 // index 119
		, (byte)0x13, (byte)0x13, (byte)0xC7 // index 120
		, (byte)0x0B, (byte)0x0B, (byte)0xAB // index 121
		, (byte)0x07, (byte)0x07, (byte)0x8F // index 122
		, (byte)0x07, (byte)0x07, (byte)0x73 // index 123
		, (byte)0x00, (byte)0x00, (byte)0x57 // index 124
		, (byte)0x00, (byte)0x00, (byte)0x3B // index 125
		, (byte)0x00, (byte)0x00, (byte)0x1F // index 126
		, (byte)0xFB, (byte)0xAF, (byte)0x8B // index 127
		, (byte)0xEF, (byte)0x9F, (byte)0x7B // index 128
		, (byte)0xE3, (byte)0x93, (byte)0x6F // index 129
		, (byte)0xDB, (byte)0x87, (byte)0x63 // index 130
		, (byte)0xCF, (byte)0x7B, (byte)0x57 // index 131
		, (byte)0xC7, (byte)0x6F, (byte)0x4B // index 132
		, (byte)0xBB, (byte)0x63, (byte)0x3F // index 133
		, (byte)0xB3, (byte)0x5B, (byte)0x37 // index 134
		, (byte)0x9F, (byte)0x4B, (byte)0x33 // index 135
		, (byte)0x8B, (byte)0x43, (byte)0x2F // index 136
		, (byte)0x77, (byte)0x37, (byte)0x2B // index 137
		, (byte)0x67, (byte)0x2B, (byte)0x23 // index 138
		, (byte)0x53, (byte)0x23, (byte)0x1F // index 139
		, (byte)0x3F, (byte)0x1B, (byte)0x17 // index 140
		, (byte)0x2F, (byte)0x13, (byte)0x13 // index 141
		, (byte)0xFF, (byte)0xD3, (byte)0x9B // index 142
		, (byte)0xEB, (byte)0xBF, (byte)0x83 // index 143
		, (byte)0xDB, (byte)0xAB, (byte)0x6B // index 144
		, (byte)0xCB, (byte)0x97, (byte)0x57 // index 145
		, (byte)0xBB, (byte)0x83, (byte)0x47 // index 146
		, (byte)0xA7, (byte)0x73, (byte)0x37 // index 147
		, (byte)0x97, (byte)0x63, (byte)0x27 // index 148
		, (byte)0x87, (byte)0x53, (byte)0x1B // index 149
		, (byte)0x77, (byte)0x47, (byte)0x13 // index 150
		, (byte)0x6B, (byte)0x3F, (byte)0x0F // index 151
		, (byte)0x5F, (byte)0x37, (byte)0x0B // index 152
		, (byte)0x53, (byte)0x2F, (byte)0x07 // index 153
		, (byte)0x47, (byte)0x27, (byte)0x00 // index 154
		, (byte)0x3B, (byte)0x1F, (byte)0x00 // index 155
		, (byte)0x2F, (byte)0x17, (byte)0x00 // index 156
		, (byte)0x27, (byte)0x13, (byte)0x00 // index 157
		, (byte)0xDB, (byte)0xEB, (byte)0xDB // index 158
		, (byte)0xBB, (byte)0xD3, (byte)0xBB // index 159
		, (byte)0x9B, (byte)0xBB, (byte)0x9B // index 160
		, (byte)0x7F, (byte)0xA3, (byte)0x7F // index 161
		, (byte)0x67, (byte)0x8B, (byte)0x67 // index 162
		, (byte)0x4F, (byte)0x73, (byte)0x4F // index 163
		, (byte)0x3B, (byte)0x5B, (byte)0x3B // index 164
		, (byte)0x27, (byte)0x43, (byte)0x27 // index 165
		, (byte)0x17, (byte)0x2F, (byte)0x17 // index 166
		, (byte)0x0B, (byte)0x17, (byte)0x0B // index 167
		, (byte)0xEB, (byte)0xB7, (byte)0xAB // index 168
		, (byte)0xCF, (byte)0x8F, (byte)0x83 // index 169
		, (byte)0xB7, (byte)0x73, (byte)0x5F // index 170
		, (byte)0x9F, (byte)0x57, (byte)0x43 // index 171
		, (byte)0x87, (byte)0x3F, (byte)0x27 // index 172
		, (byte)0x6F, (byte)0x2B, (byte)0x13 // index 173
		, (byte)0x57, (byte)0x1B, (byte)0x07 // index 174
		, (byte)0x3F, (byte)0x0F, (byte)0x00 // index 175
		, (byte)0xEB, (byte)0xEB, (byte)0xF7 // index 176
		, (byte)0xD7, (byte)0xD7, (byte)0xE7 // index 177
		, (byte)0xC3, (byte)0xC3, (byte)0xDB // index 178
		, (byte)0xB3, (byte)0xB3, (byte)0xCB // index 179
		, (byte)0x9F, (byte)0x9F, (byte)0xBB // index 180
		, (byte)0x8F, (byte)0x8F, (byte)0xAF // index 181
		, (byte)0x7F, (byte)0x7F, (byte)0x9F // index 182
		, (byte)0x6F, (byte)0x6F, (byte)0x93 // index 183
		, (byte)0x63, (byte)0x63, (byte)0x83 // index 184
		, (byte)0x53, (byte)0x53, (byte)0x77 // index 185
		, (byte)0x47, (byte)0x47, (byte)0x67 // index 186
		, (byte)0x3B, (byte)0x3B, (byte)0x5B // index 187
		, (byte)0x2F, (byte)0x2F, (byte)0x4B // index 188
		, (byte)0x23, (byte)0x23, (byte)0x3F // index 189
		, (byte)0x1B, (byte)0x1B, (byte)0x2F // index 190
		, (byte)0x13, (byte)0x13, (byte)0x23 // index 191
		, (byte)0xF7, (byte)0xDF, (byte)0xCB // index 192
		, (byte)0xE7, (byte)0xCB, (byte)0xBB // index 193
		, (byte)0xD7, (byte)0xBB, (byte)0xAB // index 194
		, (byte)0xC7, (byte)0xAB, (byte)0x9B // index 195
		, (byte)0xB7, (byte)0x9B, (byte)0x8B // index 196
		, (byte)0xAB, (byte)0x8B, (byte)0x7F // index 197
		, (byte)0x9B, (byte)0x7B, (byte)0x6F // index 198
		, (byte)0x8B, (byte)0x6F, (byte)0x63 // index 199
		, (byte)0x7B, (byte)0x5F, (byte)0x57 // index 200
		, (byte)0x6B, (byte)0x4F, (byte)0x4B // index 201
		, (byte)0x5F, (byte)0x43, (byte)0x3F // index 202
		, (byte)0x4F, (byte)0x37, (byte)0x33 // index 203
		, (byte)0x3F, (byte)0x2B, (byte)0x27 // index 204
		, (byte)0x2F, (byte)0x1F, (byte)0x1F // index 205
		, (byte)0x1F, (byte)0x13, (byte)0x13 // index 206
		, (byte)0x13, (byte)0x0B, (byte)0x0B // index 207
		, (byte)0xEF, (byte)0xEF, (byte)0xEF // index 208
		, (byte)0xDF, (byte)0xDF, (byte)0xDF // index 209
		, (byte)0xCF, (byte)0xCF, (byte)0xCF // index 210
		, (byte)0xBF, (byte)0xBF, (byte)0xBF // index 211
		, (byte)0xAF, (byte)0xAF, (byte)0xAF // index 212
		, (byte)0xA3, (byte)0xA3, (byte)0xA3 // index 213
		, (byte)0x93, (byte)0x93, (byte)0x93 // index 214
		, (byte)0x83, (byte)0x83, (byte)0x83 // index 215
		, (byte)0x73, (byte)0x73, (byte)0x73 // index 216
		, (byte)0x63, (byte)0x63, (byte)0x63 // index 217
		, (byte)0x57, (byte)0x57, (byte)0x57 // index 218
		, (byte)0x47, (byte)0x47, (byte)0x47 // index 219
		, (byte)0x37, (byte)0x37, (byte)0x37 // index 220
		, (byte)0x27, (byte)0x27, (byte)0x27 // index 221
		, (byte)0x17, (byte)0x17, (byte)0x17 // index 222
		, (byte)0x0B, (byte)0x0B, (byte)0x0B // index 223
		, (byte)0x76, (byte)0x76, (byte)0xC2 // index 224
		, (byte)0x4A, (byte)0x62, (byte)0x16 // index 225
		, (byte)0x77, (byte)0x75, (byte)0x3B // index 226
		, (byte)0xC2, (byte)0x3C, (byte)0x3F // index 227
		, (byte)0x42, (byte)0x42, (byte)0xBC // index 228
		, (byte)0x21, (byte)0x55, (byte)0x55 // index 229
		, (byte)0xB0, (byte)0xDD, (byte)0xDD // index 230
		, (byte)0xCF, (byte)0xCC, (byte)0x33 // index 231
		, (byte)0x79, (byte)0x4A, (byte)0x86 // index 232
		, (byte)0x2C, (byte)0x2C, (byte)0xB9 // index 233
		, (byte)0x4D, (byte)0x4D, (byte)0x9F // index 234
		, (byte)0xD6, (byte)0x56, (byte)0x56 // index 235
		, (byte)0x21, (byte)0x21, (byte)0x8B // index 236
		, (byte)0x95, (byte)0xBB, (byte)0xBB // index 237
		, (byte)0x8F, (byte)0x21, (byte)0x23 // index 238
		, (byte)0x92, (byte)0xAC, (byte)0x25 // index 239
		, (byte)0x16, (byte)0x16, (byte)0x5C // index 240
		, (byte)0x4F, (byte)0xCC, (byte)0xCC // index 241
		, (byte)0x42, (byte)0xAA, (byte)0xAA // index 242
		, (byte)0x37, (byte)0x37, (byte)0x9D // index 243
		, (byte)0xA6, (byte)0x66, (byte)0xB8 // index 244
		, (byte)0x35, (byte)0x88, (byte)0x88 // index 245
		, (byte)0x27, (byte)0x27, (byte)0x6E // index 246
		, (byte)0xAF, (byte)0xAC, (byte)0x2B // index 247
		, (byte)0x92, (byte)0x46, (byte)0xA4 // index 248
		, (byte)0x6A, (byte)0x7D, (byte)0x1B // index 249
		, (byte)0xCC, (byte)0x79, (byte)0x22 // index 250
		, (byte)0x99, (byte)0x97, (byte)0x4C // index 251
		, (byte)0x8F, (byte)0x8D, (byte)0x23 // index 252
		, (byte)0x5D, (byte)0x2C, (byte)0x68 // index 253
		, (byte)0x5C, (byte)0x5C, (byte)0xBF // index 254
		, (byte)0xBB, (byte)0xB8, (byte)0x5D // index 255
	};

	public static byte [] GPLHeader =
	{
		'G', 'I', 'M', 'P', ' ', 'P', 'a', 'l', 'e', 't', 't', 'e', '\n'
	};
	
	public static byte [] GPLName =
	{
		'N', 'a', 'm', 'e', ':', ' '
	};
	
	public static byte newLine = '\n';
	
	public static byte [] GPLMark = { '#', '\n' };
	
	public static String GPLLineFormat = "%3d %3d %3d\tUntitled\n";
	
	public static String getFileExt(String filename)
	{
		int i = filename.lastIndexOf('.');
		
		if(i==-1)
			return null;
		
		// explicit empty extension 'blah.'
		if(i==filename.length()-1)
			return "";
		
		String ext = filename.substring(i+1);
		
		// could this be ? 'blah./'
		if(ext.indexOf(File.pathSeparator)!=-1)
			return null;
		
		return ext;
	}
	
	public static void writeGIMPPalette(OutputStream os, String name, byte [] palette) throws IOException
	{
		assert(os!=null);
		assert(name!=null);
		assert(palette!=null);
		assert(palette.length==768);
	
		os.write(GPLHeader);

		os.write(GPLName);
		os.write(name.getBytes());
		os.write(newLine);
		
		os.write(GPLMark);
		
		for(int c=0; c<768; c+=3)
			os.write(String.format(GPLLineFormat, ((int)palette[c])&0xFF, ((int)palette[c+1])&0xFF, ((int)palette[c+2])&0xFF).getBytes());
	}
	
	public static void writeJavaPaletteArray(OutputStream os, String name, byte [] palette) throws IOException
	{
		assert(os!=null);
		assert(name!=null);
		assert(palette!=null);
		assert(palette.length==768);
		
		os.write("byte [] ".getBytes());
		os.write(name.getBytes());
		os.write(" = {\n".getBytes()); 
		for(int i=0; i<256; i++) {
			os.write
			(
				String.format
				(
					  "\t%s(byte)0x%02X, (byte)0x%02X, (byte)0x%02X // index %d\n"
					, i==0 ? "  " : ", ", palette[i*3], palette[i*3+1], palette[i*3+2], i
				).getBytes()
			);
		}
		os.write("};\n".getBytes());
	}
	
	public static byte [] readFileFully(File f) {
		byte [] buffer = new byte [1024];
		try {
			FileInputStream fis = new FileInputStream(f);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			while(fis.available()>0) {
				int read = fis.read(buffer);
				baos.write(buffer, 0, read);
			}
			
			fis.close();
			
			return baos.toByteArray();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
		
		return null;
	}
	
	public static void writeLE32(OutputStream os, int v) throws IOException {
		os.write( (v & 0xFF) );
		os.write( (v>>8) & 0xFF );
		os.write( (v>>16) & 0xFF );
		os.write( (v>>24) & 0xFF );
	}
	public static void writeLE24(OutputStream os, int v) throws IOException {
		os.write( (v & 0xFF) );
		os.write( (v>>8) & 0xFF );
		os.write( (v>>16) & 0xFF );
	}
	public static void writeLE16(OutputStream os, int v) throws IOException {
		os.write( (v & 0xFF) );
		os.write( (v>>8) & 0xFF );
	}
}
