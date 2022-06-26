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

import java.io.File;

import javax.swing.filechooser.FileFilter;



public class JavaArrayPaletteFilter extends FileFilter
{
	@Override
	public boolean accept(File arg0) {
		if(arg0.isDirectory())
			return true;
		
		String ext = Util.getFileExt(arg0.getName());
		
		if(ext==null)
			return false;
		
		if(ext.equalsIgnoreCase("pal"))
			return true;
		
		return false;
	}

	@Override
	public String getDescription() {
		return "Palette as Java-Array";
	}
}
