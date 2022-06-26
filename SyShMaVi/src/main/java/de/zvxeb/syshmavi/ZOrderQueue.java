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
package de.zvxeb.syshmavi;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Map.Entry;

public class ZOrderQueue<A> implements Queue<A> {
	private SortedMap<Double, List<A>> map;
	
	public class ZInfo {
		private Double distance;
		private A elem;
		
		public ZInfo(Double distance, A elem) {
			this.distance = distance;
			this.elem = elem;
		}
		
		public Double getDistance() {
			return distance;
		}
		
		public A getElement() {
			return elem;
		}
	}
	
	private class ZOrderComparator implements Comparator<Double> {

		@Override
		public int compare(Double o1, Double o2) {
			return o2 < o1 ? -1 : (o1 < o2 ? 1 : 0);
		}
		
	}
	
	public ZOrderQueue() {
		map = new TreeMap<Double, List<A>>(new ZOrderComparator());
	}
	
	@Override
	public boolean offer(A a) {
		return false;
	}
	
	public void offer(double dist, A a) {
		Double dval = Double.valueOf(dist);
		List<A> l = map.get(dval);
		if(l==null) {
			l = new Vector<A>();
			map.put(dval, l);
		}
		l.add(a);
	}
	
	public void remove(double dist, A a) {
		Double dval = Double.valueOf(dist);
		List<A> l = map.get(dval);
		if(l==null) {
			return;
		}
		l.remove(a);
		if(l.size()<1) {
			map.remove(dval);
		}
	}
	
	@Override
	public A poll() {
		if(map.isEmpty())
			return null;
		
		Double dval = map.firstKey();
		List<A> l = map.get(dval);
		assert(l!=null);
		assert(l.size()>0);
		
		A a = l.get(0);
		l.remove(0);
		
		if(l.size()<1) {
			map.remove(dval);
		}

		return a;
	}

	public ZInfo pollZ() {
		if(map.isEmpty())
			return null;
		
		Double dval = map.firstKey();
		List<A> l = map.get(dval);
		assert(l!=null);
		assert(l.size()>0);
		
		A a = l.get(0);
		l.remove(0);
		
		if(l.size()<1) {
			map.remove(dval);
		}

		return new ZInfo(dval, a);
	}

	@Override
	public A peek() {
		if(map.isEmpty())
			return null;
		
		Double dval = map.firstKey();
		List<A> l = map.get(dval);
		assert(l!=null);
		assert(l.size()>0);
		
		A a = l.get(0);

		return a;
	}

	@Override
	public boolean add(A e) {
		return false;
	}

	@Override
	public A element() {
		if(map.isEmpty())
			throw new NoSuchElementException();
		
		return peek();
	}

	@Override
	public A remove() {
		if(map.isEmpty())
			throw new NoSuchElementException();
		
		return poll();
	}

	@Override
	public boolean addAll(Collection<? extends A> c) {
		return false;
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public boolean contains(Object o) {
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public Iterator<A> iterator() {
		Vector<A> elems = new Vector<A>();
		
		for(Entry<Double, List<A>> e : map.entrySet()) {
			elems.addAll(e.getValue());
		}
		
		return elems.iterator();
	}

	@Override
	public boolean remove(Object o) {
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return false;
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public Object[] toArray() {
		Vector<A> elems = new Vector<A>();
		
		for(Entry<Double, List<A>> e : map.entrySet()) {
			elems.addAll(e.getValue());
		}
		
		return elems.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		Vector<A> elems = new Vector<A>();
		
		for(Entry<Double, List<A>> e : map.entrySet()) {
			elems.addAll(e.getValue());
		}
		
		return elems.toArray(a);
	}
}
