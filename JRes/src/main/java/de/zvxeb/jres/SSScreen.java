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
package de.zvxeb.jres;

public class SSScreen {
	/* stolen from screen.c - thanks Jim! */
	public static final int SCREEN_SPECIAL_START         = 0x00F6;
	public static final int SCREEN_SPECIAL_STATIC_SHODAN = 0x00F6;
	public static final int SCREEN_SPECIAL_STATIC        = 0x00F7;                                    
	public static final int SCREEN_SPECIAL_SURVEILLANCE  = 0x00F8;  /* F8-FF */
	public static final int SCREEN_SPECIAL_TEXT          = 0x0100;
	public static final int SCREEN_SPECIAL_SCROLL        = 0x0080;  /* text scroll flag        */
	public static final int SCREEN_SPECIAL_RANDOM_DIGIT  = 0x007;  /* random 0-9 in CPU rooms */
	
	public static final int ANIMATED_SCREEN_START = 321;
}
