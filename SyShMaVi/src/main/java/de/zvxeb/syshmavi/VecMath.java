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
 * Created on 20.01.2009
 */
package de.zvxeb.syshmavi;

public class VecMath {
	public static final int IDX_X = 0;
	public static final int IDX_Y = 1;
	public static final int IDX_Z = 2;
	
	public static final int IDX_11 = 0;
	public static final int IDX_12 = 1;
	public static final int IDX_13 = 2;

	public static final int IDX_21 = 3+0;
	public static final int IDX_22 = 3+1;
	public static final int IDX_23 = 3+2;

	public static final int IDX_31 = 6+0;
	public static final int IDX_32 = 6+1;
	public static final int IDX_33 = 6+2;
	
	public static double [] mkVec(double a, double b, double c, double [] r)
	{
		if(r==null)
			r = new double [3];
		
		r[0] = a;
		r[1] = b;
		r[2] = c;

		return r;
	}
	
	public static double [] vecSub(double [] v1, double [] v2, double [] r)
	{
		if(r==null)
			r = new double [3];

		r[0] = v1[0] - v2[0]; 
		r[1] = v1[1] - v2[1]; 
		r[2] = v1[2] - v2[2]; 

		return r;
	}

	public static double [] vecAdd(double [] v1, double [] v2, double [] r)
	{
		if(r==null)
			r = new double [3];

		r[0] = v1[0] + v2[0]; 
		r[1] = v1[1] + v2[1]; 
		r[2] = v1[2] + v2[2]; 

		return r;
	}
	
	public static double [] vecMul(double [] v, double s, double [] r)
	{
		if(r==null)
			r = new double [3];

		r[0] = v[0] * s; 
		r[1] = v[1] * s;  
		r[2] = v[2] * s; 

		return r;
	}

	public static double [] vecDiv(double [] v, double s, double [] r)
	{
		if(r==null)
			r = new double [3];

		r[0] = v[0] / s; 
		r[1] = v[1] / s;  
		r[2] = v[2] / s; 

		return r;
	}
	
	public static double [] crossProduct(double [] v1, double [] v2, double [] r)
	{
		if(r==null)
			r = new double [3];
		
		double t0 = v1[1] * v2[2] - v1[2] * v2[1];
		double t1 = v1[2] * v2[0] - v1[0] * v2[2];
		double t2 = v1[0] * v2[1] - v1[1] * v2[0];
		
		r[0] = t0;
		r[1] = t1;
		r[2] = t2;
		
		return r;
	}
	
	public static double innerProduct(double [] v1, double [] v2)
	{
		return v1[0] * v2[0] + v1[1] * v2[1] +  v1[2] * v2[2]; 
	}
	
	public static double vecLenSq(double [] v)
	{
		return v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
	}

	public static double vecLen(double [] v)
	{
		return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}
	
	public static final double verySmall = 0.00001;
	
	public static boolean verySmallValue(double v) {
		return (v < 0.0)?(-v < verySmall):(v < verySmall);
	}

	public static double [] normalize(double [] v, double [] r)
	{
		if(r==null)
			r = new double [3];
		
		double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		
		if(l < verySmall)
		{
			r[0] = v[0];
			r[1] = v[1];
			r[2] = v[2];
		}
		else
		{
			r[0] = v[0] / l;
			r[1] = v[1] / l;
			r[2] = v[2] / l;
		}
		return r;
	}
	
	/**
	 * Component of <em>u</em> along <em>a</em>
	 * @param u vector to decompose
	 * @param a vector to decompose along
	 * @return |proj_a(u)|, the component of <em>u</em> along <em>a</em> 
	 */
	public static double component(double [] u, double [] a) {
		//               |u . a|
		// |proj_a(u)| = -------
		//                 |a|
		return Math.abs(innerProduct(u, a)) / vecLen(a);
	}
	
	/**
	 * Projection of <em>u</em> along <em>a</em>
	 * @param a
	 * @param u
	 * @param r
	 * @return
	 */
	public static double [] proj(double [] a, double [] u, double [] r) {
		//             u . a
		// proj_a(u) = -- -- * a
		//             |a]^2
		return vecMul(a, innerProduct(u, a) / vecLenSq(a), r);
	}
	
	/**
	 * Multiply a point with a matrix (e.g. rotate with rotation matrix)
	 * @param p point with x,y,z coordinates
	 * @param m 3x3 matrix
	 * @param r point to store result or <em>null</em> to create point
	 * @return p*m
	 */
	public static double [] pMulMat(double [] p, double [] m, double [] r) {
		if(r == null) {
			r = new double [3];
		}
		
		double rx, ry, rz;
		
		rx =
			  p[IDX_X] * m[IDX_11]
			+ p[IDX_Y] * m[IDX_12] 
			+ p[IDX_Z] * m[IDX_13]
			;
		
		ry =
			  p[IDX_X] * m[IDX_21]
			+ p[IDX_Y] * m[IDX_22] 
			+ p[IDX_Z] * m[IDX_23]
			;
		
		rz =
			  p[IDX_X] * m[IDX_31]
			+ p[IDX_Y] * m[IDX_32] 
			+ p[IDX_Z] * m[IDX_33]
			;
		
		r[IDX_X] = rx;
		r[IDX_Y] = ry;
		r[IDX_Z] = rz;
		
		return r;		
	}
	
	/**
	 * Rotate point around x-axis
	 * @param p point to rotate
	 * @param theta angle in radians
	 * @param r point to store result or <em>null</em> to create point
	 * @return
	 */
	public static double [] rotateX(double [] p, double theta, double [] r) {
		double s_t = Math.sin(theta);
		double c_t = Math.cos(theta);

		double [] rmatrix =
		{
			   1.0,  0.0,  0.0
			,  0.0,  c_t, -s_t
			,  0.0,  s_t,  c_t
		};
		
		return pMulMat(p, rmatrix, r);		
	}
	
	/**
	 * Rotate point around y-axis
	 * @param p point to rotate
	 * @param theta angle in radians
	 * @param r point to store result or <em>null</em> to create point
	 * @return
	 */
	public static double [] rotateY(double [] p, double theta, double [] r) {
		double s_t = Math.sin(theta);
		double c_t = Math.cos(theta);

		double [] rmatrix =
		{
			   c_t,  0.0,  s_t
			,  0.0,  1.0,  0.0
			, -s_t,  0.0,  c_t
		};
		
		return pMulMat(p, rmatrix, r);		
	}
	
	/**
	 * Rotate point around z-axis
	 * @param p point to rotate
	 * @param theta angle in radians
	 * @param r point to store result or <em>null</em> to create point
	 * @return
	 */
	public static double [] rotateZ(double [] p, double theta, double [] r) {
		double s_t = Math.sin(theta);
		double c_t = Math.cos(theta);

		double [] rmatrix =
		{
			   c_t, -s_t,  0.0
			,  s_t,  c_t,  0.0
			,  0.0,  0.0,  1.0
		};
		
		return pMulMat(p, rmatrix, r);		
	}
	
	public static boolean point_in_rect
	(
		  double r_x1, double r_y1
		, double r_x2, double r_y2
		, double p_x, double p_y
	) {
		double xmax = (r_x1 > r_x2)?r_x1:r_x2;
		double xmin = (r_x1 < r_x2)?r_x1:r_x2;
		double ymax = (r_y1 > r_y2)?r_y1:r_y2;
		double ymin = (r_y1 < r_y2)?r_y1:r_y2;
		return (p_x >= xmin && p_x <= xmax && p_y >= ymin && p_y <= ymax);
	}
	
	public static boolean point_is_visible
	(
		  double view_x, double view_y
		, double block_x1, double block_y1
		, double block_x2, double block_y2
		, double p_x, double p_y		
	) {
		double s, spy, spx;

		double bxmax = (block_x1 > block_x2)?block_x1:block_x2;
		double bxmin = (block_x1 < block_x2)?block_x1:block_x2;
		double bymax = (block_y1 > block_y2)?block_y1:block_y2;
		double bymin = (block_y1 < block_y2)?block_y1:block_y2;
		
		double dx = p_x - view_x;
		double dy = p_y - view_y;
		
		boolean small_dx = verySmallValue(dx);
		boolean small_dy = verySmallValue(dy);
		
		if(small_dx && small_dy) {
			return true;
		}
		
		if(!small_dy) {
			s = (bymin - view_y) / dy;
			spx = view_x + s * dx;
			spy = view_y + s * dy;
			
			if(s >= 0.0 && spx >= bxmin && spx <= bxmax) {
				return false;
			}

			s = (bymax - view_y) / dy;
			spx = view_x + s * dx;
			spy = view_y + s * dy;

			if(s >= 0.0 && spx >= bxmin && spx <= bxmax) {
				return false;
			}
		}
		
		if(!small_dx) {
			s = (bxmin - view_x) / dx;
			spx = view_x + s * dx;
			spy = view_y + s * dy;

			if(s >= 0.0 && spy >= bymin && spy <= bymax) {
				return false;
			}

			s = (bxmax - view_x) / dx;
			spx = view_x + s * dx;
			spy = view_y + s * dy;

			if(s >= 0.0 && spy >= bymin && spy <= bymax) {
				return false;
			}
		}
		
		return true;
	}
	
	public static void printTest(int vx, int vy, boolean [] [] testa) {
		for(int y = 0; y<16; y++) {
			for(int x=0; x<16; x++) {
				if(vx == x && vy == y)
					System.out.print('V');
				else
					System.out.print((testa[y][x]?'#':'0'));
			}
			System.out.println();
		}
	}

	public static void printVis(int vx, int vy, boolean [] [] area, boolean [] [] vis) {
		for(int y = 0; y<16; y++) {
			for(int x=0; x<16; x++) {
				if(vx == x && vy == y)
					System.out.print('V');
				else
				{
					char mark = '0';
					
					if(area[y][x] && vis[y][x])
						mark = '#';

					if(area[y][x] && !vis[y][x])
						mark = '=';
					
					System.out.print(mark);
				}
			}
			System.out.println();
		}
	}
	
	public static void main(String...args) {
		boolean [] [] testa = new boolean [16] [16];
		boolean [] [] testv = new boolean [16] [16];

		for(int i=0; i<4; i++) {
			for(int j=0; j<4; j++) {
				testa [6+i] [6+j] = true;
			}
		}

		for(int i=0; i<2; i++) {
			for(int j=0; j<2; j++) {
				testa [2+i] [2+j] = true;
			}
		}

		for(int i=0; i<2; i++) {
			for(int j=0; j<2; j++) {
				testa [2+i] [7+j] = true;
			}
		}
		
		int vx= 2;
		int vy = 14;
		
		printTest(vx, vy, testa);

		for(int i=0; i<16; i++) {
			for(int j=0; j<16; j++) {
				testv[i][j] = point_is_visible(vx, vy, 6, 6, 10, 10, j, i);
			}
		}

		printVis(vx, vy, testa, testv);
	}
}
