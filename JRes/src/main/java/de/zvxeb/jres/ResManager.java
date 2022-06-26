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
 * Created on 02.10.2008
 */
package de.zvxeb.jres;

import java.io.File;
import java.security.InvalidParameterException;
import java.text.Collator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import de.zvxeb.jres.ResFile.DirEntry;

public class ResManager {

	private Map<String, ResFile> resFileMap;
	private ResFile last_res_file;
	
	private List<File> searchPaths;
	
	private short lowest_chunk, highest_chunk;
	
	public ResManager()
	{
		resFileMap = new TreeMap<String, ResFile>(Collator.getInstance());
		lowest_chunk = Short.MAX_VALUE;
		highest_chunk = Short.MIN_VALUE;
		last_res_file = null;
		searchPaths = new Vector<File>();
		searchPaths.add(new File("."));
	}
	
	public boolean hasResFile(File f)
	{
		return resFileMap.containsKey(f.getPath());
	}
	
	public void addSearchPath(File f)
	{
		if(f == null || !f.isDirectory())
			return;
		
		if(searchPaths.contains(f))
			return;
		
		searchPaths.add(f);
	}
	
	public File findFileInSearchPath(String filename, boolean case_sensitive) {
		for(File sp : searchPaths)
		{
			File [] listing = sp.listFiles();
			
			if(listing!=null)
			{
				for(File f : listing)
				{
					if(f.isDirectory())
						continue;
					
					if( (!case_sensitive && f.getName().equalsIgnoreCase(filename)) || f.getName().equals(filename))
					{
						return f;
					}
				}
			}
		}
		
		return null;
	}
	
	public boolean addResFileFromSearchPath(String resname, boolean case_sensitive)
	{
		if(hasResFileByName(resname, case_sensitive))
			return true;
		
		File resfile = findFileInSearchPath(resname, case_sensitive);
		
		if(resfile!=null) {
			try {
				ResFile rf = new ResFile(resfile);
				
				addResFile(rf);
				
				return true;
			} catch (Exception e) {
				// does not matter... only costs time...
			}
		}
		
		return false;
	}
	
	public boolean hasResFileByName(String name, boolean case_sensitive)
	{
		for(String fpath : resFileMap.keySet())
		{
			File ftmp = new File(fpath);
			if(case_sensitive)
			{
				if(name.equals(ftmp.getName()))
					return true;
			}
			else
			{
				if(name.equalsIgnoreCase(ftmp.getName()))
						return true;
			}
		}
		
		return false;
	}
	
	public void addResFile(ResFile rf)
	{
		resFileMap.put(rf.getFile().getPath(), rf);
		short tlcid = rf.getLowestChunkId();
		short thcid = rf.getHighestChunkId();
		
		if(lowest_chunk > tlcid)
			lowest_chunk = tlcid;
		if(highest_chunk < thcid)
			highest_chunk = thcid;
	}
	
	private void recalcChunks()
	{
		lowest_chunk = Short.MAX_VALUE;
		highest_chunk = Short.MIN_VALUE;
		
		for(ResFile rf : resFileMap.values())
		{
			short tlcid = rf.getLowestChunkId();
			short thcid = rf.getHighestChunkId();

			if(lowest_chunk > tlcid)
				lowest_chunk = tlcid;
			if(highest_chunk < thcid)
				highest_chunk = thcid;
		}
	}
	
	public void removeResFile(ResFile rf)
	{
		if(last_res_file==rf)
			last_res_file = null;
		
		resFileMap.remove(rf.getFile().getPath());
		
		recalcChunks();
	}
	
	public DirEntry getChunkEntry(int cno)
	{
		if(cno < Short.MIN_VALUE || cno > Short.MAX_VALUE)
			throw new InvalidParameterException("Chunk ID out of range: " + cno);
		
		return getChunkEntry((short)cno);
	}
	
	public DirEntry getChunkEntry(short cno)
	{
		if(cno < lowest_chunk || cno > highest_chunk)
			return null;
		
		DirEntry e = null;
		
		if(last_res_file!=null) // possible speedup
			e = last_res_file.getChunkEntry(cno);

		if(e==null)
			for(ResFile rf : resFileMap.values())
			{
				e = rf.getChunkEntry(cno);
				if(e!=null)
				{
					last_res_file = rf;
					break;
				}
			}
			
		return e;
	}
	
	public byte [] getData(short cno, int sc)
	{
		DirEntry de = getChunkEntry(cno);
		
		if(de==null)
			return null;
		
		if(sc>=0)
		{
			return de.getResFile().getSubChunkData(de, sc);
		}
		
		return de.getData();
	}
	
	public byte [] getData(short cno)
	{
		return getData(cno, -1);
	}
	
	public byte [] getData(int cno, int sc)
	{
		if(cno < Short.MIN_VALUE || cno > Short.MAX_VALUE)
			throw new InvalidParameterException("Chunk ID out of range: " + cno);

		return getData((short)cno, sc);
	}
	
	public byte [] getData(int cno)
	{
		if(cno < Short.MIN_VALUE || cno > Short.MAX_VALUE)
			throw new InvalidParameterException("Chunk ID out of range: " + cno);

		return getData((short)cno, -1);
	}
	
	public List<DirEntry> getChunkEntries(short cno)
	{
		List<DirEntry> ld = new Vector<DirEntry>();
	
		for(ResFile rf : resFileMap.values())
		{
			DirEntry e = rf.getChunkEntry(cno);
			if(e!=null)
				ld.add(e);
		}
		
		return ld;
	}
	
	private class DirEntryIterator implements Iterator<DirEntry>
	{
		private short chunk_to_get;
		
		private List<DirEntry> current_entries;
		private Iterator<DirEntry> current_iter;
		
		private DirEntryIterator()
		{
			this.chunk_to_get = lowest_chunk;
			findNextChunks();
		}
		
		private DirEntry getNextChunk()
		{
			if(!current_iter.hasNext())
				return null;

			DirEntry e = current_iter.next();

			if(!current_iter.hasNext())
				findNextChunks();
			
			return e;
		}
		
		private void findNextChunks()
		{
			while( (current_entries = getChunkEntries(chunk_to_get)).size()<1 && chunk_to_get <= highest_chunk)
				chunk_to_get++;
			
			chunk_to_get++; // prepare next
			
			current_iter = current_entries.iterator();
		}

		public boolean hasNext() {
			return current_iter.hasNext();
		}

		public DirEntry next() {
			return getNextChunk();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	public Iterator<DirEntry> iterator()
	{
		return new DirEntryIterator();
	}
	
	
}
