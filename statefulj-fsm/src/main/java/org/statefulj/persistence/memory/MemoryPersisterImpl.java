/***
 * 
 * Copyright 2014 Andrew Hall
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.statefulj.persistence.memory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.statefulj.common.utils.ReflectionUtils;
import org.statefulj.fsm.Persister;
import org.statefulj.fsm.StaleStateException;
import org.statefulj.fsm.model.State;

/**
 * Thread safe, in memory Persister.  
 * 
 * @author Andrew Hall
 *
 */
public class MemoryPersisterImpl<T> implements Persister<T> {
	
	private ConcurrentMap<String, State<T>> states = new ConcurrentHashMap<String, State<T>>();
	private State<T> start;

	public MemoryPersisterImpl(List<State<T>> states, State<T> start) {
		this.start = start;
		for(State<T> state : states) {
			this.states.put(state.getName(), state);
		}
	}
	
	public MemoryPersisterImpl(T stateful, List<State<T>> states, State<T> start) {
		this(states, start);
		this.setCurrent(stateful, start);
	}
	
	public State<T> getCurrent(T stateful) {
		try {
			String key = (String)getStateField(stateful).get(stateful);
			State<T> state = (key != null) ? states.get(key) : null;
			return (state != null) ? state : this.start;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void setCurrent(T stateful, State<T> current) {
		synchronized(stateful) {
			try {
				getStateField(stateful).set(stateful, current.getName());
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	/*
	 * Serialize all update of state.  Ensure that the current state is the same State that 
	 * was evaluated. If not, throw an exception
	 * 
	 * (non-Javadoc)
	 * @see org.fsm.Persister#setCurrent(org.fsm.model.State, org.fsm.model.State)
	 */
	public void setCurrent(T stateful, State<T> current, State<T> next) throws StaleStateException {
		synchronized(stateful) {
			if (this.getCurrent(stateful).equals(current)) {
				this.setCurrent(stateful, next);
			} else {
				throw new StaleStateException();
			}
		}
	}

	private Field getStateField(T stateful) {
		Field field = ReflectionUtils.getFirstAnnotatedField(stateful.getClass(), org.statefulj.persistence.annotations.State.class);
		field.setAccessible(true);
		return field;
	}
}