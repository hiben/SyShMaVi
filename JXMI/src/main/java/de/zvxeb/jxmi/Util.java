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

public class Util {
	public static int commonPrefix(byte [] b1, byte [] b2)
	{
		int l1 = (b1!=null) ? b1.length : 0;
		int l2 = (b2!=null) ? b2.length : 0;
		
		int l = l1 >= l2 ? l1 : l2;
		int i;
		
		for(i = 0; i<l; i++)
		{
			if(b1[i]!=b2[i])
				break;
		}
		
		return i;
	}
	
	public static boolean startsWith(byte [] b, byte [] prefix)
	{
		int bl = (b!=null) ? b.length : 0;
		int pl = (prefix!=null) ? prefix.length : 0;

		if(pl<bl)
			return false;
		
		for(int i = 0; i<pl; i++)
		{
			if(b[i]!=prefix[i])
				return false;
		}
		
		return true;
	}
}
