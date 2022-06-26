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

import static de.zvxeb.jres.util.VecMath.crossProduct;
import static de.zvxeb.jres.util.VecMath.innerProduct;
import static de.zvxeb.jres.util.VecMath.mkVec;
import static de.zvxeb.jres.util.VecMath.normalize;
import static de.zvxeb.jres.util.VecMath.vecDiv;
import static de.zvxeb.jres.util.VecMath.vecMul;
import static de.zvxeb.jres.util.VecMath.vecSub;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.Vector;

public class SSModel {
	public static final boolean debug = false;
	
	public static final int MODEL_CHUNK_BASE = 2300;
	public static final int MODEL_TEXTURE_CHUNK_BASE = 475;
	public static final int MODEL_OBJECT_TEXTURE_CHUNK_BASE = 2180;
	public static final int MODEL_OBJECT_TEXTURE_CHUNK_MAX = 2194;
	public static final float VT_THRESHOLD = 32768.0f;
	public static final float VT_VALUE = 40000.0f;
	
	public static final int
		M3F_FLAT 			= 0x0001,
		M3F_TEXTURED 		= 0x0002,
		M3F_WALL_TEXTURE	= 0x0004,
		M3F_OBJ_TEXTURE 	= 0x0008,
		M3F_SHADED 			= 0x0010,
		M3F_AUX_SHADE 		= 0x0020;
	
	public static String flagName(int flag) {
		switch(flag) {
		case M3F_FLAT: return "Flat Shaded";
		case M3F_TEXTURED: return "Textured";
		case M3F_WALL_TEXTURE: return "Wall-Texture";
		case M3F_OBJ_TEXTURE: return "Object-Texture";
		case M3F_SHADED: return "Shaded";
		case M3F_AUX_SHADE: return "Aux. Shade";
		default:
			return "Unknown Flag (" + flag + ")";
		}
	}
	
	public static String decodeFlags(int flags) {
		StringBuilder sb = new StringBuilder();
		for(int f : new int [] {M3F_FLAT, M3F_TEXTURED, M3F_WALL_TEXTURE, M3F_OBJ_TEXTURE, M3F_SHADED, M3F_AUX_SHADE}) {
			if( (flags & f) == f) {
				if(sb.length()>0)
					sb.append(", ");
				sb.append(flagName(f));
			}
		}
		
		if(sb.length()==0)
			sb.append("<no flags set>");
		
		return sb.toString();
	}
		
	
	public static final short
		M3_IGNORE 			= 0x0000,
		M3_FACE 			= 0x0001,
		M3_VERTICES 		= 0x0003,
		M3_FLAT_FACE		= 0x0004,
		M3_COLOR 			= 0x0005,
		M3_SORT_PLANE 		= 0x0006,
		M3_VERTEX_OFFSET_X 	= 0x000A,
		M3_VERTEX_OFFSET_Y 	= 0x000B,
		M3_VERTEX_OFFSET_Z 	= 0x000C,
		M3_VERTEX_OFFSET_XY	= 0x000D,
		M3_VERTEX_OFFSET_XZ	= 0x000E,
		M3_VERTEX_OFFSET_YZ = 0x000F,
		M3_VERTEX			= 0x0015,
		M3_SHADE			= 0x001C,
		M3_TEXTURE_MAPPING	= 0x0025,
		M3_TEXTURE			= 0x0026;
	
	public static final int
		M3_VAR_HEIGHT 	= 0x0001,
		M3_AUXPAL 		= 0x0002;
		
	
	// the magical unknown header
	private byte [] header = new byte [8];
	private List<M3Vector> vertices = new Vector<M3Vector>();
	private Node root = null;
	private int flags = 0;
	private int numNodes = 1;
	
	private M3Vector minCoord = new M3Vector(0,0,0);
	private M3Vector maxCoord = new M3Vector(0,0,0);
	
	private List<Integer> usedTextures = new Vector<Integer>();
	
	private boolean usesObjectTexture = false;
	
	public byte [] getHeader() {
		return header;
	}
	
	public List<M3Vector> getVertices() {
		return vertices;
	}
	
	public Node getRoot() {
		return root;
	}
	
	public int getFlags() {
		return flags;
	}
	
	public int getNumberOfNodes() {
		return numNodes;
	}
	
	public M3Vector getMinCoord() {
		return minCoord;
	}

	public M3Vector getMaxCoord() {
		return maxCoord;
	}
	
	public List<Integer> getUsedTextures() {
		return usedTextures;
	}
	
	public boolean usesObjectTexture() {
		return usesObjectTexture;
	}
	
	public boolean hasFlag(int flag) {
		return (flags & flag) == flag;
	}
	
	// prohibit user-instances
	private SSModel() { }

	public static Node parseNode(SSModel m, ByteBuffer bb) {
		return parseNode(m, bb, 0);
	}
	
	private void checkMinMaxBounds(M3Vector v) {
		if(v.x > maxCoord.x)
			maxCoord.x = v.x;
		
		if(v.y > maxCoord.y)
			maxCoord.y = v.y;
		
		if(v.z > maxCoord.z)
			maxCoord.z = v.z;
		
		if(v.x < minCoord.x)
			minCoord.x = v.x;
		
		if(v.y < minCoord.y)
			minCoord.y = v.y;
		
		if(v.z < minCoord.z)
			minCoord.z = v.z;
	}
	
	private void addVertex(M3Vector v) {
		checkMinMaxBounds(v);
		vertices.add(v);
	}

	public static PrintStream model_output = null;
	private static int node_level = 0; 
	
	public static Node parseNode(SSModel m, ByteBuffer bb, int dc) {
		Node n = new Node();
		int command = 0, index;
		boolean inFace = false;
		int faceEndPosition = -1;
		Surface currentSurface = null;
		int leftNodePos = 0;
		int rightNodePos = 0;
		M3Vector offsV, newV;
		float offset;
		float offset2;
		
		String debugSpace = "";
		if(debug) {
			for(int i=0; i<dc; i++)
				debugSpace += ">";
		}

		if(model_output!=null) {
			model_output.printf("# Node Level %d\n", node_level);
		}
		
		do {
			int numVertices = 0;
			int numTC = 0;
			
			command = bb.getShort();
			
			if(model_output!=null) {
				model_output.printf("# processing command %d\n", command);
			}
			
			if(command==0) {
				break;
			}

			switch(command) {
			case M3_IGNORE:
				debug(debugSpace + "Ignore");
				if(model_output!=null) {
					model_output.printf("IGNORE\n");
				}
				break;
			case M3_FACE:
				if(inFace) {
					debug("%soldFaceEnd %d, now at %d", debugSpace, faceEndPosition, bb.position());
				}
				inFace = true;
				int curPos = bb.position();
				// this is the position of the next command after face data
				// (minus 2 because we already read the command)
				// (note: Sort-plane definitions come after face data)
				faceEndPosition = curPos + readUnsignedShort(bb) - 2;
				currentSurface = new Surface();
				currentSurface.normal = readM3Vector(bb);
				currentSurface.point = readM3Vector(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "FACE\n# Normal\n%.3f %.3f %.3f\n# Point\n%.3f %.3f %.3f\n", currentSurface.normal.x, currentSurface.normal.y, currentSurface.normal.z, currentSurface.point.x, currentSurface.point.y, currentSurface.point.z);
				}
				break;
			case M3_VERTICES:
				numVertices = readUnsignedShort(bb);
				skip(bb,2);
				if(model_output!=null) {
					model_output.printf(Locale.US, "VERTICES\n# Points\n%d\n", numVertices);
				}
				for(int i=0; i<numVertices; i++) {
					m.addVertex(readM3Vector(bb));
					if(model_output!=null) {
						M3Vector m3v = m.getVertices().get(m.getVertices().size()-1);
						model_output.printf(Locale.US, "%.3f %.3f %.3f # Index %d\n", m3v.x, m3v.y, m3v.z, m.getVertices().size()-1);
					}
				}
				break;
			case M3_FLAT_FACE:
				if(!inFace) {
					throw new InvalidModelDataException("Face vertices set outside face definition!");
				}
				currentSurface.flags |= M3F_FLAT;
				numVertices = readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "FLAT_FACE\n# Points\n%d\n", numVertices);
				}
				currentSurface.getVertexIndices().clear();
				for(int i=0; i<numVertices; i++) {
					currentSurface.vertexIndices.add(readUnsignedShort(bb));
					if(model_output!=null) {
						model_output.printf(Locale.US, "%d ", currentSurface.vertexIndices.get(currentSurface.vertexIndices.size()-1));
					}
				}
				if(model_output!=null) {
					model_output.printf(Locale.US, "\n");
				}
				n.surfaces.add(new Surface(currentSurface));
				break;
			case M3_COLOR:
				if(!inFace) {
					throw new InvalidModelDataException("Face color set outside face definition!");
				}
				currentSurface.color = readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "COLOUR\n%d\n", currentSurface.color);
				}
				break;
			case M3_SORT_PLANE:
				int planeDefPos = bb.position()-2;
				n.sortPlaneNormal = readM3Vector(bb);
				n.sortPlanePoint = readM3Vector(bb);
				leftNodePos = planeDefPos + readUnsignedShort(bb);
				rightNodePos = planeDefPos + readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "SORT_PLANE\n# Normal\n%.3f %.3f %.3f\n# Point\n%.3f %.3f %.3f\n", n.sortPlaneNormal.x, n.sortPlaneNormal.y, n.sortPlaneNormal.z, n.sortPlanePoint.x, n.sortPlanePoint.y, n.sortPlanePoint.z);
				}
				break;
			case M3_VERTEX_OFFSET_X:
			case M3_VERTEX_OFFSET_Y:
			case M3_VERTEX_OFFSET_Z:
				skip(bb,2);
				index = readUnsignedShort(bb);
				offsV = m.vertices.get(index);
				offset = readM3Float(bb);
				newV = new M3Vector(offsV);
				newV.x += (command==M3_VERTEX_OFFSET_X)?offset:0.0;
				newV.y += (command==M3_VERTEX_OFFSET_Y)?offset:0.0;
				newV.z += (command==M3_VERTEX_OFFSET_Z)?offset:0.0;
				m.addVertex(newV);
				if(model_output!=null) {
					model_output.printf(Locale.US, "VERTEX_OFFSET_%s\n%d %.3f # (%.3f %.3f %.3f) -> (%.3f %.3f %.3f) Index %d\n",
							(command==M3_VERTEX_OFFSET_X)?"X":(command==M3_VERTEX_OFFSET_Y)?"Y":"Z"
							, index
							, offset
							, offsV.x, offsV.y, offsV.z
							, newV.x, newV.y, newV.z
							, m.getVertices().size()-1
							);
				}
				break;
			case M3_VERTEX_OFFSET_XY:
			case M3_VERTEX_OFFSET_XZ:
			case M3_VERTEX_OFFSET_YZ:
				skip(bb,2);
				index = readUnsignedShort(bb);
				offsV = m.vertices.get(index);
				newV = new M3Vector(offsV);
				offset = readM3Float(bb);
				newV.x += (command==M3_VERTEX_OFFSET_XY || command==M3_VERTEX_OFFSET_XZ)?offset:0.0;
				newV.y += (command==M3_VERTEX_OFFSET_YZ)?offset:0.0;
				offset2 = readM3Float(bb);
				newV.y += (command==M3_VERTEX_OFFSET_XY)?offset2:0.0;
				newV.z += (command==M3_VERTEX_OFFSET_XZ || command==M3_VERTEX_OFFSET_YZ)?offset2:0.0;
				m.addVertex(newV);
				if(model_output!=null) {
					model_output.printf(Locale.US, "VERTEX_OFFSET_%s\n%d %.3f %.3f # (%.3f %.3f %.3f) -> (%.3f %.3f %.3f) Index %d\n",
							(command==M3_VERTEX_OFFSET_XY)?"XY":(command==M3_VERTEX_OFFSET_XZ)?"XZ":"YZ"
							, index
							, offset
							, offset2
							, offsV.x, offsV.y, offsV.z
							, newV.x, newV.y, newV.z
							, m.getVertices().size()-1
							);
				}
				break;
			case M3_VERTEX:
				skip(bb,2);
				m.addVertex(readM3Vector(bb));
				if(model_output!=null) {
					M3Vector m3v = m.getVertices().get(m.getVertices().size()-1);
					model_output.printf(Locale.US, "VERTEX\n%.3f %.3f %.3f# Index %d\n"
							, m3v.x, m3v.y, m3v.z
							, m.getVertices().size()-1
							);
				}
				break;
			case M3_SHADE:
				if(!inFace) {
					throw new InvalidModelDataException("Face shade set outside face definition!");
				}
				currentSurface.flags |= M3F_AUX_SHADE;
				m.flags |= M3F_AUX_SHADE;
				
				currentSurface.color = readUnsignedShort(bb);
				currentSurface.texture = readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "SHADE\n# Color\n%d\n# Darkness\n%d\n"
							, currentSurface.color
							, currentSurface.texture
							);
				}
				break;
			case M3_TEXTURE_MAPPING:
				if(!inFace) {
					throw new InvalidModelDataException("Texture mapping defined outside face definition!");
				}
				numTC = readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "TEXTURE_MAPPING\n%d\n", numTC);
				}
				for(int i=0; i<numTC; i++) {
					index = readUnsignedShort(bb);
					float u = readM3Float(bb);
					float v = readM3Float(bb);
					M3TexCoord tc = new M3TexCoord(u, v);
					while(tc.u < -128.0)
						tc.u += 256.0;
					while(tc.v < -128.0)
						tc.v += 256.0;
					currentSurface.texCoords.put(index, tc);
					if(model_output!=null) {
						model_output.printf(Locale.US, "%d %.3f %.3f\n", index, tc.u, tc.v);
					}
				}
				break;
			case M3_TEXTURE:
				if(!inFace) {
					throw new InvalidModelDataException("Texture set outside face definition!");
				}
				currentSurface.flags |= M3F_TEXTURED;
				currentSurface.texture = readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "TEXTURE\n%d\n", currentSurface.texture);
				}
				if(currentSurface.texture==0) {
					currentSurface.flags |= M3F_OBJ_TEXTURE;
					m.usesObjectTexture = true;
				} else {
					currentSurface.texture += MODEL_TEXTURE_CHUNK_BASE;
					if(!m.usedTextures.contains(currentSurface.texture))
						m.usedTextures.add(currentSurface.texture);
				}
				numVertices = readUnsignedShort(bb);
				if(model_output!=null) {
					model_output.printf(Locale.US, "%d\n", numVertices);
				}
				currentSurface.getVertexIndices().clear();
				for(int i=0; i<numVertices; i++) {
					index = readUnsignedShort(bb);
					currentSurface.vertexIndices.add(index);
					if(model_output!=null) {
						model_output.printf(Locale.US, "%d ", index);
					}
				}
				// TODO check texture mapping
				uv2Anchor(m, currentSurface);
				n.surfaces.add(new Surface(currentSurface));
				if(model_output!=null) {
					model_output.printf(Locale.US, "\n# Surface index %d\n", n.surfaces.size()-1);
				}
				break;
			default:
				debugErr("%sUnknown M3-Command %d", debugSpace, command);
				throw new InvalidModelDataException("Unknown M3-Command " + command);
			}
			
			if(inFace) {
				if(bb.position()>faceEndPosition) {
					debugErr("%sFace length mismatch. Expected %d, now at %d", debugSpace, faceEndPosition, bb.position());
					throw new InvalidModelDataException("Face length mismatch. Expected " + faceEndPosition + ", now at " + bb.position());
				} else {
					if(bb.position()==faceEndPosition) {
						inFace = false;
					}
				}
			}
		} while(true);
		
		if(model_output!=null) {
			model_output.printf(Locale.US, "# node finished\n");
		}
		
		if(leftNodePos>0) {
			m.numNodes+=2;
			debug(debugSpace + "Left Node");
			bb.position(leftNodePos);
			if(model_output!=null) {
				model_output.printf(Locale.US, "# parsing left node\n");
			}
			node_level++;
			n.left = parseNode(m, bb, dc+1);
			node_level--;
			
			// right pos has also been set at this point.
			debug(debugSpace + "Right Node");
			bb.position(rightNodePos);
			if(model_output!=null) {
				model_output.printf(Locale.US, "# parsing right node\n");
			}
			node_level++;
			n.right = parseNode(m, bb, dc+1);
			node_level--;
		}
		
		return n;
	}
	
	public static SSModel parseModel(ByteBuffer bb) {
		SSModel m = new SSModel();

		bb.get(m.header);
		
		m.root = parseNode(m, bb, 0);
		
		return m;
	}

	public static class Surface {
		public int getFlags() {
			return flags;
		}
		public int getTexture() {
			return texture;
		}
		public int getColor() {
			return color;
		}
		public List<Integer> getVertexIndices() {
			return vertexIndices;
		}
		public M3Vector getNormal() {
			return normal;
		}
		public M3Vector getPoint() {
			return point;
		}
		
		public Map<Integer, M3TexCoord> getTexCoords() {
			return texCoords;
		}
		
		public M3Vector getAnchor() {
			return anchor;
		}
		
		public M3TexCoord getAnchorTC() {
			return anchor_tc;
		}
		
		public M3Vector getU() {
			return u;
		}

		public M3Vector getV() {
			return v;
		}
		
		public boolean hasFlag(int flag) {
			return (flags & flag) == flag;
		}
		
		public Surface() {
			
		}
		
		public Surface(Surface old) {
			this.flags = old.flags;
			this.texture = old.texture;
			this.color = old.color;
			this.vertexIndices.addAll(old.vertexIndices);
			this.normal = new M3Vector(old.normal);
			this.point = new M3Vector(old.point);
			this.texCoords.putAll(old.texCoords);
			
			this.anchor = new M3Vector(old.anchor);
			this.anchor_tc = new M3TexCoord(old.anchor_tc);
			this.u = new M3Vector(old.u);
			this.v = new M3Vector(old.v);
		}
		
		private int flags = 0;
		private int texture = 0;
		private int color = 0;
		private List<Integer> vertexIndices = new Vector<Integer>();
		private M3Vector normal = new M3Vector(0 ,0 ,0);
		private M3Vector point = new M3Vector(0 ,0 ,0);
		private Map<Integer, M3TexCoord> texCoords = new TreeMap<Integer, M3TexCoord>();
		
		private M3Vector anchor;
		private M3TexCoord anchor_tc;
		
		private M3Vector u;
		private M3Vector v;
	}
	
	public static class Node {
		public List<Surface> getSurfaces() {
			return surfaces;
		}
		public M3Vector getSortPlaneNormal() {
			return sortPlaneNormal;
		}
		public M3Vector getSortPlanePoint() {
			return sortPlanePoint;
		}
		public Node getLeft() {
			return left;
		}
		public Node getRight() {
			return right;
		}
		private List<Surface> surfaces = new Vector<Surface>();
		private M3Vector sortPlaneNormal = new M3Vector(0 ,0 ,0);
		private M3Vector sortPlanePoint = new M3Vector(0 ,0 ,0);
		private Node left = null;
		private Node right = null;
	}
	
	public static class M3Vector {
		private float x = 0.0f, y = 0.0f, z = 0.0f;
		
		public M3Vector() { }
		
		public M3Vector(M3Vector v) {
			if(v!=null) {
				this.x = v.x;
				this.y = v.y;
				this.z = v.z;
			}
		}
		
		public M3Vector(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public M3Vector(float [] a) {
			this.x = a[0];
			this.y = a[1];
			this.z = a[2];
		}

		public M3Vector(double [] a) {
			this.x = (float)a[0];
			this.y = (float)a[1];
			this.z = (float)a[2];
		}
		
		public float getX() { return x; }
		
		public float getY() { return y; }
		
		public float getZ() { return z; }
		
		public double [] asDArray() {
			return new double [] { x, y, z };
		}

		public float [] asFArray() {
			return new float [] { x, y, z };
		}
		
		@Override
		public String toString() {
			return String.format("<%.3f, %.3f, %.3f>", x, y, z);
		}
	}
	
	public static class M3TexCoord {
		private double u,v;
		
		public M3TexCoord() { }

		public M3TexCoord(M3TexCoord tc) {
			if(tc!=null) {
				this.u = tc.u;
				this.v = tc.v;
			}
		}
		
		public M3TexCoord(double u, double v) {
			this.u = u;
			this.v = v;
		}
		
		public double getU() { return u; }

		public double getV() { return v; }
		
		public String toString() {
			return String.format("U:%.3f V:%.3f", u, v);
		}
	}
	
	public static void skip(ByteBuffer bb, int skip) {
		bb.position(bb.position()+skip);
	}
	
	public static int readUnsignedShort(ByteBuffer bb) {
		return ((int)bb.getShort()&0xFFFF);
	}
	
	public static float readM3Float(ByteBuffer bb) {
		return (float)bb.getInt() / 256.0f;		
	}
	
	public static M3Vector readM3Vector(ByteBuffer bb) {
		return readM3Vector(bb, null);
	}
	
	public static M3Vector readM3Vector(ByteBuffer bb, M3Vector dst) {
		if(dst==null)
			dst = new M3Vector();
		dst.x = readM3Float(bb);		
		dst.y = readM3Float(bb);		
		dst.z = readM3Float(bb);
		
		return dst;
	}
	
	public static void debug(String msg,Object...args) {
		if(debug)
			System.out.println(String.format(msg, args));
	}

	public static void debugErr(String msg,Object...args) {
		if(debug) {
			// ensure that the error is displayed exactly after the last
			// regular message
			System.out.flush();
			System.err.println(String.format(msg, args));
			System.err.flush();
		}
	}
	
	private static void uv2Anchor(SSModel m, Surface s) {
		M3Vector p0 = m.getVertices().get(s.getVertexIndices().get(0));
		M3Vector p1 = m.getVertices().get(s.getVertexIndices().get(1));
		M3Vector p2 = m.getVertices().get(s.getVertexIndices().get(2));
		
		M3TexCoord tc0 = s.getTexCoords().get(s.getVertexIndices().get(0));
		M3TexCoord tc1 = s.getTexCoords().get(s.getVertexIndices().get(1));
		M3TexCoord tc2 = s.getTexCoords().get(s.getVertexIndices().get(2));
		
		double u1 = tc0.u - tc1.u;
		double v1 = tc0.v - tc1.v;
		double u2 = tc2.u - tc1.u;
		double v2 = tc2.v - tc1.v;
		
		double [] a = mkVec(p0.x, p0.y, p0.z, null);
		double [] b = mkVec(p1.x, p1.y, p1.z, null);
		double [] c = mkVec(p2.x, p2.y, p2.z, null);
		
		double [] side1 = vecSub(a, b, null);
		double [] side2 = vecSub(c, b, null);
		
		double [] normal = crossProduct(side1, side2, null);
		normalize(normal, normal);
		
		double [] t1_v = crossProduct(side1, normal, null);
		double [] t2_v = crossProduct(side2, normal, null);
		
		double tp = innerProduct(t1_v, side2);
		
		// ((t1_v * u2) - (t2_v * u1)) / tp
		double [] u = vecDiv(vecSub(vecMul(t1_v, u2, null), vecMul(t2_v, u1, null), null), tp, null);
		double [] v = vecDiv(vecSub(vecMul(t1_v, v2, null), vecMul(t2_v, v1, null), null), tp, null);
		
		// negative UV check breaks texcoords for some reason -> removed
		/*
		int corrections = 0;
		int vindex = 0;
		do {
			for(vindex = 0; vindex < s.vertexIndices.size(); vindex++) {
				vecSub(m.getVertices().get(s.vertexIndices.get(vindex)).asDArray(), b, t1_v);
				if( (tc1.u + innerProduct(t1_v, u)) < 0.0 ) {
					tc1.u += 256.0;
					corrections++;
					break;
				}
				if( (tc1.v + innerProduct(t1_v, v)) < 0.0 ) {
					tc1.v += 256.0;
					corrections++;
					break;
				}
			}
			
		} while(vindex < s.vertexIndices.size());
		
		System.out.println("Did " + corrections + " corrections...");
		*/
		
		s.anchor = p1;
		s.anchor_tc = tc1;
		s.u = new M3Vector(u);
		s.v = new M3Vector(v);
	}
	
	public void saveSurfaces(String prefix) throws IOException {
		if(prefix==null)
			prefix="model";
		
		Queue<Node> nodequeue = new LinkedList<Node>();
		Queue<String> names = new LinkedList<String>();
		
		nodequeue.offer(root);
		names.offer(prefix+".Root");
		
		String gpline = "splot ";
		boolean first = true;
		
		while(!nodequeue.isEmpty()) {
			Node n = nodequeue.poll();
			String name = names.poll();
			if(n==null)
				continue;
			
			int sindex = 1;
			for(Surface s : n.getSurfaces()) {
				String filename = name + ".S"+sindex;
				FileWriter fw = new FileWriter(filename);
				//boolean firstv = true;
				for(Integer vi : s.getVertexIndices()) {
					M3Vector vec = vertices.get(vi);
					/*if(firstv) {
						fw.write(String.format("%.3f %.3f %.3f\n", 0.0, 0.0, 0.0));
						firstv = false;
					}*/
					fw.write(String.format("%.3f %.3f %.3f\n", vec.getX(), vec.getZ(), -vec.getY()));
				}
				fw.close();
				sindex++;

				if(!first) {
					gpline +=", ";
				} else {
					first = false;
				}
				
				gpline += "'" + filename + "' with linespoints";
			}

			if(n.getLeft()!=null) {
				nodequeue.offer(n.getLeft());
				names.offer(name+".L");
			}
			if(n.getRight()!=null) {
				nodequeue.offer(n.getRight());
				names.offer(name+".R");
			}
			
			if(n == root) {
				FileWriter fw = new FileWriter(prefix+"_onlyroot.gp");
				fw.write(gpline+"\n");
				fw.close();
			}
		}
		
		FileWriter fw = new FileWriter(prefix+".gp");
		fw.write(gpline+"\n");
		fw.close();
	}
}
